package com.assemblers.snapout.core

import com.assemblers.snapout.ai.PromptBuilder
import com.assemblers.snapout.data.FeedSession
import com.assemblers.snapout.data.Intervention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L

    private fun session(startAgo: Long, minutes: Int, app: String, swipes: Int, late: Boolean = false) =
        FeedSession(0, now - startAgo, now - startAgo + minutes * min, app, swipes, 1.0, 85, false, taps = swipes / 20, avgDwellMs = 5_000, late = late)

    @Test fun summaryAggregatesLastWeekOnly() {
        val s = UsageSummary.from(
            listOf(
                session(60 * min, 30, "TikTok", 300, late = true),
                session(3 * 24 * 60 * min, 10, "YouTube", 100),
                session(10 * 24 * 60 * min, 99, "Instagram", 999),
            ),
            listOf(Intervention(0, now - 50 * min, "TikTok", 90, "GEMMA", 100, "x", "went_home")),
            now,
        )
        assertEquals(2, s.sessions)
        assertEquals(40, s.totalMinutes)
        assertEquals(400, s.totalSwipes)
        assertEquals("TikTok", s.apps.first().app)
        assertEquals(75, s.lateNightPct)
        assertEquals(5, s.tapsPer100Swipes)
        assertEquals(1, s.wentHome)
        assertTrue(s.toJson().contains("\"total_minutes\":40"))
    }

    @Test fun emptySummaryHasNoRuleInsights() {
        assertTrue(UsageSummary.from(emptyList(), emptyList(), now).empty)
        assertTrue(RuleInsights.from(UsageSummary()).isEmpty())
    }

    @Test fun ruleInsightsGiveFindingsAndTips() {
        val (ss, ns) = SampleData.week(now)
        val items = RuleInsights.from(UsageSummary.from(ss, ns, now))
        assertEquals(3, items.count { !it.tip })
        assertEquals(3, items.count { it.tip })
    }

    @Test fun parseInsightsHandlesMarkdownAndThinking() {
        val raw = "<think>hmm</think>\n1. **Insight:** You scroll 42 min per day on TikTok.\n- Tip - Charge the phone outside the bedroom.\n" +
            "Insight 2: Most sessions start after 22:00.\nrandom chatter\nTip: You are lazy and should stop."
        val items = PromptBuilder.parseInsights(raw)
        assertEquals(listOf(false, false, true), items.map { it.tip })
        assertEquals("You scroll 42 min per day on TikTok.", items[0].text)
        assertEquals(items, PromptBuilder.parseInsights(items.render()))
    }

    @Test fun mainSignalPicksLargestContributor() {
        val snap = TranceSnapshot(breakdown = ScoreBreakdown(swipeRate = 0.2, darkLate = 1.0))
        assertEquals("scrolling in the dark late at night", com.assemblers.snapout.ai.ReframeContext.mainSignal(snap))
    }
}
