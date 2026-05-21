package com.example.eyetab

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.eyetab.Constants.FACE_LOST_TIMEOUT_MS
import com.example.eyetab.Constants.SCROLL_COOLDOWN_MS
import com.example.eyetab.Constants.SCROLL_EDGE_RATIO
import com.example.eyetab.Constants.SMOOTHING_WINDOW
import com.example.eyetab.Constants.TAP_COOLDOWN_MS

class EyeTrackingManager private constructor() {

    interface Listener {
        fun onCursorMoved(x: Float, y: Float)
        fun onTapRequest(x: Float, y: Float)
        fun onScrollRequest(direction: ScrollDirection, x: Float, y: Float)
        fun onFaceLost()
    }

    enum class ScrollDirection { UP, DOWN }

    private val mainHandler = Handler(Looper.getMainLooper())

    // Created in start(), destroyed in stop()
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private val filterX = MovingAverageFilter(SMOOTHING_WINDOW)
    private val filterY = MovingAverageFilter(SMOOTHING_WINDOW)
    private var mapper: CalibrationMapper? = null
    private var blinkDetector: BlinkDetector? = null

    private var listener: Listener? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var screenWidth = 1920
    private var screenHeight = 1200
    private var sensorWidth = 1920
    private var sensorHeight = 1080
    private var frontCameraId = ""
    private var cursorX = 0f
    private var cursorY = 0f
    private var lastTapTime = 0L
    private var lastScrollTime = 0L
    private var faceLostRunnable: Runnable? = null
    private var running = false

    fun start(context: Context, listener: Listener) {
        if (running) return
        try {
            this.listener = listener
            running = true

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
                        val dir = when {
                            cursorY < screenHeight * SCROLL_EDGE_RATIO -> ScrollDirection.UP
                            cursorY > screenHeight * (1f - SCROLL_EDGE_RATIO) -> ScrollDirection.DOWN
                            else -> return@BlinkDetector
                        }
                        mainHandler.post { listener.onScrollRequest(dir, cursorX, cursorY) }
                    }
                }
            )

            val ct = HandlerThread("EyeTab-Camera").also { it.start() }
            cameraThread = ct
            cameraHandler = Handler(ct.looper)

            openFrontCamera(context)
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            running = false
        }
    }

    private fun openFrontCamera(context: Context) {
        try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (id in camManager.cameraIdList) {
                val chars = camManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id
                    val size = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    sensorWidth = size?.width ?: 1920
                    sensorHeight = size?.height ?: 1080
                    break
                }
            }
            if (frontCameraId.isEmpty()) {
                Log.e(TAG, "No front camera found")
                return
            }
            camManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCapture(camera)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    camera.close(); cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close(); cameraDevice = null
                }
            }, cameraHandler)
        } catch (e: Exception) {
            Log.e(TAG, "openFrontCamera failed", e)
        }
    }

    private fun startCapture(camera: CameraDevice) {
        try {
            val reader = ImageReader.newInstance(320, 240, ImageFormat.JPEG, 2)
            reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, cameraHandler)

            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(reader.surface)
                                set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                    CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL)
                            }
                            session.setRepeatingRequest(req.build(), captureCallback, cameraHandler)
                        } catch (e: Exception) {
                            Log.e(TAG, "setRepeatingRequest failed", e)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "captureSession configure failed")
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "startCapture failed", e)
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            if (!running) return
            try {
                val faces = result.get(CaptureResult.STATISTICS_FACES)
                if (faces.isNullOrEmpty()) {
                    blinkDetector?.process(false)
                    scheduleFaceLost()
                    return
                }
                cancelFaceLost()
                val face = faces.maxByOrNull { it.bounds.width() * it.bounds.height() } ?: return
                blinkDetector?.process(true)

                val rawNx = 1f - (face.bounds.exactCenterX() / sensorWidth)
                val rawNy = face.bounds.exactCenterY() / sensorHeight
                val (px, py) = mapper?.map(rawNx, rawNy) ?: return
                val smoothX = filterX.add(px)
                val smoothY = filterY.add(py)
                cursorX = smoothX; cursorY = smoothY
                mainHandler.post { listener?.onCursorMoved(smoothX, smoothY) }
            } catch (e: Exception) {
                Log.e(TAG, "captureCallback error", e)
            }
        }
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

    fun stop() {
        running = false
        listener = null
        try { captureSession?.close() } catch (e: Exception) { Log.e(TAG, "close session", e) }
        try { cameraDevice?.close() } catch (e: Exception) { Log.e(TAG, "close camera", e) }
        captureSession = null
        cameraDevice = null
        try { cameraThread?.quitSafely() } catch (e: Exception) { Log.e(TAG, "quit camera thread", e) }
        cameraThread = null
        cameraHandler = null
        mapper = null
        blinkDetector = null
        filterX.reset()
        filterY.reset()
    }

    companion object {
        private const val TAG = "EyeTab"
        val instance: EyeTrackingManager by lazy { EyeTrackingManager() }
    }
}
