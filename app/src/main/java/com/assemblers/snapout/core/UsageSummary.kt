package com.assemblers.snapout.core

import com.assemblers.snapout.data.FeedSession
import com.assemblers.snapout.data.Intervention
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

data class AppUsage(val app: String, val sessions: Int, val minutes: Int, val swipes: Int)

/** Aggregated feed analytics over the last [days] days. This (not raw events) is what the local model analyses. */
data class UsageSummary(
    val days: Int = 0,
    val sessions: Int = 0,
    val totalMinutes: Int = 0,
    val minutesPerDay: Int = 0,
    val totalSwipes: Int = 0,
    val avgSecondsPerVideo: Double = 0.0,
    val tapsPer100Swipes: Int = 0,
    val avgSessionMinutes: Int = 0,
    val longestSessionMinutes: Int = 0,
    val lateNightPct: Int = 0,
    val darkPct: Int = 0,
    val lyingPct: Int = 0,
    val peakHour: Int? = null,
    val apps: List<AppUsage> = emptyList(),
    val nudges: Int = 0,
    val wentHome: Int = 0,
    val snoozed: Int = 0,
    val ignored: Int = 0,
) {
    val empty get() = sessions == 0

    fun toJson(): String {
        val appsJson = apps.joinToString(",") {
            """{"app":"${it.app}","sessions":${it.sessions},"minutes":${it.minutes},"swipes":${it.swipes}}"""
        }
        return """{"days":$days,"sessions":$sessions,"total_minutes":$totalMinutes,"minutes_per_day":$minutesPerDay,""" +
            """"total_swipes":$totalSwipes,"avg_seconds_per_video":${fmt(avgSecondsPerVideo)},"taps_per_100_swipes":$tapsPer100Swipes,""" +
            """"avg_session_minutes":$avgSessionMinutes,"longest_session_minutes":$longestSessionMinutes,""" +
            """"late_night_pct":$lateNightPct,"dark_room_pct":$darkPct,"lying_down_pct":$lyingPct,""" +
            """"peak_hour":${peakHour?.let { "\"%02d:00\"".format(it) } ?: "null"},"apps":[$appsJson],""" +
            """"nudges":$nudges,"nudges_went_home":$wentHome,"nudges_snoozed":$snoozed,"nudges_ignored":$ignored}"""
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60_000L

        private fun fmt(d: Double) = String.format(Locale.US, "%.1f", d)

        fun from(sessions: List<FeedSession>, interventions: List<Intervention>, now: Long, days: Int = 7): UsageSummary {
            val since = now - days * DAY_MS
            val ss = sessions.filter { it.startedAt >= since && it.endedAt >= it.startedAt }
            if (ss.isEmpty()) return UsageSummary(days = days)
            val minutes = ss.map { (it.endedAt - it.startedAt) / 60_000.0 }
            val total = minutes.sum()
            fun pct(f: (FeedSession) -> Boolean) =
                if (total <= 0) 0 else (100 * ss.indices.filter { f(ss[it]) }.sumOf { minutes[it] } / total).roundToInt()
            val swipes = ss.sumOf { it.swipes }
            val dwellSessions = ss.filter { it.avgDwellMs > 0 }
            val avgDwell = if (dwellSessions.isEmpty()) {
                if (swipes == 0) 0.0 else total * 60 / swipes
            } else {
                dwellSessions.sumOf { it.avgDwellMs * it.swipes.toDouble() } / dwellSessions.sumOf { it.swipes }.coerceAtLeast(1) / 1000.0
            }
            val cal = Calendar.getInstance()
            val byHour = ss.indices.groupBy { cal.apply { timeInMillis = ss[it].startedAt }.get(Calendar.HOUR_OF_DAY) }
                .mapValues { (_, idx) -> idx.sumOf { minutes[it] } }
            val activeDays = ss.map { cal.apply { timeInMillis = it.startedAt }.get(Calendar.DAY_OF_YEAR) }.distinct().size
            val apps = ss.indices.groupBy { ss[it].app }.map { (app, idx) ->
                AppUsage(app, idx.size, idx.sumOf { minutes[it] }.roundToInt(), idx.sumOf { ss[it].swipes })
            }.sortedByDescending { it.minutes }
            val ns = interventions.filter { it.at >= since }
            return UsageSummary(
                days = days,
                sessions = ss.size,
                totalMinutes = total.roundToInt(),
                minutesPerDay = (total / activeDays.coerceAtLeast(1)).roundToInt(),
                totalSwipes = swipes,
                avgSecondsPerVideo = avgDwell,
                tapsPer100Swipes = if (swipes == 0) 0 else 100 * ss.sumOf { it.taps } / swipes,
                avgSessionMinutes = (total / ss.size).roundToInt(),
                longestSessionMinutes = minutes.max().roundToInt(),
                lateNightPct = pct { it.late },
                darkPct = pct { it.dark },
                lyingPct = pct { it.lying },
                peakHour = byHour.maxByOrNull { it.value }?.key,
                apps = apps,
                nudges = ns.size,
                wentHome = ns.count { it.outcome == "went_home" },
                snoozed = ns.count { it.outcome == "snoozed" },
                ignored = ns.count { it.outcome == null || it.outcome == "dismissed" || it.outcome == "continued" },
            )
        }
    }
}

