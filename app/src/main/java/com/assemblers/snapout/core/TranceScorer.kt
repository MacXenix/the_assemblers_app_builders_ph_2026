package com.assemblers.snapout.core

import kotlin.math.abs

/**
 * Deterministic, explainable trance score. Works only on interaction metadata
 * (timestamps, scroll deltas, taps) and ambient sensors — never on screen content.
 */
class TranceScorer(var config: ScorerConfig = ScorerConfig.NORMAL) {

    private val swipes = ArrayDeque<Long>()
    private val taps = ArrayDeque<Long>()
    private var lastScrollEventAt = 0L
    private var lastSwipeAt = 0L
    private var lastItemIndex = -1

    var sessionStart: Long? = null
        private set
    var sessionSwipes = 0
        private set
    var sessionTaps = 0
        private set
    private var dwellSumMs = 0L
    private var dwellCount = 0

    /** Mean time between swipes this session, ignoring pauses over 2 min. */
    val sessionAvgDwellMs get() = if (dwellCount == 0) 0L else dwellSumMs / dwellCount
    private var sessionDistancePx = 0L

    fun startSession(now: Long) {
        sessionStart = now
        sessionSwipes = 0
        sessionTaps = 0
        dwellSumMs = 0
        dwellCount = 0
        sessionDistancePx = 0
        swipes.clear()
        taps.clear()
        lastScrollEventAt = 0
        lastSwipeAt = 0
        lastItemIndex = -1
    }

    fun endSession() {
        sessionStart = null
        swipes.clear()
        taps.clear()
    }

    /**
     * Scroll events arrive in bursts during one fling; a gap > [SWIPE_GAP_MS] starts a new swipe.
     * Apps that keep emitting events (e.g. YouTube) also report list positions, so moving to a new
     * item counts as a swipe too, at most once per [MIN_ITEM_SWIPE_MS].
     */
    fun onScroll(now: Long, deltaPx: Int, itemIndex: Int = -1) {
        if (sessionStart == null) startSession(now)
        val afterGap = now - lastScrollEventAt > SWIPE_GAP_MS
        val newItem = itemIndex >= 0 && lastItemIndex >= 0 && itemIndex != lastItemIndex &&
            now - lastSwipeAt > MIN_ITEM_SWIPE_MS
        if (afterGap || newItem) {
            if (lastSwipeAt > 0 && now - lastSwipeAt < MAX_DWELL_MS) {
                dwellSumMs += now - lastSwipeAt
                dwellCount++
            }
            swipes.addLast(now)
            sessionSwipes++
            lastSwipeAt = now
        }
        lastScrollEventAt = now
        if (itemIndex >= 0) lastItemIndex = itemIndex
        sessionDistancePx += abs(deltaPx)
    }

    fun onTap(now: Long) {
        if (sessionStart == null) return
        taps.addLast(now)
        sessionTaps++
    }

    fun compute(
        now: Long,
        feedPackage: String?,
        lux: Float?,
        gravityY: Float?,
        hourOfDay: Int,
        forceNight: Boolean,
        densityDpi: Int,
    ): TranceSnapshot {
        prune(now)
        val start = sessionStart
        if (feedPackage == null || start == null) {
            return TranceSnapshot(lux = lux)
        }
        val windowSwipes = swipes.size
        val spm = windowSwipes * (60_000.0 / WINDOW_MS)
        val dwell = medianGap(swipes.toList())
        val sessionMin = (now - start) / 60_000.0

        val swipeRate = norm(spm, config.baselineSwipesPerMin, config.fastSwipesPerMin)
        val shortDwell = if (windowSwipes < 2) 0.0 else 1.0 - norm(dwell.toDouble(), 4_000.0, 20_000.0)
        val sessionLength = norm(sessionMin, 0.0, config.longSessionMinutes)
        val tapRatio = if (windowSwipes == 0) 0.0 else taps.size.toDouble() / (taps.size + windowSwipes)
        val lowTap = if (windowSwipes < 3) 0.0 else 1.0 - norm(tapRatio, 0.05, 0.30)
        val isDark = forceNight || (lux != null && lux < DARK_LUX)
        val isLate = forceNight || hourOfDay >= 22 || hourOfDay < 5
        val darkLate = when {
            isDark && isLate -> 1.0
            isDark || isLate -> 0.5
            else -> 0.0
        }
        val lying = if (forceNight) 1.0 else gravityY?.let { 1.0 - norm(abs(it).toDouble(), 3.0, 7.0) } ?: 0.0

        val b = ScoreBreakdown(swipeRate, shortDwell, sessionLength, lowTap, darkLate, lying)
        val behaviour = 25 * swipeRate + 20 * shortDwell + 20 * sessionLength + 10 * lowTap
        val raw = if (config.behaviourOnly) behaviour * 100 / 75 else behaviour + 15 * darkLate + 10 * lying
        // Without active swiping there is no trance, regardless of context.
        val score = if (windowSwipes == 0) 0 else raw.toInt().coerceIn(0, 100)
        val meters = sessionDistancePx / densityDpi.coerceAtLeast(1).toDouble() * 0.0254

        return TranceSnapshot(
            score = score,
            state = stateFor(score),
            feedPackage = feedPackage,
            swipesPerMinute = spm,
            medianDwellMs = dwell,
            sessionMinutes = sessionMin,
            sessionSwipes = sessionSwipes,
            sessionTaps = sessionTaps,
            lyingDown = lying >= 0.5,
            sessionDistanceMeters = meters,
            lux = lux,
            isDark = isDark,
            isLate = isLate,
            breakdown = b,
        )
    }

    private fun prune(now: Long) {
        while (swipes.isNotEmpty() && now - swipes.first() > WINDOW_MS) swipes.removeFirst()
        while (taps.isNotEmpty() && now - taps.first() > WINDOW_MS) taps.removeFirst()
    }

    companion object {
        const val WINDOW_MS = 60_000L
        const val SWIPE_GAP_MS = 350L
        const val MIN_ITEM_SWIPE_MS = 700L
        const val MAX_DWELL_MS = 120_000L
        const val DARK_LUX = 10f

        fun stateFor(score: Int) = when {
            score >= 80 -> TranceState.ZOMBIE
            score >= 50 -> TranceState.DRIFTING
            else -> TranceState.FOCUSED
        }

        fun norm(v: Double, lo: Double, hi: Double): Double =
            if (hi <= lo) 0.0 else ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)

        fun medianGap(times: List<Long>): Long {
            if (times.size < 2) return 0
            val gaps = times.zipWithNext { a, b -> b - a }.sorted()
            return gaps[gaps.size / 2]
        }
    }
}
