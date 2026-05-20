package com.example.eyetab

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.Face
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Surface
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
    private val cameraThread = HandlerThread("CameraThread").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private val filterX = MovingAverageFilter(SMOOTHING_WINDOW)
    private val filterY = MovingAverageFilter(SMOOTHING_WINDOW)
    private lateinit var mapper: CalibrationMapper
    private lateinit var blinkDetector: BlinkDetector

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

    fun start(context: Context, listener: Listener) {
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
                    val dir = when {
                        cursorY < screenHeight * SCROLL_EDGE_RATIO -> ScrollDirection.UP
                        cursorY > screenHeight * (1f - SCROLL_EDGE_RATIO) -> ScrollDirection.DOWN
                        else -> return@BlinkDetector
                    }
                    mainHandler.post { listener.onScrollRequest(dir, cursorX, cursorY) }
                }
            }
        )

        openFrontCamera(context)
    }

    private fun openFrontCamera(context: Context) {
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
        if (frontCameraId.isEmpty()) return

        camManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startCapture(camera, camManager.getCameraCharacteristics(frontCameraId))
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
            override fun onError(camera: CameraDevice, error: Int) { camera.close(); cameraDevice = null }
        }, cameraHandler)
    }

    private fun startCapture(camera: CameraDevice, chars: CameraCharacteristics) {
        val reader = ImageReader.newInstance(320, 240, ImageFormat.JPEG, 2)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, cameraHandler)

        camera.createCaptureSession(
            listOf(reader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader.surface)
                        set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL)
                    }
                    session.setRepeatingRequest(request.build(), captureCallback, cameraHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            cameraHandler
        )
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val faces = result.get(CaptureResult.STATISTICS_FACES)
            if (faces.isNullOrEmpty()) {
                blinkDetector.process(false)
                scheduleFaceLost()
                return
            }
            cancelFaceLost()
            val face = faces.maxByOrNull { it.bounds.width() * it.bounds.height() } ?: return
            blinkDetector.process(true)

            val bb = face.bounds
            val rawNx = 1f - (bb.exactCenterX() / sensorWidth)
            val rawNy = bb.exactCenterY() / sensorHeight
            val (px, py) = mapper.map(rawNx, rawNy)
            val smoothX = filterX.add(px)
            val smoothY = filterY.add(py)
            cursorX = smoothX
            cursorY = smoothY
            mainHandler.post { listener?.onCursorMoved(smoothX, smoothY) }
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
        listener = null
        captureSession?.close()
        cameraDevice?.close()
        captureSession = null
        cameraDevice = null
    }

    companion object {
        val instance: EyeTrackingManager by lazy { EyeTrackingManager() }
    }
}
