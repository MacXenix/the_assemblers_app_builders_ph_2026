package com.assemblers.snapout.core

import com.assemblers.snapout.data.FeedSession
import com.assemblers.snapout.data.Intervention
import java.util.Calendar
import kotlin.random.Random

/** A realistic made-up week so Insights can be demoed on a fresh install. */
object SampleData {
    private val apps = listOf("TikTok", "TikTok", "TikTok", "YouTube", "YouTube", "Instagram")
    private val hours = listOf(7, 12, 18, 21, 22, 22, 23, 23, 0, 1)
    private val texts = listOf(
        "You've been on TikTok for 24 minutes. What were you hoping to find?",
        "It's 23:40. Does this scroll move you closer to being asleep by midnight?",
        "180 swipes so far. Which video do you actually remember?",
    )
    private val outcomes = listOf("went_home", "snoozed", null, "went_home", "dismissed")

    fun week(now: Long, rnd: Random = Random(now / 3_600_000)): Pair<List<FeedSession>, List<Intervention>> {
        val sessions = mutableListOf<FeedSession>()
        val nudges = mutableListOf<Intervention>()
        for (day in 6 downTo 0) {
            repeat(rnd.nextInt(2, 5)) {
                val hour = hours.random(rnd)
                val start = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -day)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, rnd.nextInt(60))
                }.timeInMillis
                if (start > now) return@repeat
                val late = hour >= 22 || hour < 5
                val minutes = if (late) rnd.nextInt(12, 55) else rnd.nextInt(3, 20)
                val swipes = minutes * rnd.nextInt(5, 13)
                val dwell = 60_000L * minutes / swipes
                val intervened = minutes >= 20
                val app = apps.random(rnd)
                sessions += FeedSession(
                    0, start, start + minutes * 60_000L, app, swipes, swipes * 0.09, if (intervened) rnd.nextInt(80, 97) else rnd.nextInt(30, 79),
                    intervened, taps = (swipes * rnd.nextDouble(0.02, 0.12)).toInt(), avgDwellMs = dwell,
                    late = late, dark = late && rnd.nextInt(4) > 0, lying = late && rnd.nextBoolean(),
                )
                if (intervened) nudges += Intervention(
                    0, start + 18 * 60_000L, app, rnd.nextInt(80, 97), "FALLBACK", null, texts.random(rnd), outcomes.random(rnd),
                )
            }
        }
        return sessions to nudges
    }
}
