package com.assemblers.snapout.service

import android.graphics.PixelFormat
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.assemblers.snapout.ai.Reframe
import com.assemblers.snapout.app
import com.assemblers.snapout.core.FeedApps
import com.assemblers.snapout.core.TranceSnapshot
import com.assemblers.snapout.data.Intervention
import com.assemblers.snapout.ui.InterventionScreen
import com.assemblers.snapout.ui.theme.SnapOutTheme
import java.util.Locale

/** Full-screen intervention drawn via TYPE_ACCESSIBILITY_OVERLAY (no SYSTEM_ALERT_WINDOW needed). */
class InterventionOverlay(private val service: SnapOutAccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var interventionId: Long? = null
    private var tts: TextToSpeech? = null

    fun show(snap: TranceSnapshot) {
        if (view != null) return
        val app = service.app
        val engine = app.engine
        engine.overlayShowing = true
        val ctx = engine.buildContext(snap)
        app.llm.startReframe(ctx)
        interventionId = app.db.insertIntervention(
            Intervention(0, System.currentTimeMillis(), FeedApps.label(snap.feedPackage), snap.score, "PENDING", null, "", null),
        )
        val settings = app.settings.state.value
        if (settings.speak) tts = TextToSpeech(service) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }

        val o = OverlayOwner().also { it.start() }
        owner = o
        val v = ComposeView(service).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                SnapOutTheme {
                    InterventionScreen(
                        snap = snap,
                        context = ctx,
                        strictness = settings.strictness,
                        reframeFlow = app.llm.current,
                        aiStatusFlow = app.llm.status,
                        onReframeDone = { r -> speak(r) },
                        onGoHome = { close(continued = false); service.goHome() },
                        onContinue = { close(continued = true) },
                    )
                }
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(v, lp)
        view = v
    }

    private fun speak(r: Reframe) {
        tts?.speak(r.text, TextToSpeech.QUEUE_FLUSH, null, "reframe")
    }

    private fun close(continued: Boolean) {
        val app = service.app
        val r = app.llm.current.value
        interventionId?.let {
            app.db.setOutcome(it, if (continued) "continued" else "went_home", r.text.ifBlank { null }, r.source.name, r.ttftMs)
        }
        interventionId = null
        app.engine.onInterventionClosed(continued)
        dismiss()
    }

    fun dismiss() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        owner?.stop()
        owner = null
        tts?.shutdown()
        tts = null
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val ssrc = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry

        fun start() {
            ssrc.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
