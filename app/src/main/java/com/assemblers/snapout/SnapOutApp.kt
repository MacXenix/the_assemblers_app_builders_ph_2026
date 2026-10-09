package com.assemblers.snapout

import android.app.Application
import com.assemblers.snapout.ai.LlmManager
import com.assemblers.snapout.data.SettingsState
import com.assemblers.snapout.data.Settings
import com.assemblers.snapout.data.SnapOutDb
import com.assemblers.snapout.service.TranceEngine

class SnapOutApp : Application() {
    lateinit var settings: Settings
    lateinit var db: SnapOutDb
    lateinit var llm: LlmManager
    lateinit var engine: TranceEngine

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        db = SnapOutDb(this)
        llm = LlmManager(this) { settings.state.value.modelFile }
        engine = TranceEngine(this, settings, db, llm)
        llm.refreshModelPresence()
    }
}

val android.content.Context.app: SnapOutApp get() = applicationContext as SnapOutApp
val SettingsState.isConfigured get() = onboarded
