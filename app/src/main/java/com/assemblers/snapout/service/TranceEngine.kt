package com.assemblers.snapout.service

import android.content.Context
import com.assemblers.snapout.ai.LlmManager
import com.assemblers.snapout.ai.ReframeContext
import com.assemblers.snapout.core.FeedApps
import com.assemblers.snapout.core.ScorerConfig
import com.assemblers.snapout.core.ScrollSignal
import com.assemblers.snapout.core.TranceScorer
import com.assemblers.snapout.core.TranceSnapshot
import com.assemblers.snapout.core.TranceState
import com.assemblers.snapout.data.FeedSession
import com.assemblers.snapout.data.Settings
import com.assemblers.snapout.data.SnapOutDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import kotlin.random.Random

data class Diagnostics(
    val scrollEvents: Int = 0,
    val feedScrollEvents: Int = 0,
    val clickEvents: Int = 0,
    val windowEvents: Int = 0,
    val lastPackage: String? = null,
    val lastScroll: String? = null,
)

/** Glue between accessibility events, the deterministic scorer, and the intervention trigger. */
class TranceEngine(
    context: Context,
    private val settings: Settings,
    private val db: SnapOutDb,
    private val llm: LlmManager,
) {
    private val scorer = TranceScorer()
    private val sensors = SensorMonitor(context)
    private val densityDpi = context.resources.displayMetrics.densityDpi

    private val _snapshot = MutableStateFlow(TranceSnapshot())
    val snapshot: StateFlow<TranceSnapshot> = _snapshot.asStateFlow()

    private val _diag = MutableStateFlow(Diagnostics())
    val diagnostics: StateFlow<Diagnostics> = _diag.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    var onTrigger: ((TranceSnapshot) -> Unit)? = null

    private var feedPackage: String? = null
    private var leftFeedAt: Long? = null
    private var zombieSince: Long? = null
    private var cooldownUntil = 0L
    private var peakScore = 0
    private var intervenedThisSession = false
    private var warmed = false
    private var ticks = 0
    private var darkTicks = 0
    private var lyingTicks = 0
    private var sessionLate = false

    private val config get() = if (settings.state.value.demoMode) ScorerConfig.DEMO else ScorerConfig.NORMAL

    fun setConnected(c: Boolean) {
        _serviceConnected.value = c
        if (!c) endSession(System.currentTimeMillis())
    }

    fun onWindowChanged(pkg: String?, now: Long) {
        _diag.value = _diag.value.copy(windowEvents = _diag.value.windowEvents + 1, lastPackage = pkg)
        if (pkg == null || pkg in FeedApps.transient || pkg == OWN_PACKAGE) return
        if (FeedApps.isFeed(pkg)) {
            leftFeedAt = null
            if (feedPackage != pkg) {
                if (feedPackage != null) endSession(now)
                feedPackage = pkg
                scorer.startSession(now)
                sensors.start()
            }
        } else if (feedPackage != null && leftFeedAt == null) {
            leftFeedAt = now
        }
    }

    fun onScroll(pkg: String?, sig: ScrollSignal, now: Long) {
        val feed = FeedApps.isFeed(pkg)
        val d = _diag.value
        _diag.value = d.copy(
            scrollEvents = d.scrollEvents + 1,
            feedScrollEvents = d.feedScrollEvents + if (feed) 1 else 0,
            lastPackage = pkg,
            lastScroll = "dy=${sig.dy} dx=${sig.dx} item=${sig.fromIndex}..${sig.toIndex}/${sig.itemCount}",
        )
        if (!feed || sig.horizontalOnly) return
        if (feedPackage != pkg) onWindowChanged(pkg, now)
        scorer.onScroll(now, sig.dy, sig.fromIndex)
    }

    fun onClick(pkg: String?, now: Long) {
        _diag.value = _diag.value.copy(clickEvents = _diag.value.clickEvents + 1)
        if (FeedApps.isFeed(pkg)) scorer.onTap(now)
    }

    /** Called every second by the service. */
    fun tick(now: Long) {
        leftFeedAt?.let { if (now - it > LEAVE_GRACE_MS) endSession(now) }
        scorer.config = config
        val cal = Calendar.getInstance()
        val s = settings.state.value
        val snap = scorer.compute(
            now, feedPackage, sensors.lux, sensors.gravityY, cal.get(Calendar.HOUR_OF_DAY), s.forceNight, densityDpi,
        )
        _snapshot.value = snap
        peakScore = maxOf(peakScore, snap.score)
        if (snap.feedPackage != null) {
            ticks++
            if (snap.isDark) darkTicks++
            if (snap.lyingDown) lyingTicks++
            if (snap.isLate) sessionLate = true
        }

        if (snap.state >= TranceState.DRIFTING && !warmed) {
            warmed = true
            llm.warmUp()
        }
        if (snap.state == TranceState.ZOMBIE) {
            if (zombieSince == null) zombieSince = now
        } else {
            zombieSince = null
        }
        val since = zombieSince
        if (s.enabled && since != null && now - since >= config.zombieHoldMs && now >= cooldownUntil) {
            fire(snap)
        }
    }

    /** Demo button: live stats if a feed session is running, otherwise a varied made-up session. */
    fun forceTrigger() {
        val snap = _snapshot.value.let {
            if (it.feedPackage != null && it.sessionSwipes > 0) it else it.copy(
                feedPackage = DEMO_APPS.random(),
                sessionMinutes = Random.nextInt(12, 75).toDouble(),
                sessionSwipes = Random.nextInt(60, 260),
                score = Random.nextInt(82, 97), state = TranceState.ZOMBIE,
            )
        }
        fire(snap)
    }

    private fun fire(snap: TranceSnapshot) {
        zombieSince = null
        intervenedThisSession = true
        warmed = false
        cooldownUntil = System.currentTimeMillis() + config.cooldownMs
        onTrigger?.invoke(snap)
    }

    /** No automatic nudges for [ms] (notification "Snooze" / after "Take me out"). */
    fun snooze(ms: Long) {
        cooldownUntil = System.currentTimeMillis() + ms
    }

    fun buildContext(snap: TranceSnapshot): ReframeContext {
        val cal = Calendar.getInstance()
        val s = settings.state.value
        val hour = if (s.forceNight && cal.get(Calendar.HOUR_OF_DAY) in 5..21) 1 else cal.get(Calendar.HOUR_OF_DAY)
        return ReframeContext.from(
            snap, hour, cal.get(Calendar.MINUTE), s.goal,
            db.interventionsSince(nightStart()) + 1, s.strictness, db.recentTexts(3),
        )
    }

    private fun nightStart(): Long = Calendar.getInstance().apply {
        if (get(Calendar.HOUR_OF_DAY) < 18) add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis

    private fun endSession(now: Long) {
        val pkg = feedPackage ?: return
        val start = scorer.sessionStart
        val snap = _snapshot.value
        if (start != null && scorer.sessionSwipes > 0) {
            db.insertSession(
                FeedSession(
                    0, start, now, FeedApps.label(pkg), scorer.sessionSwipes, snap.sessionDistanceMeters, peakScore, intervenedThisSession,
                    taps = scorer.sessionTaps, avgDwellMs = scorer.sessionAvgDwellMs, late = sessionLate,
                    dark = darkTicks * 2 > ticks, lying = lyingTicks * 2 > ticks,
                ),
            )
        }
        feedPackage = null
        leftFeedAt = null
        zombieSince = null
        peakScore = 0
        intervenedThisSession = false
        warmed = false
        ticks = 0
        darkTicks = 0
        lyingTicks = 0
        sessionLate = false
        scorer.endSession()
        sensors.stop()
        _snapshot.value = TranceSnapshot()
    }

    companion object {
        const val OWN_PACKAGE = "com.assemblers.snapout"
        private const val LEAVE_GRACE_MS = 5_000L
        private val DEMO_APPS = listOf("com.zhiliaoapp.musically", "com.google.android.youtube", "com.instagram.android")
    }
}
