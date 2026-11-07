package dev.uzair.levitate

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

class MotionSensors(context: Context, private val engine: MotionEngine,
                    private val onFrame: (MotionFrame) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrix = FloatArray(9)
    private var thread: HandlerThread? = null
    val available get() = sensor != null
    val description get() = sensor?.let { "${it.name} · ${it.vendor}" } ?: "No rotation sensor. Touchpad is still available."
    var samplePeriodUs = 16_667

    fun start() {
        if (thread != null || sensor == null) return
        val worker = HandlerThread("levitate-sensors").also { it.start() }
        thread = worker
        if (!manager.registerListener(this, sensor, samplePeriodUs, Handler(worker.looper))) stop()
    }

    fun stop() {
        manager.unregisterListener(this)
        thread?.quitSafely()
        thread = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        onFrame(engine.update(matrix, event.timestamp))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
