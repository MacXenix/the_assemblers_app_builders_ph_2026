package com.assemblers.snapout.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.assemblers.snapout.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reads only event type, package name, timestamps and scroll deltas.
 * canRetrieveWindowContent=false, so screen text/content is never accessible.
 */
class SnapOutAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlay: InterventionOverlay

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = InterventionOverlay(this)
        val engine = app.engine
        engine.setConnected(true)
        engine.onTrigger = { snap -> scope.launch { overlay.show(snap) } }
        scope.launch {
            while (isActive) {
                engine.tick(System.currentTimeMillis())
                delay(1_000)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val engine = app.engine
        val pkg = event.packageName?.toString()
        val now = System.currentTimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> engine.onWindowChanged(pkg, now)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> engine.onScroll(pkg, event.scrollDeltaY, now)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> engine.onClick(pkg, now)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        app.engine.setConnected(false)
        app.engine.onTrigger = null
        if (::overlay.isInitialized) overlay.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    companion object {
        @Volatile var instance: SnapOutAccessibilityService? = null
    }
}