/** One line of the analysis, either an observation or a suggestion. */
data class InsightItem(val tip: Boolean, val text: String) {
    fun render() = (if (tip) "Tip: " else "Insight: ") + text
}

fun List<InsightItem>.render() = joinToString("\n") { it.render() }

/** Deterministic analysis used when no model is available; also a sanity baseline for the model's output. */
object RuleInsights {
    fun from(s: UsageSummary): List<InsightItem> {
        if (s.empty) return emptyList()
        val found = mutableListOf<InsightItem>()
        val tips = mutableListOf<InsightItem>()
        s.apps.firstOrNull()?.let { top ->
            val share = if (s.totalMinutes == 0) 0 else 100 * top.minutes / s.totalMinutes
            found += InsightItem(false, "$share% of your feed time (${top.minutes} min) was on ${top.app}, about ${s.minutesPerDay} min per active day.")
        }
        if (s.avgSecondsPerVideo in 0.1..8.0) {
            found += InsightItem(false, "You spend about ${"%.0f".format(s.avgSecondsPerVideo)} s per video before swiping, which is skim mode rather than watching.")
            tips += InsightItem(true, "When you notice fast swiping, pick one video to watch fully or close the app.")
        }
        if (s.lateNightPct >= 30) {
            found += InsightItem(false, "${s.lateNightPct}% of your scrolling happened between 22:00 and 05:00.")
            tips += InsightItem(true, "Set a phone-down time 30 minutes before bed and charge the phone away from your bed.")
        }
        if (s.tapsPer100Swipes < 10) {
            found += InsightItem(false, "Only ${s.tapsPer100Swipes} taps per 100 swipes: mostly passive watching.")
            tips += InsightItem(true, "Open feeds with a purpose, like checking one creator, and leave once it's done.")
        }
        if (s.longestSessionMinutes >= 30) {
            found += InsightItem(false, "Your longest session lasted ${s.longestSessionMinutes} minutes without a break.")
            tips += InsightItem(true, "Decide a stopping point before you open the app, for example 10 videos or one song.")
        }
        if (s.lyingPct >= 40) found += InsightItem(false, "${s.lyingPct}% of your feed time was while lying down.")
        if (s.nudges > 0) {
            found += InsightItem(false, "You left the feed after ${s.wentHome} of ${s.nudges} nudges.")
            if (s.wentHome * 2 < s.nudges) tips += InsightItem(true, "Try Strict mode so nudges are more direct.")
        }
        s.peakHour?.let { found += InsightItem(false, "Your heaviest scrolling starts around %02d:00.".format(it)) }
        tips += InsightItem(true, "Move feed apps off your home screen so opening them is a choice, not a reflex.")
        tips += InsightItem(true, "Replace the first scroll of the evening with a short walk or a page of a book.")
        return found.take(3) + tips.take(3)
    }
}
