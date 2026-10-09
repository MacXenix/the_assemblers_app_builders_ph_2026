package com.assemblers.snapout.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranceScorerTest {
    private val feed = "com.zhiliaoapp.musically"

    private fun compute(s: TranceScorer, now: Long, lux: Float? = 200f, hour: Int = 14, gravityY: Float? = 9f) =
        s.compute(now, feed, lux, gravityY, hour, forceNight = false, densityDpi = 420)

    @Test fun idleFeedScoresZero() {
        val s = TranceScorer()
        s.startSession(0)
        assertEquals(0, compute(s, 30_000, lux = 1f, hour = 1).score)
    }

    @Test fun burstOfScrollEventsCountsAsOneSwipe() {
        val s = TranceScorer()
        s.startSession(0)
        listOf(1000L, 1050L, 1100L, 1200L).forEach { s.onScroll(it, 300) }
        s.onScroll(3000, 300)
        assertEquals(2, s.sessionSwipes)
    }

    @Test fun casualDaytimeBrowsingStaysFocused() {
        val s = TranceScorer()
        s.startSession(0)
        var t = 0L
        repeat(4) { t += 15_000; s.onScroll(t, 1000); s.onTap(t + 2000) }
        assertEquals(TranceState.FOCUSED, compute(s, t + 1000).state)
    }

    @Test fun fastLateNightScrollingInTheDarkIsZombie() {
        val s = TranceScorer()
        s.startSession(0)
        var t = 0L
        while (t < 25 * 60_000L) { t += 2_000; s.onScroll(t, 1500) }
        val snap = compute(s, t, lux = 2f, hour = 1, gravityY = 1f)
        assertEquals(TranceState.ZOMBIE, snap.state)
        assertTrue(snap.score >= 90)
        assertTrue(snap.isDark && snap.isLate)
    }

    @Test fun demoConfigTriggersQuickly() {
        val s = TranceScorer(ScorerConfig.DEMO)
        s.startSession(0)
        var t = 0L
        while (t < 70_000L) { t += 2_500; s.onScroll(t, 1500) }
        assertTrue(compute(s, t).score >= 80)
    }

    @Test fun medianGap() {
        assertEquals(2000, TranceScorer.medianGap(listOf(0, 1000, 3000, 6000)))
    }
}
