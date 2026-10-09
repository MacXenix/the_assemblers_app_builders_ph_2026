package com.assemblers.snapout.ai

import com.assemblers.snapout.core.FeedApps
import com.assemblers.snapout.core.InsightItem
import com.assemblers.snapout.core.RuleInsights
import com.assemblers.snapout.core.UsageSummary
import com.assemblers.snapout.core.Strictness
import com.assemblers.snapout.core.TranceSnapshot
import java.util.Locale

data class ReframeContext(
    val time: String,
    val app: String,
    val minutes: Int,
    val swipes: Int,
    val dark: Boolean,
    val goal: String,
    val interventionsTonight: Int,
    val strictness: Strictness,
    val recent: List<String>,
    val angle: String = "",
    val swipesPerMin: Int = 0,
    val secondsPerVideo: Int = 0,
    val tapsPer100Swipes: Int = 0,
    val late: Boolean = false,
    val lyingDown: Boolean = false,
    val mainSignal: String = "",
) {
    fun toJson(): String {
        val recentJson = recent.joinToString(",") { "\"" + it.replace("\"", "'") + "\"" }
        return """{"time":"$time","app":"$app","minutes":$minutes,"swipes":$swipes,"dark":$dark,""" +
            """"goal":"${goal.replace("\"", "'")}","interruption_number":$interventionsTonight,""" +
            """"swipes_per_min":$swipesPerMin,"seconds_per_video":$secondsPerVideo,"taps_per_100_swipes":$tapsPer100Swipes,""" +
            """"late_night":$late,"lying_down":$lyingDown,"main_signal":"$mainSignal",""" +
            """"tone":"${tone()}","angle":"$angle","recent":[$recentJson]}"""
    }

    fun tone() = when {
        strictness == Strictness.STRICT || interventionsTonight >= 3 -> "direct and firm, still kind"
        interventionsTonight >= 2 -> "warm but more direct"
        else -> "gentle and curious"
    }

    companion object {
        fun from(
            snap: TranceSnapshot,
            hour: Int,
            minute: Int,
            goal: String,
            interventionsTonight: Int,
            strictness: Strictness,
            recent: List<String>,
        ) = ReframeContext(
            time = String.format(Locale.US, "%02d:%02d", hour, minute),
            app = FeedApps.label(snap.feedPackage),
            minutes = snap.sessionMinutes.toInt().coerceAtLeast(1),
            swipes = snap.sessionSwipes,
            dark = snap.isDark,
            goal = goal.ifBlank { "use my phone more intentionally" },
            interventionsTonight = interventionsTonight,
            strictness = strictness,
            recent = recent.takeLast(3),
            angle = PromptBuilder.ANGLES.random(),
            swipesPerMin = snap.swipesPerMinute.toInt(),
            secondsPerVideo = (snap.medianDwellMs / 1000).toInt(),
            tapsPer100Swipes = if (snap.sessionSwipes == 0) 0 else 100 * snap.sessionTaps / snap.sessionSwipes,
            late = snap.isLate,
            lyingDown = snap.lyingDown,
            mainSignal = mainSignal(snap),
        )

        /** The score component contributing most points, in plain words, so the message addresses it. */
        fun mainSignal(snap: TranceSnapshot): String = with(snap.breakdown) {
            listOf(
                "fast swiping" to 25 * swipeRate,
                "very short views" to 20 * shortDwell,
                "a long unbroken session" to 20 * sessionLength,
                "passive watching with almost no taps" to 10 * lowTapRatio,
                "scrolling in the dark late at night" to 15 * darkLate,
                "scrolling while lying down" to 10 * lyingDown,
            ).maxBy { it.second }.let { if (it.second > 0) it.first else "a long unbroken session" }
        }
    }
}

object PromptBuilder {
    const val SYSTEM = "You write one phone notification for SnapOut, a calm, non-judgmental attention coach that runs privately on the user's phone. " +
        "The user is stuck in a mindless scrolling loop right now. Write at most 2 short sentences (30 words max). " +
        "First reflect the main_signal or one concrete number from the context, then ask ONE open question or suggest one small action tied to the user's goal. " +
        "Match the requested tone. No shaming, no diagnosis, no medical claims, no emojis, no hashtags. " +
        "Never repeat or closely paraphrase any message in \"recent\". Output only the message."

