package com.assemblers.snapout.ai

import com.assemblers.snapout.core.FeedApps
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
) {
    fun toJson(): String {
        val recentJson = recent.joinToString(",") { "\"" + it.replace("\"", "'") + "\"" }
        return """{"time":"$time","app":"$app","minutes":$minutes,"swipes":$swipes,"dark":$dark,""" +
            """"goal":"${goal.replace("\"", "'")}","interruption_number":$interventionsTonight,""" +
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
        )
    }
}

object PromptBuilder {
    const val SYSTEM = "You are SnapOut, a calm, non-judgmental attention coach that runs privately on the user's phone. " +
        "The user is stuck in a mindless scrolling loop. Reply in at most 2 short sentences (35 words max). " +
        "First reflect one concrete fact from the context, then ask ONE open question tied to the user's goal. " +
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
