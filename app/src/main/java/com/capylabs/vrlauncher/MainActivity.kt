package com.capylabs.vrlauncher

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var scene: VrSceneView
    private lateinit var sensors: SensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        scene = VrSceneView(this)
        setContentView(scene)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 7)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val matrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(matrix, orientation)
            scene.setHead(orientation[0], orientation[1])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensors.unregisterListener(this)
        super.onDestroy()
    }
}