    /** One is picked at random per message so consecutive nudges take different approaches. */
    val ANGLES = listOf(
        "ask what they were originally looking for when they opened the app",
        "invite them to notice their body: eyes, neck, breathing",
        "connect the moment to how tomorrow morning will feel",
        "ask whether they remember any of the last few videos",
        "suggest one small concrete thing to do for the next five minutes",
        "ask what they would choose if they decided on purpose",
        "point out the time and gently link it to their goal",
        "ask how they feel now compared to when they started scrolling",
    )

    fun userPrompt(ctx: ReframeContext) =
        "Context: ${ctx.toJson()}\nAngle for this message: ${ctx.angle.ifBlank { ANGLES.first() }}.\nWrite the message."

    /** System instructions + user turn in one message (works with every model's chat template). */
    fun fullPrompt(ctx: ReframeContext) = "$SYSTEM\n\n${userPrompt(ctx)}"

    /**
     * ChatML template for Qwen models. The bundled Qwen3 template drops non-string message content, which is
     * how LiteRT-LM passes it, so the model would see an empty prompt. The pre-closed think block skips reasoning.
     */
    const val QWEN_TEMPLATE = "{%- for message in messages -%}{{ '<|im_start|>' + message.role + '\\n' }}" +
        "{%- if message.content is string -%}{{ message.content }}" +
        "{%- else -%}{%- for c in message.content -%}{%- if c.text is defined -%}{{ c.text }}{%- endif -%}{%- endfor -%}{%- endif -%}" +
        "{{ '<|im_end|>\\n' }}{%- endfor -%}" +
        "{%- if add_generation_prompt -%}{{ '<|im_start|>assistant\\n<think>\\n\\n</think>\\n\\n' }}{%- endif -%}"

    /** What to show while tokens stream in: hides reasoning blocks, including one still open. */
    fun streamView(raw: String): String =
        raw.replace(Regex("(?s)<think>.*?</think>"), "").substringBefore("<think>").trimStart()

    const val INSIGHTS_SYSTEM = "You are SnapOut's on-device digital wellbeing analyst. Below is the user's dashboard usage summary " +
        "from their local phone sensors and feed sessions over the past few days, including overall totals and a specific telemetry breakdown for each monitored app (sessions, minutes, scroll meters, average seconds per video, engagement taps, longest session, and late-night percentage). " +
        "Analyze what this dashboard data reveals about their scrolling loops across different apps, swipe speeds, feed mileage, late-night habits, and nudge responses. " +
        "Write exactly 3 lines starting with \"Insight: \", each highlighting an impactful observation directly citing specific app numbers or comparisons (e.g., top app minutes/meters, rapid swiping on TikTok, or late-night scrolling in bed). " +
        "Then write exactly 3 lines starting with \"Tip: \", each providing a compassionate, practical micro-action tailored to a specific app habit or tied to their goal. " +
        "Keep each line concise (under 25 words). No shaming, no emojis, no medical claims. Output only those 6 lines."

    fun insightsPrompt(summaryJson: String, goal: String) =
        "$INSIGHTS_SYSTEM\n\nUser Goal: ${goal.ifBlank { "use my phone intentionally and reduce mindless scrolling" }}\nDashboard Statistics (including per-app breakdown): $summaryJson\nWrite the 6 lines."

