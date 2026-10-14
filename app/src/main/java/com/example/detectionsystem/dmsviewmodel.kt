package com.example.detectionsystem

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.detectionsystem.data.DriveSessionMetrics
import com.example.detectionsystem.data.AccountContactStore
import com.example.detectionsystem.data.EmergencyContact
import com.example.detectionsystem.data.DrivingDao
import com.example.detectionsystem.data.DrivingSession
import com.example.detectionsystem.hardware.BleBraceletManager
import com.example.detectionsystem.hardware.BleBraceletState
import com.example.detectionsystem.hardware.SoundAlarmManager
import com.example.detectionsystem.hardware.VoiceAssistantManager
import com.example.detectionsystem.vision.FatigueRiskCalculator
import com.example.detectionsystem.vision.VisionAnalysis
import com.example.detectionsystem.vision.VisionStatus
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DmsViewModel @Inject constructor(
    private val drivingDao: DrivingDao,
    private val auth: FirebaseAuth,
    @ApplicationContext context: Context
) : ViewModel() {

    private val mutableSessions = MutableStateFlow<List<DrivingSession>>(emptyList())
    val allSessions = mutableSessions.asStateFlow()
    private val contactStore = AccountContactStore(context)
    private var activeUserId: String? = null
    private var sessionsJob: Job? = null
    private val authListener = FirebaseAuth.AuthStateListener { updateActiveUser(it.currentUser?.uid) }
    private val braceletManager = BleBraceletManager(context)

    // ==========================================
    // 1. ARAYÜZ (UI) DURUM DEĞİŞKENLERİ
    // ==========================================
    var isTrackingActive by mutableStateOf(false)
        private set
    var isSavingDrive by mutableStateOf(false)
        private set
    var driveSaveErrorMessage by mutableStateOf<String?>(null)
        private set
    var braceletState by mutableStateOf(BleBraceletState())
        private set
    var showBreakSuggestion by mutableStateOf(false)
        private set

    // Acil Durum Yönetimi
    var emergencyPhoneNumber by mutableStateOf("")
        private set
    var emergencyContactName by mutableStateOf("Kayıtlı Kişi Yok")
        private set
    var isSosTriggered by mutableStateOf(false)
        private set
    var shouldRequestSmsPermission by mutableStateOf(false)
        private set
    var sosStatusMessage by mutableStateOf<String?>(null)
        private set
    var canRetrySos by mutableStateOf(false)
        private set

    var visionStatus by mutableStateOf(VisionStatus.FACE_NOT_DETECTED)
        private set
    var isSmsPermissionGranted by mutableStateOf(false)
        private set

    // ==========================================
    // 2. SÜRÜŞ TELEMETRİSİ VE RİSK DURUMLARI
    // ==========================================
    var riskPercentage by mutableIntStateOf(BASE_RISK_PERCENTAGE)
        private set
    var currentEar by mutableFloatStateOf(DEFAULT_EAR)
        private set
    var currentMar by mutableFloatStateOf(0.0f)
        private set
    var isDanger by mutableStateOf(false)
        private set
    var isYawning by mutableStateOf(false)
        private set
    var isDistracted by mutableStateOf(false)
        private set

    // ==========================================
    // 3. ARKA PLAN SAYAÇLARI VE KRONOMETRELER
    // ==========================================
    private var sessionMetrics by mutableStateOf(DriveSessionMetrics())
    val drivingSeconds: Long get() = sessionMetrics.durationSeconds
    val yawnCount: Int get() = sessionMetrics.yawnCount
    val sleepWarningCount: Int get() = sessionMetrics.sleepWarningCount

    private var timerJob: Job? = null
    private var yawnStartTime = 0L
    private var hasCountedCurrentYawn = false
    private var lastDangerTime = 0L
    private var sendSosAfterPermission = false
    private var smoothedRiskPercentage = BASE_RISK_PERCENTAGE.toFloat()
    private var lastRiskUpdateTime = 0L

    init {
        auth.addAuthStateListener(authListener)
        viewModelScope.launch {
            braceletManager.state.collect { braceletState = it }
        }
    }

    // ==========================================
    // FONKSİYONLAR
    // ==========================================

    private fun updateActiveUser(userId: String?) {
        if (userId == activeUserId) return
        stopTracking()
        if (!isSavingDrive) saveCurrentDrive()
        resetDriveSession()
        sessionsJob?.cancel()
        mutableSessions.value = emptyList()
        braceletManager.stopScan()
        braceletManager.disconnect()
        emergencyPhoneNumber = ""
        emergencyContactName = "Kayıtlı Kişi Yok"
        driveSaveErrorMessage = null
        activeUserId = userId

        if (userId != null) {
            val contact = contactStore.load(userId)
            loadEmergencyContact(contact.phone, contact.name)
            sessionsJob = viewModelScope.launch {
                drivingDao.getSessionsForUser(userId).collect { sessions ->
                    if (activeUserId == userId) mutableSessions.value = sessions
                }
            }
        }
    }

    fun signOut() {
        auth.signOut()
        updateActiveUser(null)
    }

    fun scanForBracelets() = braceletManager.startScan()

    fun stopBraceletScan() = braceletManager.stopScan()

    fun connectToBracelet(address: String) = braceletManager.connect(address)

    fun disconnectBracelet() = braceletManager.disconnect()

    fun alertBracelet() = braceletManager.sendFatigueAlert(isDanger)

    fun clearBraceletAlert() = braceletManager.clearFatigueAlert()

    fun getFormattedDrivingTime(): String {
        val hours = drivingSeconds / 3600
        val minutes = (drivingSeconds % 3600) / 60
        return if (hours > 0) {
            "$hours Saat $minutes Dk"
        } else {
            "$minutes Dk ${drivingSeconds % 60} Sn"
        }
    }

    fun saveEmergencyContact(phone: String, name: String) {
        val userId = activeUserId ?: return
        if (auth.currentUser?.uid != userId) return
        val contact = EmergencyContact(phone.trim(), name.trim())
        contactStore.save(userId, contact)
        loadEmergencyContact(contact.phone, contact.name)
    }

    private fun loadEmergencyContact(phone: String, name: String) {
        emergencyPhoneNumber = phone
        emergencyContactName = name.ifBlank { if (phone.isBlank()) "Kayıtlı Kişi Yok" else "Acil Durum Kişisi" }
        if (phone.isNotBlank() && sendSosAfterPermission) {
            if (isSmsPermissionGranted) {
                sendSosAfterPermission = false
                sendEmergencySos()
            } else {
                shouldRequestSmsPermission = true
            }
        }
    }

    fun updateSmsPermission(granted: Boolean) {
        isSmsPermissionGranted = granted
    }

    fun requestSmsPermissionForContactSetup() {
        if (isSmsPermissionGranted) {
            if (sendSosAfterPermission && emergencyPhoneNumber.isNotBlank()) {
                sendSosAfterPermission = false
                sendEmergencySos()
            }
        } else {
            shouldRequestSmsPermission = true
        }
    }

    fun onSmsPermissionResult(granted: Boolean) {
        isSmsPermissionGranted = granted
        shouldRequestSmsPermission = false

        if (granted && sendSosAfterPermission) {
            sendSosAfterPermission = false
            sendEmergencySos()
        } else if (!granted) {
            canRetrySos = sendSosAfterPermission
            sendSosAfterPermission = false
            sosStatusMessage = "SMS izni verilmedi. Otomatik SOS mesajı gönderilemez."
        }
    }

    fun dismissSosStatus() {
        sosStatusMessage = null
    }

    fun retryEmergencySos() {
        sosStatusMessage = null
        canRetrySos = false
        triggerEmergencySos()
    }

    fun retrySmsPermissionRequest() {
        if (!isSmsPermissionGranted) shouldRequestSmsPermission = true
    }

    fun updateVisionAnalysis(result: VisionAnalysis, voiceAssistant: VoiceAssistantManager?) {
        if (!isTrackingActive || auth.currentUser?.uid != activeUserId) return
        visionStatus = result.status
        if (result.status != VisionStatus.TRACKING) {
            currentEar = 0f
            currentMar = 0f
            isDanger = false
            isYawning = false
            isDistracted = false
            yawnStartTime = 0L
            hasCountedCurrentYawn = false
            updateFatigueRisk()
            return
        }

        updateFullTelemetryWithVoice(
            danger = result.isDanger,
            rawYawning = result.isYawning,
            distracted = result.isDistracted,
            sos = result.isSosTriggered,
            ear = result.currentEAR,
            mar = result.currentMAR,
            voiceAssistant = voiceAssistant
        )
    }

    fun pauseVisionTracking() {
        visionStatus = VisionStatus.PAUSED
        currentEar = 0f
        currentMar = 0f
        isDanger = false
        isYawning = false
        isDistracted = false
        yawnStartTime = 0L
        hasCountedCurrentYawn = false
        updateFatigueRisk()
    }

    fun updateFullTelemetryWithVoice(
        danger: Boolean,
        rawYawning: Boolean,
        distracted: Boolean,
        sos: Boolean,
        ear: Float,
        mar: Float,
        voiceAssistant: VoiceAssistantManager?
    ) {
        currentEar = ear
        currentMar = mar
        val currentTime = SystemClock.elapsedRealtime()

        // 1. UYKU DURUMU (Öncelikli Risk)
        if (danger) {
            isDanger = true
            isYawning = false
            yawnStartTime = 0L
            hasCountedCurrentYawn = false

            if (lastDangerTime == 0L || currentTime - lastDangerTime > SLEEP_WARNING_COOLDOWN_MS) {
                sessionMetrics = sessionMetrics.copy(sleepWarningCount = sleepWarningCount + 1)
                lastDangerTime = currentTime
            }
            voiceAssistant?.speak("Dikkat! Uyku algılandı!")
        }
        // 2. ESNEME DURUMU
        else {
            isDanger = false

            if (rawYawning || mar > MAR_THRESHOLD) {
                if (yawnStartTime == 0L) {
                    yawnStartTime = currentTime
                } else {
                    val timeElapsed = currentTime - yawnStartTime

                    // Kesintisiz süre kontrolü
                    if (timeElapsed >= YAWN_DURATION_THRESHOLD_MS) {
                        isYawning = true

                        if (!hasCountedCurrentYawn) {
                            sessionMetrics = sessionMetrics.copy(yawnCount = yawnCount + 1)
                            hasCountedCurrentYawn = true

                            if (yawnCount >= YAWN_COUNT_FOR_BREAK && !showBreakSuggestion) {
                                showBreakSuggestion = true
                                voiceAssistant?.speak("Sık esneme algılandı. Lütfen en yakın dinlenme tesisinde mola verin.")
                            } else if (yawnCount < YAWN_COUNT_FOR_BREAK) {
                                voiceAssistant?.speak("Yorgunluk algılandı.")
                            }
                        }
                    }
                }
            } else {
                isYawning = false
                yawnStartTime = 0L
                hasCountedCurrentYawn = false
            }
        }

        // 3. DİKKAT DAĞINIKLIĞI
        isDistracted = distracted && !danger
        if (isDistracted) {
            voiceAssistant?.speak("Lütfen yola odaklanın.")
        }

        // 4. SOS DURUMU
        if (sos && !isSosTriggered) {
            triggerEmergencySos()
        }

        updateFatigueRisk()
    }

    fun updateFullTelemetry(
        danger: Boolean,
        yawning: Boolean,
        distracted: Boolean,
        sos: Boolean,
        ear: Float,
        mar: Float
    ) {
        updateFullTelemetryWithVoice(danger, yawning, distracted, sos, ear, mar, null)
    }

    fun dismissBreakSuggestion() {
        showBreakSuggestion = false
    }

    fun startTracking() {
        if (isSavingDrive) return
        if (activeUserId == null || auth.currentUser?.uid != activeUserId) return
        if (isTrackingActive) return
        isTrackingActive = true
        timerJob = viewModelScope.launch {
            while (isTrackingActive) {
                delay(1000L)
                sessionMetrics = sessionMetrics.copy(durationSeconds = drivingSeconds + 1L)
                updateFatigueRisk()
            }
        }
    }

    fun stopTracking() {
        isTrackingActive = false
        pauseVisionTracking()
        timerJob?.cancel()
        timerJob = null
    }

    // HİLT SAYESİNDE DAO DIŞARIDAN İSTENMİYOR
    fun finishAndSaveDrive(onSaved: () -> Unit) = saveAndResetDrive(startNew = false, onSaved)

    /** Waits for the previous drive to be archived before opening a new session. */
    fun startNewDrive(onStarted: () -> Unit) = saveAndResetDrive(startNew = true, onStarted)

    private fun saveAndResetDrive(startNew: Boolean, onSaved: () -> Unit) {
        if (isSavingDrive) return
        val userId = activeUserId ?: return
        if (auth.currentUser?.uid != userId) return
        val metrics = sessionMetrics
        isSavingDrive = true
        driveSaveErrorMessage = null
        stopTracking()
        viewModelScope.launch {
            var saved = false
            try {
                if (metrics.hasActivity) drivingDao.insertSession(metrics.toDrivingSession(userId))
                if (activeUserId == userId) {
                    resetDriveSession()
                    saved = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DMS_HISTORY", "Drive could not be saved", e)
                if (activeUserId == userId) driveSaveErrorMessage = "Sürüş kaydedilemedi. Kayıtlarınız korunuyor; yeniden deneyin."
            } finally {
                isSavingDrive = false
            }
            if (saved) {
                if (startNew) startTracking()
                onSaved()
            }
        }
    }

    fun dismissDriveSaveError() {
        driveSaveErrorMessage = null
    }

    private fun saveCurrentDrive() {
        val userId = activeUserId ?: return
        val metrics = sessionMetrics
        if (!metrics.hasActivity) return

        val session = metrics.toDrivingSession(userId)
        viewModelScope.launch {
            try {
                drivingDao.insertSession(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DMS_HISTORY", "Unfinished drive could not be archived", e)
                if (activeUserId == userId) driveSaveErrorMessage = "Önceki sürüş kaydedilemedi."
            }
        }
    }

    private fun DriveSessionMetrics.toDrivingSession(userId: String) = DrivingSession(
        durationSeconds = durationSeconds,
        yawnCount = yawnCount,
        sleepWarningCount = sleepWarningCount,
        userId = userId
    )

    private fun resetDriveSession() {
        sessionMetrics = sessionMetrics.cleared()
        showBreakSuggestion = false
        currentEar = DEFAULT_EAR
        currentMar = 0f
        isDanger = false
        isYawning = false
        isDistracted = false
        yawnStartTime = 0L
        hasCountedCurrentYawn = false
        lastDangerTime = 0L
        isSosTriggered = false
        sendSosAfterPermission = false
        shouldRequestSmsPermission = false
        sosStatusMessage = null
        canRetrySos = false
        visionStatus = VisionStatus.FACE_NOT_DETECTED
        smoothedRiskPercentage = BASE_RISK_PERCENTAGE.toFloat()
        riskPercentage = BASE_RISK_PERCENTAGE
        lastRiskUpdateTime = 0L
    }

    fun triggerEmergencySos() {
        if (isSosTriggered) return
        if (emergencyPhoneNumber.isBlank()) {
            sendSosAfterPermission = true
            canRetrySos = true
            sosStatusMessage = "SOS uyarısı için önce bir acil durum kişisi kaydedin."
            return
        }

        if (!isSmsPermissionGranted) {
            sendSosAfterPermission = true
            canRetrySos = true
            shouldRequestSmsPermission = true
            sosStatusMessage = "Acil durum mesajı için SMS izni gerekiyor."
            return
        }

        sendEmergencySos()
    }

    private fun sendEmergencySos() {
        try {
            val message = "Smart DMS uyarısı: uzun süreli göz kapanması algılandı. Lütfen sürücüyle iletişim kurmaya çalışın."
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(emergencyPhoneNumber, null, message, null, null)
            isSosTriggered = true
            canRetrySos = false
            sosStatusMessage = "SOS mesajı gönderilmek üzere Android'e iletildi. Teslim edilmesi garanti edilmez."
            Log.i("DMS_SOS", "SOS message request submitted")
        } catch (e: SecurityException) {
            isSmsPermissionGranted = false
            sendSosAfterPermission = true
            canRetrySos = true
            shouldRequestSmsPermission = true
            sosStatusMessage = "Acil durum mesajı için SMS izni gerekiyor."
        } catch (e: Exception) {
            isSosTriggered = false
            canRetrySos = true
            sosStatusMessage = "SOS mesajı gönderilemedi. Yeniden deneyebilirsiniz."
            Log.e("DMS_SOS", "SOS message request failed", e)
        }
    }

    fun resetTimer() {
        sessionMetrics = sessionMetrics.copy(durationSeconds = 0L)
        updateFatigueRisk()
    }

    /** Recalculates risk from current driving cues and observed drive history. */
    private fun updateFatigueRisk() {
        val targetRisk = FatigueRiskCalculator.calculateTarget(
            drivingSeconds = drivingSeconds,
            yawnCount = yawnCount,
            sleepWarningCount = sleepWarningCount,
            isDanger = isDanger,
            isYawning = isYawning,
            isDistracted = isDistracted
        ).toFloat()

        val now = SystemClock.elapsedRealtime()
        if (lastRiskUpdateTime == 0L) {
            smoothedRiskPercentage = targetRisk
        } else {
            val elapsed = (now - lastRiskUpdateTime).coerceAtLeast(0L)
            val smoothingFactor = elapsed.toFloat() / (RISK_SMOOTHING_TIME_MS + elapsed)
            smoothedRiskPercentage += (targetRisk - smoothedRiskPercentage) * smoothingFactor
        }
        lastRiskUpdateTime = now
        riskPercentage = smoothedRiskPercentage.toInt().coerceIn(0, 100)
    }

    fun testAlarm() {
        viewModelScope.launch {
            SoundAlarmManager.startAlarm()
            delay(1500L)
            SoundAlarmManager.stopAlarm()
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        braceletManager.close()
        super.onCleared()
    }

    // ==========================================
    // CLEAN CODE: SABİT DEĞERLER (MAGIC NUMBERS)
    // ==========================================
    companion object {
        private const val BASE_RISK_PERCENTAGE = 5
        private const val RISK_SMOOTHING_TIME_MS = 1_500L
        private const val DEFAULT_EAR = 0.30f
        private const val MAR_THRESHOLD = 0.45f
        private const val YAWN_DURATION_THRESHOLD_MS = 2000L
        private const val SLEEP_WARNING_COOLDOWN_MS = 5000L
        private const val YAWN_COUNT_FOR_BREAK = 3
    }
}
