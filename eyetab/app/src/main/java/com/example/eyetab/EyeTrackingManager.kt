package com.example.eyetab

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EyeTrackingManager private constructor() {

    private val tag = "EyeTab"

    interface Listener {
        fun onCursorMoved(x: Float, y: Float)
        fun onTapRequest(x: Float, y: Float)
        fun onScrollRequest(direction: ScrollDirection, x: Float, y: Float)
        fun onFaceLost()
    }

    enum class ScrollDirection { UP, DOWN }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val filterX = MovingAverageFilter(Constants.SMOOTHING_WINDOW)
    private val filterY = MovingAverageFilter(Constants.SMOOTHING_WINDOW)

    private var listener: Listener? = null
    private var lastTapTime = 0L
    private var lastScrollTime = 0L
    private var faceLostRunnable: Runnable? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var screenWidth = 1920
    private var screenHeight = 1200

    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var mapper: CalibrationMapper? = null
    private var blinkDetector: BlinkDetector? = null

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }

    fun start(context: Context, lifecycleOwner: LifecycleOwner, listener: Listener) {
        try {
            this.listener = listener
            filterX.reset()
            filterY.reset()

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                screenWidth = bounds.width()
                screenHeight = bounds.height()
            } else {
                @Suppress("DEPRECATION")
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }

            mapper = CalibrationMapper(screenWidth, screenHeight)
            blinkDetector = BlinkDetector(
                onShortBlink = {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime >= Constants.TAP_COOLDOWN_MS) {
                        lastTapTime = now
                        mainHandler.post { listener.onTapRequest(cursorX, cursorY) }
                    }
                },
                onLongBlink = {
                    val now = System.currentTimeMillis()
                    if (now - lastScrollTime >= Constants.SCROLL_COOLDOWN_MS) {
                        lastScrollTime = now
                        val direction = when {
                            cursorY < screenHeight * Constants.SCROLL_EDGE_RATIO -> ScrollDirection.UP
                            cursorY > screenHeight * (1f - Constants.SCROLL_EDGE_RATIO) -> ScrollDirection.DOWN
                            else -> return@BlinkDetector
                        }
                        mainHandler.post { listener.onScrollRequest(direction, cursorX, cursorY) }
                    }
                }
            )

            val executor = Executors.newSingleThreadExecutor()
            analysisExecutor = executor

            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val provider = future.get()
                    cameraProvider = provider
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, ::analyzeImage) }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis
                    )
                } catch (e: Exception) {
                    Log.e(tag, "camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(context))

        } catch (e: Exception) {
            Log.e(tag, "start failed", e)
        }
    }

    fun stop() {
        try {
            listener = null
            blinkDetector?.reset()
            cameraProvider?.unbindAll()
            cameraProvider = null
            analysisExecutor?.shutdown()
            analysisExecutor = null
            cancelFaceLost()
        } catch (e: Exception) {
            Log.e(tag, "stop failed", e)
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeImage(proxy: ImageProxy) {
        try {
            val mediaImage = proxy.image ?: run { proxy.close(); return }
            val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
            val frameWidth = proxy.width.toFloat()
            val frameHeight = proxy.height.toFloat()

            detector.process(image)
                .addOnSuccessListener { faces ->
                    try {
                        proxy.close()
                        if (faces.isEmpty()) {
                            scheduleFaceLost()
                            return@addOnSuccessListener
                        }
                        cancelFaceLost()
                        val face = faces.maxByOrNull {
                            it.boundingBox.width() * it.boundingBox.height()
                        } ?: return@addOnSuccessListener

                        val bb = face.boundingBox
                        val rawNx = 1f - (bb.centerX() / frameWidth)
                        val rawNy = bb.centerY() / frameHeight
                        val (px, py) = mapper?.map(rawNx, rawNy) ?: return@addOnSuccessListener
                        val smoothX = filterX.add(px)
                        val smoothY = filterY.add(py)
                        cursorX = smoothX
                        cursorY = smoothY

                        val left = face.leftEyeOpenProbability ?: 1f
                        val right = face.rightEyeOpenProbability ?: 1f
                        blinkDetector?.process(left, right)

                        mainHandler.post { listener?.onCursorMoved(smoothX, smoothY) }
                    } catch (e: Exception) {
                        Log.e(tag, "analyzeImage success handler", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "ML Kit detect failed", e)
                    proxy.close()
                }
        } catch (e: Exception) {
            Log.e(tag, "analyzeImage outer", e)
            try { proxy.close() } catch (_: Exception) {}
        }
    }

    private fun scheduleFaceLost() {
        if (faceLostRunnable != null) return
        val r = Runnable {
            faceLostRunnable = null
            mainHandler.post { listener?.onFaceLost() }
        }
        faceLostRunnable = r
        mainHandler.postDelayed(r, Constants.FACE_LOST_TIMEOUT_MS)
    }

    private fun cancelFaceLost() {
        faceLostRunnable?.let { mainHandler.removeCallbacks(it) }
        faceLostRunnable = null
    }

    companion object {
        val instance: EyeTrackingManager by lazy { EyeTrackingManager() }
    }
}
