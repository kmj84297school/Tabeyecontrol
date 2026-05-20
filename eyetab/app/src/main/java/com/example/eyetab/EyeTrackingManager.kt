package com.example.eyetab

import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.eyetab.Constants.FACE_LOST_TIMEOUT_MS
import com.example.eyetab.Constants.SCROLL_COOLDOWN_MS
import com.example.eyetab.Constants.SCROLL_EDGE_RATIO
import com.example.eyetab.Constants.SMOOTHING_WINDOW
import com.example.eyetab.Constants.TAP_COOLDOWN_MS
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class EyeTrackingManager private constructor() {

    interface Listener {
        fun onCursorMoved(x: Float, y: Float)
        fun onTapRequest(x: Float, y: Float)
        fun onScrollRequest(direction: ScrollDirection, x: Float, y: Float)
        fun onFaceLost()
    }

    enum class ScrollDirection { UP, DOWN }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val filterX = MovingAverageFilter(SMOOTHING_WINDOW)
    private val filterY = MovingAverageFilter(SMOOTHING_WINDOW)
    private lateinit var mapper: CalibrationMapper
    private lateinit var blinkDetector: BlinkDetector

    private var listener: Listener? = null
    private var lastTapTime = 0L
    private var lastScrollTime = 0L
    private var faceLostRunnable: Runnable? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var screenWidth = 1920
    private var screenHeight = 1200

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    fun start(context: Context, lifecycleOwner: LifecycleOwner, listener: Listener) {
        this.listener = listener
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        mapper = CalibrationMapper(screenWidth, screenHeight)
        blinkDetector = BlinkDetector(
            onShortBlink = {
                val now = System.currentTimeMillis()
                if (now - lastTapTime >= TAP_COOLDOWN_MS) {
                    lastTapTime = now
                    mainHandler.post { listener.onTapRequest(cursorX, cursorY) }
                }
            },
            onLongBlink = {
                val now = System.currentTimeMillis()
                if (now - lastScrollTime >= SCROLL_COOLDOWN_MS) {
                    lastScrollTime = now
                    val direction = when {
                        cursorY < screenHeight * SCROLL_EDGE_RATIO -> ScrollDirection.UP
                        cursorY > screenHeight * (1f - SCROLL_EDGE_RATIO) -> ScrollDirection.DOWN
                        else -> return@BlinkDetector
                    }
                    mainHandler.post { listener.onScrollRequest(direction, cursorX, cursorY) }
                }
            }
        )

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::analyzeImage) }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis
            )
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        listener = null
        analysisExecutor.shutdown()
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeImage(proxy: ImageProxy) {
        val mediaImage = proxy.image ?: run { proxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        val frameWidth = proxy.width.toFloat()
        val frameHeight = proxy.height.toFloat()

        detector.process(image)
            .addOnSuccessListener { faces ->
                proxy.close()
                if (faces.isEmpty()) {
                    scheduleFaceLost()
                    return@addOnSuccessListener
                }
                cancelFaceLost()
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return@addOnSuccessListener
                val bb = face.boundingBox
                val rawNx = 1f - (bb.centerX() / frameWidth)
                val rawNy = bb.centerY() / frameHeight
                val (px, py) = mapper.map(rawNx, rawNy)
                val smoothX = filterX.add(px)
                val smoothY = filterY.add(py)
                cursorX = smoothX
                cursorY = smoothY

                val left = face.leftEyeOpenProbability ?: 1f
                val right = face.rightEyeOpenProbability ?: 1f
                blinkDetector.process(left, right)

                mainHandler.post { listener?.onCursorMoved(smoothX, smoothY) }
            }
            .addOnFailureListener { proxy.close() }
    }

    private fun scheduleFaceLost() {
        if (faceLostRunnable != null) return
        val r = Runnable {
            faceLostRunnable = null
            mainHandler.post { listener?.onFaceLost() }
        }
        faceLostRunnable = r
        mainHandler.postDelayed(r, FACE_LOST_TIMEOUT_MS)
    }

    private fun cancelFaceLost() {
        faceLostRunnable?.let { mainHandler.removeCallbacks(it) }
        faceLostRunnable = null
    }

    companion object {
        val instance: EyeTrackingManager by lazy { EyeTrackingManager() }
    }
}
