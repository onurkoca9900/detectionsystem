package com.example.detectionsystem.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class VisionStatus {
    TRACKING,
    FACE_NOT_DETECTED,
    MODEL_UNAVAILABLE,
    ERROR,
    PAUSED
}

data class VisionAnalysis(
    val status: VisionStatus,
    val isDanger: Boolean = false,
    val isYawning: Boolean = false,
    val isDistracted: Boolean = false,
    val isSosTriggered: Boolean = false,
    val currentEAR: Float = 0f,
    val currentMAR: Float = 0f
)

class EyeLandmarkAnalyzer(
    context: Context,
    private val onAnalysisResult: (VisionAnalysis) -> Unit
) : ImageAnalysis.Analyzer {

    private var faceLandmarker: FaceLandmarker? = null

    private val eyeClosureTracker = EyeClosureTracker()
    private var dangerDurationStartTime: Long? = null
    private var isSosAlreadyTriggered = false

    init {
        setupFaceLandmarker(context.applicationContext)
    }

    private fun setupFaceLandmarker(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(VisionConstants.MODEL_ASSET_PATH)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(VisionConstants.LOG_TAG, "FaceLandmarker could not be initialized", e)
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val landmarker = faceLandmarker
            if (landmarker == null) {
                resetDetectionTimers()
                onAnalysisResult(VisionAnalysis(VisionStatus.MODEL_UNAVAILABLE))
                return
            }

            val sourceBitmap = imageProxy.toBitmap()
            val inputBitmap = rotateBitmap(sourceBitmap, imageProxy.imageInfo.rotationDegrees)
            try {
                val result = landmarker.detect(BitmapImageBuilder(inputBitmap).build())
                processLandmarks(result)
            } finally {
                if (inputBitmap !== sourceBitmap) inputBitmap.recycle()
                sourceBitmap.recycle()
            }
        } catch (e: Exception) {
            resetDetectionTimers()
            Log.e(VisionConstants.LOG_TAG, "Frame analysis failed", e)
            onAnalysisResult(VisionAnalysis(VisionStatus.ERROR))
        } finally {
            imageProxy.close()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun processLandmarks(result: FaceLandmarkerResult) {
        val faces = result.faceLandmarks()
        if (faces.isEmpty()) {
            resetDetectionTimers()
            onAnalysisResult(VisionAnalysis(VisionStatus.FACE_NOT_DETECTED))
            return
        }

        val landmarks = faces[0]
        val currentTime = SystemClock.elapsedRealtime()
        val avgEAR = LandmarkMathUtils.calculateAverageEAR(landmarks)
        val currentMAR = LandmarkMathUtils.calculateMAR(landmarks)
        val isDistracted = LandmarkMathUtils.calculateHeadDistraction(landmarks)
        val isYawning = currentMAR > VisionConstants.MAR_THRESHOLD
        val isDanger = checkEyeClosureDanger(avgEAR, currentTime)
        val isSosTriggered = checkSosTrigger(isDanger, currentTime)

        onAnalysisResult(
            VisionAnalysis(
                status = VisionStatus.TRACKING,
                isDanger = isDanger,
                isYawning = isYawning,
                isDistracted = isDistracted,
                isSosTriggered = isSosTriggered,
                currentEAR = avgEAR,
                currentMAR = currentMAR
            )
        )
    }

    private fun resetDetectionTimers() {
        eyeClosureTracker.reset()
        dangerDurationStartTime = null
        isSosAlreadyTriggered = false
    }

    private fun checkEyeClosureDanger(avgEAR: Float, currentTime: Long): Boolean {
        return eyeClosureTracker.update(avgEAR, currentTime)
    }

    private fun checkSosTrigger(isDanger: Boolean, currentTime: Long): Boolean {
        if (isDanger) {
            if (dangerDurationStartTime == null) dangerDurationStartTime = currentTime
            val dangerElapsed = currentTime - (dangerDurationStartTime ?: currentTime)
            if (dangerElapsed >= VisionConstants.SOS_TIME_LIMIT_MS && !isSosAlreadyTriggered) {
                isSosAlreadyTriggered = true
                return true
            }
        } else {
            dangerDurationStartTime = null
            isSosAlreadyTriggered = false
        }
        return false
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }
}

object LandmarkMathUtils {

    fun calculateAverageEAR(landmarks: List<NormalizedLandmark>): Float {
        val leftEAR = calculateEAR(VisionConstants.LEFT_EYE_INDICES, landmarks)
        val rightEAR = calculateEAR(VisionConstants.RIGHT_EYE_INDICES, landmarks)
        return (leftEAR + rightEAR) / 2.0f
    }

    private fun calculateEAR(eyeIndices: List<Int>, landmarks: List<NormalizedLandmark>): Float {
        val points = eyeIndices.map { landmarks[it].let { point -> LandmarkPoint(point.x(), point.y()) } }
        return EyeAspectRatioCalculator.calculate(points)
    }

    fun calculateMAR(landmarks: List<NormalizedLandmark>): Float {
        val upperLip = landmarks[VisionConstants.UPPER_LIP_INDEX]
        val lowerLip = landmarks[VisionConstants.LOWER_LIP_INDEX]
        val leftCorner = landmarks[VisionConstants.LEFT_LIP_CORNER_INDEX]
        val rightCorner = landmarks[VisionConstants.RIGHT_LIP_CORNER_INDEX]

        val verticalDist = distance(upperLip.x(), upperLip.y(), lowerLip.x(), lowerLip.y())
        val horizontalDist = distance(leftCorner.x(), leftCorner.y(), rightCorner.x(), rightCorner.y())
        return verticalDist / (horizontalDist + 0.0001f)
    }

    fun calculateHeadDistraction(landmarks: List<NormalizedLandmark>): Boolean {
        val nose = landmarks[VisionConstants.NOSE_TIP_INDEX]
        val leftCheek = landmarks[VisionConstants.LEFT_CHEEK_INDEX]
        val rightCheek = landmarks[VisionConstants.RIGHT_CHEEK_INDEX]

        val distToLeft = abs(nose.x() - leftCheek.x())
        val distToRight = abs(nose.x() - rightCheek.x())
        val yawRatio = distToLeft / (distToRight + 0.0001f)

        return VisionThresholds.isHeadDistracted(yawRatio)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
}

object VisionConstants {
    const val LOG_TAG = "EyeAnalyzer"
    const val MODEL_ASSET_PATH = "face_landmarker.task"

    const val MAR_THRESHOLD = 0.40f

    const val SOS_TIME_LIMIT_MS = 8000L

    val LEFT_EYE_INDICES = listOf(362, 385, 387, 263, 373, 380)
    val RIGHT_EYE_INDICES = listOf(33, 160, 158, 133, 153, 144)

    const val UPPER_LIP_INDEX = 13
    const val LOWER_LIP_INDEX = 14
    const val LEFT_LIP_CORNER_INDEX = 78
    const val RIGHT_LIP_CORNER_INDEX = 308

    const val NOSE_TIP_INDEX = 1
    const val LEFT_CHEEK_INDEX = 234
    const val RIGHT_CHEEK_INDEX = 454
}
