package com.assemblers.snapout.ai

object FallbackTemplates {
    private val templates = listOf(
        "You've been on {app} for {min} minutes. What were you hoping to find when you opened it?",
        "{swipes} swipes so far. Is the next one going to feel any different?",
        "It's {time}. Does this scroll move you closer to \"{goal}\"?",
        "Your thumb has been doing the deciding for {min} minutes. What would you choose on purpose right now?",
        "Pause. Notice your breathing. What do you actually need in this moment?",
        "{min} minutes in. If you put the phone down now, what would future-you thank you for?",
        "The feed never ends, but your evening does. What do you want the rest of it to be?",
        "Quick check-in: are you enjoying this, or just continuing it?",
        "You set a goal: \"{goal}\". Is now a good moment to honor it?",
        "{swipes} videos in {min} minutes. Which one do you remember?",
        "Your eyes have been on {app} for {min} minutes. How do they feel right now?",
        "What is one thing you could do in the next five minutes that you'd actually enjoy?",
        "Tomorrow-you wakes up in a few hours. What would they want you to do now?",
        "Is {app} giving you what you came for, or just more of the same?",
    )

    fun pick(ctx: ReframeContext): String {
        val pool = templates.filterNot { t -> ctx.recent.any { it.startsWith(fill(t, ctx).take(20)) } }
        return fill((pool.ifEmpty { templates }).random(), ctx)
    }

    private fun fill(t: String, ctx: ReframeContext) = t
        .replace("{app}", ctx.app)
        .replace("{min}", ctx.minutes.toString())
        .replace("{swipes}", ctx.swipes.toString())
        .replace("{time}", ctx.time)
        .replace("{goal}", ctx.goal)
}
