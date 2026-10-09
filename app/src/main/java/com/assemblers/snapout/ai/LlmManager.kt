package com.assemblers.snapout.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.assemblers.snapout.core.InsightItem
import com.assemblers.snapout.core.UsageSummary
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.random.Random

enum class EngineStatus { NO_MODEL, IDLE, LOADING, READY, ERROR }

data class AiStatus(
    val status: EngineStatus = EngineStatus.IDLE,
    val modelName: String? = null,
    val backend: String? = null,
    val loadMs: Long? = null,
    val error: String? = null,
)

/** Progress of an on-device usage analysis; [partial] is the text streamed so far. */
data class InsightRun(val running: Boolean = false, val partial: String = "")

/** One line in the Insights chat; [by] says who wrote an answer (model name or rules). */
data class ChatMsg(val fromUser: Boolean, val text: String, val by: String = "")

/** One generated nudge plus the evidence shown in the notification (latency, what the model saw). */
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

class LlmManager(private val context: Context, private val preferredModel: () -> String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var engine: Engine? = null
    private var idleJob: Job? = null

    private val _status = MutableStateFlow(AiStatus())
    val status: StateFlow<AiStatus> = _status.asStateFlow()

    private val _current = MutableStateFlow(Reframe())
    val current: StateFlow<Reframe> = _current.asStateFlow()

    private val _insightRun = MutableStateFlow(InsightRun())
    val insightRun: StateFlow<InsightRun> = _insightRun.asStateFlow()
    private var insightJob: Job? = null

    private val _chat = MutableStateFlow<List<ChatMsg>>(emptyList())
    val chat: StateFlow<List<ChatMsg>> = _chat.asStateFlow()

    /** null = idle; otherwise the answer streamed so far ("" while the model is starting). */
    private val _chatTyping = MutableStateFlow<String?>(null)
    val chatTyping: StateFlow<String?> = _chatTyping.asStateFlow()
    private var chatJob: Job? = null
    /** Set when GPU loaded but failed at generation time (e.g. no OpenCL); later loads go straight to CPU. */
    private var cpuOnly = false
    private val currentModelName get() = _status.value.modelName

    fun modelCandidates(): List<File> = listOfNotNull(
        context.getExternalFilesDir(null),
        File("/data/local/tmp/llm"),
    ).flatMap { dir -> dir.listFiles { f -> f.name.endsWith(".litertlm") }?.toList().orEmpty() }
        .distinctBy { it.name }

    val modelDir: String get() = context.getExternalFilesDir(null)?.absolutePath ?: "?"

    /** The model picked on Home, else the smallest one found. */
    fun findModel(): File? = modelCandidates().let { all ->
        all.firstOrNull { it.name == preferredModel() } ?: all.minByOrNull { it.length() }
    }

    /** Unload the current model so the next generation loads the newly selected one. */
    fun switchModel() {
        release()
    }

    fun refreshModelPresence() {
        if (engine == null && _status.value.status != EngineStatus.LOADING) {
            val m = findModel()
            _status.value = if (m == null) AiStatus(EngineStatus.NO_MODEL) else AiStatus(EngineStatus.IDLE, m.name)
        }
    }

    /** Load the model in the background (called when the user starts Drifting, so the nudge is fast). */
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
        for (backend in if (cpuOnly) listOf<Backend>(Backend.CPU()) else listOf(Backend.GPU(), Backend.CPU())) {
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

    /** Writes a nudge for [ctx]; tokens stream through [current]. Falls back to a built-in message. */
    suspend fun reframe(ctx: ReframeContext): Reframe = withContext(Dispatchers.Default) {
        _current.value = Reframe(contextJson = ctx.toJson())
        withTimeoutOrNull(GENERATION_TIMEOUT_MS) { generate(ctx) }
            ?: fallback(ctx, ctx.toJson()).also { Log.w(TAG, "Generation timed out") }
    }

    private suspend fun generate(ctx: ReframeContext): Reframe {
        val json = ctx.toJson()
        val g = run(PromptBuilder.fullPrompt(ctx), MAX_OUTPUT_TOKENS) { text, first ->
            _current.value = Reframe(PromptBuilder.streamView(text), false, Reframe.Source.GEMMA, first, null, json)
        }
        val cleaned = g?.let { PromptBuilder.postFilter(it.raw) } ?: return fallback(ctx, json)
        return Reframe(cleaned, true, Reframe.Source.GEMMA, g.ttftMs, g.tokensPerSec, json).also { _current.value = it }
    }

    /**
     * Analyses aggregated usage stats with the local model. [onDone] gets the parsed lines, or null if the
     * model is missing, failed, or produced unusable output (the caller then shows rule-based insights).
     */
    fun analyze(summaryJson: String, goal: String, onDone: (List<InsightItem>?, String?) -> Unit) {
        if (insightJob?.isActive == true) return
        _insightRun.value = InsightRun(running = true)
        insightJob = scope.launch {
            val g = withTimeoutOrNull(INSIGHTS_TIMEOUT_MS) {
                run(PromptBuilder.insightsPrompt(summaryJson, goal), INSIGHT_TOKENS) { text, _ ->
                    _insightRun.value = InsightRun(true, PromptBuilder.streamView(text))
                }
            }
            val items = g?.let { PromptBuilder.parseInsights(it.raw) }?.takeIf { it.size >= 2 }
            _insightRun.value = InsightRun()
            onDone(items, currentModelName)
        }
    }

    /** Answers a question using only the usage stats. Falls back to a rule-based answer if the model is unavailable. */
    fun ask(question: String, summary: UsageSummary, goal: String) {
        val q = question.trim()
        if (q.isEmpty() || chatJob?.isActive == true) return
        val history = _chat.value.takeLast(6)
        _chat.value = _chat.value + ChatMsg(true, q)
        _chatTyping.value = ""
        chatJob = scope.launch {
            val g = withTimeoutOrNull(CHAT_TIMEOUT_MS) {
                run(PromptBuilder.chatPrompt(q, summary.toJson(), goal, history), CHAT_TOKENS) { text, _ ->
                    _chatTyping.value = PromptBuilder.streamView(text)
                }
            }
            val answer = g?.let { PromptBuilder.cleanChat(it.raw) }?.takeIf { it.isNotBlank() }
            val msg = if (g != null && answer != null) {
                val model = currentModelName?.removeSuffix(".litertlm") ?: "local model"
                val speed = g.ttftMs?.let { " · first word ${"%.1f".format(Locale.US, it / 1000.0)} s" } ?: ""
                ChatMsg(false, answer, "$model · on-device$speed")
            } else {
                val why = if (_status.value.status == EngineStatus.NO_MODEL) "no model found" else "model failed or timed out"
                ChatMsg(false, PromptBuilder.ruleAnswer(q, summary), "built-in rules ($why)")
            }
            _chat.value = _chat.value + msg
            _chatTyping.value = null
        }
    }

    fun clearChat() {
        if (chatJob?.isActive == true) return
        _chat.value = emptyList()
    }

    private class Generation(val raw: String, val ttftMs: Long?, val tokensPerSec: Double)

    /** One prompt → full text. Retries once on CPU if the GPU loads but can't generate (e.g. no OpenCL). */
    private suspend fun run(prompt: String, maxTokens: Int, onPartial: (String, Long?) -> Unit): Generation? {
        val e = withTimeoutOrNull(LOAD_TIMEOUT_MS) { ensureEngine() } ?: return null
        return try {
            lock.withLock {
                // System text goes in the user turn: some model templates (e.g. Qwen3) reject a structured system message.
                val qwen = currentModelName?.contains("qwen", true) == true
                val config = ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 1.0, seed = Random.nextInt(1, Int.MAX_VALUE)),
                    maxOutputToken = maxTokens,
                    chatTemplate = if (qwen) PromptBuilder.QWEN_TEMPLATE else null,
                )
                e.createConversation(config).use { conv ->
                    val start = System.currentTimeMillis()
                    var first: Long? = null
                    val sb = StringBuilder()
                    conv.sendMessageAsync(prompt).collect { msg ->
                        val piece = msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        if (piece.isEmpty()) return@collect
                        if (first == null) first = System.currentTimeMillis() - start
                        sb.append(piece)
                        onPartial(sb.toString(), first)
                    }
                    val total = (System.currentTimeMillis() - start - (first ?: 0)).coerceAtLeast(1)
                    scheduleRelease()
                    Generation(sb.toString(), first, (sb.length / CHARS_PER_TOKEN) / (total / 1000.0))
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "Generation failed", t)
            if (cpuOnly || _status.value.backend != "GPU") return null
            cpuOnly = true
            lock.withLock {
                val old = engine
                engine = null
                scope.launch { runCatching { old?.close() } }
            }
            run(prompt, maxTokens, onPartial)
        }
    }

    private fun fallback(ctx: ReframeContext, json: String): Reframe =
        Reframe(FallbackTemplates.pick(ctx), true, Reframe.Source.FALLBACK, contextJson = json).also { _current.value = it }

    companion object {
        private const val TAG = "SnapOutLlm"
        private const val IDLE_RELEASE_MS = 5 * 60_000L
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val CHARS_PER_TOKEN = 4.0
        private const val MAX_OUTPUT_TOKENS = 160
        private const val GENERATION_TIMEOUT_MS = 45_000L
        private const val INSIGHT_TOKENS = 360
        private const val CHAT_TOKENS = 200
        private const val CHAT_TIMEOUT_MS = 180_000L
        private const val INSIGHTS_TIMEOUT_MS = 240_000L
    }
}
