package com.assemblers.snapout.data

import android.content.Context
import com.assemblers.snapout.core.Strictness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(
    val enabled: Boolean = true,
    val demoMode: Boolean = false,
    val forceNight: Boolean = false,
    val speak: Boolean = false,
    val strictness: Strictness = Strictness.BALANCED,
    val goal: String = "",
    val onboarded: Boolean = false,
    val modelFile: String = "",
    val insights: String = "",
    val insightsAt: Long = 0L,
    val insightsBy: String = "",
    val enabledFeeds: Set<String> = com.assemblers.snapout.core.FeedApps.names.keys,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("snapout", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun load() = SettingsState(
        enabled = prefs.getBoolean("enabled", true),
        demoMode = prefs.getBoolean("demoMode", false),
        forceNight = prefs.getBoolean("forceNight", false),
        speak = prefs.getBoolean("speak", false),
        strictness = Strictness.valueOf(prefs.getString("strictness", Strictness.BALANCED.name)!!),
        goal = prefs.getString("goal", "")!!,
        onboarded = prefs.getBoolean("onboarded", false),
        modelFile = prefs.getString("modelFile", "")!!,
        insights = prefs.getString("insights", "")!!,
        insightsAt = prefs.getLong("insightsAt", 0L),
        insightsBy = prefs.getString("insightsBy", "")!!,
        enabledFeeds = prefs.getStringSet("enabledFeeds", com.assemblers.snapout.core.FeedApps.names.keys) ?: com.assemblers.snapout.core.FeedApps.names.keys,
    )

    fun update(f: (SettingsState) -> SettingsState) {
        val s = f(_state.value)
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putBoolean("demoMode", s.demoMode)
            .putBoolean("forceNight", s.forceNight)
            .putBoolean("speak", s.speak)
            .putString("strictness", s.strictness.name)
            .putString("goal", s.goal)
            .putBoolean("onboarded", s.onboarded)
            .putString("modelFile", s.modelFile)
            .putString("insights", s.insights)
            .putLong("insightsAt", s.insightsAt)
            .putString("insightsBy", s.insightsBy)
            .putStringSet("enabledFeeds", s.enabledFeeds)
            .apply()
        _state.value = s
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = load()
    }
}