    private val insightLine = Regex("^(insight|tip)s?\\s*\\d*\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)

    /** Extracts "Insight:" / "Tip:" lines from model output, dropping reasoning, markdown and unsafe lines. */
    fun parseInsights(raw: String): List<InsightItem> =
        raw.replace(Regex("(?s)<think>.*?(</think>|$)"), "").lines()
            .map { it.trim().trimStart('-', '*', '•', ' ').replace("**", "").replace(Regex("^\\d+[.)]\\s*"), "") }
            .mapNotNull { insightLine.find(it) }
            .map { InsightItem(it.groupValues[1].lowercase() == "tip", it.groupValues[2].trim()) }
            .filter { item -> item.text.length > 8 && banned.none { item.text.lowercase().contains(it) } }
            .let { items -> items.filterNot { it.tip }.take(4) + items.filter { it.tip }.take(4) }

    const val CHAT_SYSTEM = "You are SnapOut, an empathetic on-device digital wellbeing and attention coach. " +
        "You help the user break mindless short-form doom-scrolling and build intentional phone habits. " +
        "Use the user's goal and statistics below as personal context. " +
        "Provide warm, practical, actionable advice and psychological reframing. " +
        "When asked about nudges, explain how to use the circuit-breaker (breathing pause, putting the phone down, or leaving the feed). " +
        "When asked how to improve, give concrete, compassionate behavioral tips tailored to their usage. " +
        "Keep your response natural, conversational, and concise (2 to 4 sentences). Never shame the user, no emojis, no medical claims."

    fun chatPrompt(question: String, summaryJson: String, goal: String, history: List<ChatMsg>): String {
        val past = history.joinToString("\n") { (if (it.fromUser) "User: " else "SnapOut: ") + it.text }
        return "$CHAT_SYSTEM\n\nUser's Personal Goal: ${goal.ifBlank { "use my phone more intentionally and sleep earlier" }}\n" +
            "User's 7-Day Usage Telemetry: $summaryJson\n" +
            (if (past.isNotBlank()) "Conversation History:\n$past\n" else "") +
            "User: $question\nSnapOut:"
    }

    fun cleanChat(raw: String): String =
        raw.replace(Regex("(?s)<think>.*?(</think>|$)"), "")
            .replace("**", "")
            .trim()
            .removePrefix("SnapOut:").removePrefix("Assistant:").trim()
            .lines().takeWhile { !it.trimStart().startsWith("User:") }.joinToString("\n").trim()
            .take(600)

    /** Answer from the stats alone, used when no model is available. */
    fun ruleAnswer(question: String, s: UsageSummary): String {
        if (s.empty) return "No feed sessions logged yet. Scroll with SnapOut on, or tap Load sample week (demo) to see stats and tips."
        val q = question.lowercase()
        val top = s.apps.maxByOrNull { it.minutes }
        fun has(vararg w: String) = w.any { q.contains(it) }
        return when {
            has("nudge", "respond", "notification") ->
                "When a nudge appears, use the 10-second breathing pause to reset your attention. You can choose to leave the feed, put your phone face down, or snooze if needed. So far, you've left the feed ${s.wentHome} times out of ${s.nudges} nudges."
            has("improve", "tip", "help", "should", "stop", "reduce", "better") ->
                "The most effective step is targeting your top app (${top?.app ?: "social media"}). Try setting a 10-minute bedtime cutoff and charging your phone across the room—currently ${s.lateNightPct}% of your scrolling happens late at night."
            has("app", "most", "often", "which", "where") && top != null ->
                "${top.app} is your most active feed: ${top.minutes} of ${s.totalMinutes} total minutes over ${top.sessions} sessions." +
                    s.apps.filter { it != top }.joinToString("") { " ${it.app}: ${it.minutes} min." }
            has("when", "hour", "time", "night", "late") ->
                "Your heaviest scrolling happens around ${s.peakHour?.let { "%02d:00".format(it) } ?: "nighttime"}; ${s.lateNightPct}% of your feed time is after 22:00 in bed."
            has("swipe", "video", "fast", "skip", "speed") ->
                "You average about ${"%.1f".format(s.avgSecondsPerVideo)} seconds per video and ${s.tapsPer100Swipes} taps per 100 swipes, showing a fast, passive consumption pattern."
            has("tap", "like", "comment") -> "You tap ${s.tapsPer100Swipes} times per 100 swipes—meaning 98% of your time is spent in passive watching."
            has("long", "session", "minute", "much") ->
                "You average ${s.minutesPerDay} minutes per active day across ${s.sessions} sessions, with your longest session lasting ${s.longestSessionMinutes} minutes."
            has("bed", "lying", "lie") -> "${s.lyingPct}% of your sessions happen while lying down, and ${s.darkPct}% in total darkness."
            else -> "I'm your SnapOut wellbeing coach. Ask me how to improve your focus, how to respond to nudges, or about your top scrolling habits."
        }
    }

    private val banned = listOf("addict", "disorder", "pathetic", "lazy", "loser", "shame", "diagnos", "depress")

    /** Returns a cleaned message, or null if the model output is unusable. */
    fun postFilter(raw: String): String? {
        var t = raw.replace(Regex("(?s)<think>.*?(</think>|$)"), "")
            .trim().removePrefix("Message:").trim().trim('"').replace(Regex("\\s+"), " ")
        if (t.isBlank()) return null
        if (banned.any { t.lowercase().contains(it) }) return null
        val words = t.split(" ")
        if (words.size > 45) t = words.take(45).joinToString(" ").trimEnd(',', ';') + "…"
        return t
    }
}
