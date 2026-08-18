package com.capylabs.vrlauncher

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HandTracker(
    private val context: Context,
    private val onHands: (List<HandPoint>) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var landmarker: HandLandmarker? = null

    fun start(owner: LifecycleOwner) {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.45f)
                .setMinHandPresenceConfidence(0.45f)
                .setMinTrackingConfidence(0.35f)
                .setResultListener { result, _ ->
                    val points = ArrayList<HandPoint>(result.landmarks().size)
                    result.landmarks().forEach { landmarks ->
                        val palm = landmarks.getOrNull(0)
                        val index = landmarks.getOrNull(8)
                        val thumb = landmarks.getOrNull(4)
                        if (palm != null && index != null && thumb != null) {
                            points += HandPoint(
                                palmX = palm.x(), palmY = palm.y(),
                                indexX = index.x(), indexY = index.y(),
                                pinch = distance(index, thumb) < 0.075f
                            )
                        }
                    }
                    ContextCompat.getMainExecutor(context).execute { onHands(points) }
                }
                .setErrorListener { error ->
                    ContextCompat.getMainExecutor(context).execute { onError(error.message ?: "Hand tracking error") }
                }
                .build()
            landmarker = HandLandmarker.createFromOptions(context, options)

            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    analysis.setAnalyzer(executor) { image -> analyze(image) }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        owner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        analysis
                    )
                } catch (t: Throwable) {
                    onError(t.message ?: "Camera start failed")
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (t: Throwable) {
            onError(t.message ?: "Hand tracker initialization failed")
        }
    }

    private fun analyze(image: ImageProxy) {
        try {
            val mediaImage = image.image
            if (mediaImage == null) {
                image.close()
                return
            }
            val mpImage = MediaImageBuilder(mediaImage).build()
            val processing = ImageProcessingOptions.builder()
                .setRotationDegrees(image.imageInfo.rotationDegrees)
                .build()
            landmarker?.detectAsync(mpImage, processing, SystemClock.uptimeMillis())
        } catch (t: Throwable) {
            ContextCompat.getMainExecutor(context).execute {
                onError(t.message ?: "Hand frame processing failed")
            }
        } finally {
            image.close()
        }
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun stop() {
        landmarker?.close()
        landmarker = null
        executor.shutdownNow()
    }
}

data class HandPoint(
    val palmX: Float,
    val palmY: Float,
    val indexX: Float,
    val indexY: Float,
    val pinch: Boolean
)
