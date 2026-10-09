package com.assemblers.snapout.ai

object FallbackTemplates {
    private val anytimeTemplates = listOf(
        "You've been on {app} for {min} minutes. What were you hoping to find when you opened it?",
        "{swipes} swipes so far. Is the next one going to feel any different?",
        "It's {time}. Does this scroll move you closer to \"{goal}\"?",
        "Your thumb has been doing the deciding for {min} minutes. What would you choose on purpose right now?",
        "Pause. Notice your breathing. What do you actually need in this moment?",
        "{min} minutes in. If you put the phone down now, what would future-you thank you for?",
        "Quick check-in: are you enjoying this, or just continuing it?",
        "You set a goal: \"{goal}\". Is now a good moment to honor it?",
        "{swipes} videos in {min} minutes. Which one do you remember?",
        "Your eyes have been on {app} for {min} minutes. How do they feel right now?",
        "What is one thing you could do in the next five minutes that you'd actually enjoy?",
        "Is {app} giving you what you came for, or just more of the same?",
    )

    private val morningTemplates = listOf(
        "Good morning. Is {app} how you wanted to start your day?",
        "It's {time}. Take a breath and decide what comes next for your morning.",
    )

    private val afternoonTemplates = listOf(
        "It's {time}. Step back for a moment—are you taking a break or stuck in a loop?",
        "You've scrolled {swipes} videos this afternoon. Stretch your neck and look away for a minute.",
    )

    private val eveningTemplates = listOf(
        "The feed never ends, but your evening does. What do you want the rest of it to be?",
        "It's {time}. What would feel relaxing to do offline tonight?",
    )

    private val lateNightTemplates = listOf(
        "Tomorrow-you wakes up in a few hours. What would they want you to do now?",
        "It's {time} in bed. Give your eyes and mind a rest.",
        "Your body is tired. Close {app} and let yourself sleep.",
    )

    fun pick(ctx: ReframeContext): String {
        val candidates = anytimeTemplates + when {
            ctx.late || ctx.timeOfDay == "late_night" -> lateNightTemplates
            ctx.timeOfDay == "morning" -> morningTemplates
            ctx.timeOfDay == "afternoon" -> afternoonTemplates
            else -> eveningTemplates
        }
        val pool = candidates.filterNot { t -> ctx.recent.any { it.startsWith(fill(t, ctx).take(20)) } }
        return fill((pool.ifEmpty { candidates }).random(), ctx)
    }

    private fun fill(t: String, ctx: ReframeContext) = t
        .replace("{app}", ctx.app)
        .replace("{min}", ctx.minutes.toString())
        .replace("{swipes}", ctx.swipes.toString())
        .replace("{time}", ctx.time)
        .replace("{goal}", ctx.goal)
}
