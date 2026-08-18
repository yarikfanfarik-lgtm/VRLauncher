package com.capylabs.vrlauncher

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.PI

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var scene: VrSceneView
    private lateinit var handOverlay: HandOverlayView
    private lateinit var sensors: SensorManager
    private var handTracker: HandTracker? = null
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var calibrated = false
    private var yawOffset = 0f
    private var pitchOffset = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        scene = VrSceneView(this)
        handOverlay = HandOverlayView(this).apply { isClickable = false }
        val root = FrameLayout(this)
        root.addView(scene, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        root.addView(handOverlay, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)

        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startHandTracking()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        }
    }

    private fun startHandTracking() {
        if (handTracker != null) return
        handTracker = HandTracker(
            context = this,
            onHands = { hands -> handOverlay.setHands(hands) },
            onError = { /* Camera/model errors must not stop the VR desktop. */ }
        ).also { it.start(this) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startHandTracking()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR && event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val raw = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(raw, event.values)
        val remapped = FloatArray(9)
        val rotation = if (android.os.Build.VERSION.SDK_INT >= 30) display?.rotation ?: Surface.ROTATION_0 else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        when (rotation) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remapped)
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remapped)
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(raw, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remapped)
            else -> System.arraycopy(raw, 0, remapped, 0, 9)
        }
        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        var yaw = orientation[0]
        var pitch = orientation[1]
        if (!calibrated) {
            yawOffset = yaw
            pitchOffset = pitch
            smoothYaw = 0f
            smoothPitch = 0f
            calibrated = true
        }
        yaw = shortestAngle(yaw - yawOffset)
        pitch = (pitch - pitchOffset).coerceIn((-PI / 2).toFloat(), (PI / 2).toFloat())
        val alpha = 0.28f
        smoothYaw += shortestAngle(yaw - smoothYaw) * alpha
        smoothPitch += (pitch - smoothPitch) * alpha
        scene.setHead(smoothYaw, smoothPitch)
    }

    private fun shortestAngle(value: Float): Float {
        var v = value
        while (v > PI) v -= (2f * PI).toFloat()
        while (v < -PI) v += (2f * PI).toFloat()
        return v
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        handTracker?.stop()
        sensors.unregisterListener(this)
        super.onDestroy()
    }

    companion object { private const val CAMERA_REQUEST = 7 }
}
