package com.assemblers.snapout.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Ambient light + gravity only; registered only while a feed session is active. */
class SensorMonitor(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var active = false

    @Volatile var lux: Float? = null
        private set
    @Volatile var gravityY: Float? = null
        private set

    fun start() {
        if (active) return
        active = true
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        sm.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        if (!active) return
        active = false
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_LIGHT -> lux = e.values[0]
            Sensor.TYPE_GRAVITY -> gravityY = e.values[1]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
