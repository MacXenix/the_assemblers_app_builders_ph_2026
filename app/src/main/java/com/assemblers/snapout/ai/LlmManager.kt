package com.assemblers.snapout.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

enum class EngineStatus { NO_MODEL, IDLE, LOADING, READY, ERROR }

data class AiStatus(
    val status: EngineStatus = EngineStatus.IDLE,
    val modelName: String? = null,
    val backend: String? = null,
    val loadMs: Long? = null,
    val error: String? = null,
)

/** Live view of one generation, rendered by the overlay's "on-device AI" panel. */
data class Reframe(
    val text: String = "",
    val done: Boolean = false,
    val source: Source = Source.PENDING,
    val ttftMs: Long? = null,
    val tokensPerSec: Double? = null,
    val contextJson: String = "",
) {
    enum class Source { PENDING, GEMMA, FALLBACK }
}

class LlmManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var engine: Engine? = null
    private var idleJob: Job? = null

    private val _status = MutableStateFlow(AiStatus())
    val status: StateFlow<AiStatus> = _status.asStateFlow()

    private val _current = MutableStateFlow(Reframe())
    val current: StateFlow<Reframe> = _current.asStateFlow()

    private var pregenerated: Reframe? = null

    fun modelCandidates(): List<File> = listOfNotNull(
        context.getExternalFilesDir(null),
        File("/data/local/tmp/llm"),
    ).flatMap { dir -> dir.listFiles { f -> f.name.endsWith(".litertlm") }?.toList().orEmpty() }

    fun findModel(): File? = modelCandidates().minByOrNull { it.length() }

    fun refreshModelPresence() {
        if (engine == null && _status.value.status != EngineStatus.LOADING) {
            val m = findModel()
            _status.value = if (m == null) AiStatus(EngineStatus.NO_MODEL) else AiStatus(EngineStatus.IDLE, m.name)
        }
    }

    /** Load the model in the background (called when the user starts Drifting). */
    fun warmUp() {
        scope.launch { ensureEngine() }
    }

    private suspend fun ensureEngine(): Engine? = lock.withLock {
        engine?.let { scheduleRelease(); return it }
        val model = findModel() ?: run {
            _status.value = AiStatus(EngineStatus.NO_MODEL)
            return null
        }
        _status.value = AiStatus(EngineStatus.LOADING, model.name)
        val started = System.currentTimeMillis()
        for (backend in listOf<Backend>(Backend.GPU(), Backend.CPU())) {
            try {
                val e = Engine(EngineConfig(modelPath = model.absolutePath, backend = backend, cacheDir = context.cacheDir.path))
                e.initialize()
                engine = e
                _status.value = AiStatus(
                    EngineStatus.READY, model.name, backend.name, System.currentTimeMillis() - started,
                )
                scheduleRelease()
                return e
            } catch (t: Throwable) {
                Log.w(TAG, "Engine init failed on ${backend.name}", t)
                _status.value = AiStatus(EngineStatus.LOADING, model.name, error = t.message)
            }
        }
        _status.value = AiStatus(EngineStatus.ERROR, model.name, error = "Could not load model on GPU or CPU")
        null
    }

    private fun scheduleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            release()
        }
    }

    fun release() {
        scope.launch {
            lock.withLock {
                engine?.close()
                engine = null
                refreshModelPresence()
            }
        }
    }

    /** Generate ahead of time so the overlay can show a reframe instantly. */
    fun pregenerate(ctx: ReframeContext) {
        scope.launch {
            val r = generateBlocking(ctx, publish = false)
            if (r.source == Reframe.Source.GEMMA) pregenerated = r
        }
    }

    /** Starts a reframe for the overlay; result streams through [current]. */
    fun startReframe(ctx: ReframeContext) {
        val ready = pregenerated
        pregenerated = null
        if (ready != null) {
            _current.value = ready.copy(contextJson = ctx.toJson())
            return
        }
        _current.value = Reframe(contextJson = ctx.toJson())
        scope.launch { generateBlocking(ctx, publish = true) }
    }

    private suspend fun generateBlocking(ctx: ReframeContext, publish: Boolean): Reframe {
        val json = ctx.toJson()
        val e = withTimeoutOrNull(LOAD_TIMEOUT_MS) { ensureEngine() }
        if (e == null) return fallback(ctx, json, publish)
        return try {
            lock.withLock {
                val config = ConversationConfig(
                    systemInstruction = Contents.of(PromptBuilder.SYSTEM),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.9),
                )
                e.createConversation(config).use { conv ->
                    val start = System.currentTimeMillis()
                    var first: Long? = null
                    var chunks = 0
                    val sb = StringBuilder()
                    conv.sendMessageAsync(PromptBuilder.userPrompt(ctx)).collect { msg ->
                        val piece = msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        if (piece.isEmpty()) return@collect
                        if (first == null) first = System.currentTimeMillis() - start
                        chunks++
                        sb.append(piece)
                        if (publish) _current.value = Reframe(sb.toString(), false, Reframe.Source.GEMMA, first, null, json)
                    }
                    val total = (System.currentTimeMillis() - start - (first ?: 0)).coerceAtLeast(1)
                    val cleaned = PromptBuilder.postFilter(sb.toString())
                        ?: return@use fallback(ctx, json, publish)
                    val tps = (sb.length / CHARS_PER_TOKEN) / (total / 1000.0)
                    Reframe(cleaned, true, Reframe.Source.GEMMA, first, tps, json).also {
                        if (publish) _current.value = it
                        scheduleRelease()
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Generation failed", t)
            fallback(ctx, json, publish)
        }
    }

    private fun fallback(ctx: ReframeContext, json: String, publish: Boolean): Reframe {
        val r = Reframe(FallbackTemplates.pick(ctx), true, Reframe.Source.FALLBACK, contextJson = json)
        if (publish) _current.value = r
        return r
    }

    companion object {
        private const val TAG = "SnapOutLlm"
        private const val IDLE_RELEASE_MS = 5 * 60_000L
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val CHARS_PER_TOKEN = 4.0
    }
}
