package com.assemblers.snapout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import com.assemblers.snapout.R
import com.assemblers.snapout.ai.Reframe
import com.assemblers.snapout.app
import com.assemblers.snapout.core.FeedApps
import com.assemblers.snapout.core.TranceSnapshot
import com.assemblers.snapout.data.Intervention
import com.assemblers.snapout.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Nudges are heads-up notifications instead of a screen block. The model writes a fresh message from the
 * current scroll parameters each time; if it isn't done within [QUICK_MS] the notification shows a placeholder
 * and is updated silently when the text is ready.
 */
class NudgeNotifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var tts: TextToSpeech? = null

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Scroll nudges", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pops up when SnapOut notices mindless scrolling"
            },
        )
    }

    fun show(snap: TranceSnapshot) {
        val app = context.app
        val ctx = app.engine.buildContext(snap)
        val id = app.db.insertIntervention(
            Intervention(0, System.currentTimeMillis(), FeedApps.label(snap.feedPackage), snap.score, "PENDING", null, "", null),
        )
        val title = "${snap.sessionSwipes} swipes · ${ctx.minutes} min on ${ctx.app}"
        nm.cancel(NOTIFICATION_ID)
        job?.cancel()
        job = scope.launch {
            val gen = async { app.llm.reframe(ctx) }
            val quick = withTimeoutOrNull(QUICK_MS) { gen.await() }
            if (quick == null) post(id, title, "On-device AI is writing your nudge…", "${ctx.app} · score ${snap.score}")
            val r = quick ?: gen.await()
            app.db.updateMessage(id, r.text, r.source.name, r.ttftMs)
            post(id, title, r.text, evidence(r))
            if (app.settings.state.value.speak) speak(r.text)
        }
    }

    private fun evidence(r: Reframe): String {
        val model = context.app.llm.status.value.modelName?.removeSuffix(".litertlm")
        return if (r.source == Reframe.Source.GEMMA) {
            "${model ?: "local model"} · on-device${r.ttftMs?.let { " · ${"%.1f".format(Locale.US, it / 1000.0)} s" } ?: ""}"
        } else {
            "built-in message (no model loaded)"
        }
    }

    private fun post(id: Long, title: String, text: String, sub: String) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 1)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_snapout)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSubText(sub)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setColor(0xFF6EE7B7.toInt())
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setDeleteIntent(action(ACTION_DISMISS, id))
            .addAction(Notification.Action.Builder(null, "Take me out", action(ACTION_HOME, id)).build())
            .addAction(Notification.Action.Builder(null, "Snooze 15 min", action(ACTION_SNOOZE, id)).build())
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private fun action(name: String, id: Long) = PendingIntent.getBroadcast(
        context, name.hashCode(),
        Intent(context, NudgeActionReceiver::class.java).setAction(name).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun speak(text: String) {
        tts?.shutdown()
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nudge")
            }
        }
    }

    fun close() {
        scope.cancel()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val CHANNEL = "nudges"
        const val NOTIFICATION_ID = 42
        const val EXTRA_ID = "intervention_id"
        const val ACTION_HOME = "com.assemblers.snapout.NUDGE_HOME"
        const val ACTION_SNOOZE = "com.assemblers.snapout.NUDGE_SNOOZE"
        const val ACTION_DISMISS = "com.assemblers.snapout.NUDGE_DISMISS"
        private const val QUICK_MS = 6_000L
        private const val SNOOZE_MS = 15 * 60_000L
        private const val AFTER_HOME_MS = 5 * 60_000L

        fun canPost(context: Context) = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    /** Handles the notification buttons and swipe-away; records the outcome for Insights. */
    class NudgeActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.app
            val id = intent.getLongExtra(EXTRA_ID, -1)
            val outcome = when (intent.action) {
                ACTION_HOME -> {
                    SnapOutAccessibilityService.instance?.goHome()
                    app.engine.snooze(AFTER_HOME_MS)
                    "went_home"
                }
                ACTION_SNOOZE -> {
                    app.engine.snooze(SNOOZE_MS)
                    "snoozed"
                }
                ACTION_DISMISS -> "dismissed"
                else -> return
            }
            if (id >= 0) app.db.setOutcome(id, outcome)
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
}
