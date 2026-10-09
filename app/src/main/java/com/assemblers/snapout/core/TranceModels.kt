package com.assemblers.snapout.core

enum class TranceState(val label: String) {
    FOCUSED("Focused"),
    DRIFTING("Drifting"),
    ZOMBIE("Zombie-scrolling"),
}

/** Metadata of one TYPE_VIEW_SCROLLED event. Indices are list positions (-1 if the app doesn't report them). */
data class ScrollSignal(val dx: Int = 0, val dy: Int = 0, val fromIndex: Int = -1, val toIndex: Int = -1, val itemCount: Int = -1) {
    val horizontalOnly get() = dx != 0 && dy == 0
}

enum class Strictness { GENTLE, BALANCED, STRICT }

data class ScoreBreakdown(
    val swipeRate: Double = 0.0,
    val shortDwell: Double = 0.0,
    val sessionLength: Double = 0.0,
    val lowTapRatio: Double = 0.0,
    val darkLate: Double = 0.0,
    val lyingDown: Double = 0.0,
)

data class TranceSnapshot(
    val score: Int = 0,
    val state: TranceState = TranceState.FOCUSED,
    val feedPackage: String? = null,
    val swipesPerMinute: Double = 0.0,
    val medianDwellMs: Long = 0,
    val sessionMinutes: Double = 0.0,
    val sessionSwipes: Int = 0,
    val sessionDistanceMeters: Double = 0.0,
    val lux: Float? = null,
    val isDark: Boolean = false,
    val isLate: Boolean = false,
    val breakdown: ScoreBreakdown = ScoreBreakdown(),
)

data class ScorerConfig(
    val baselineSwipesPerMin: Double,
    val fastSwipesPerMin: Double,
    val longSessionMinutes: Double,
    val zombieHoldMs: Long,
    val cooldownMs: Long,
    /** Demo: score from scroll behaviour alone, so it triggers in a bright room at noon. */
    val behaviourOnly: Boolean = false,
) {
    companion object {
        val NORMAL = ScorerConfig(
            baselineSwipesPerMin = 6.0,
            fastSwipesPerMin = 20.0,
            longSessionMinutes = 20.0,
            zombieHoldMs = 30_000,
            cooldownMs = 10 * 60_000,
        )
        val DEMO = ScorerConfig(
            baselineSwipesPerMin = 2.0,
            fastSwipesPerMin = 10.0,
            longSessionMinutes = 1.0,
            zombieHoldMs = 5_000,
            cooldownMs = 60_000,
            behaviourOnly = true,
        )
    }
}
