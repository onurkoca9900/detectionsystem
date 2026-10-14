package com.example.detectionsystem
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.detectionsystem.components.BreakSuggestionDialog
import com.example.detectionsystem.data.DrivingSession
import com.example.detectionsystem.hardware.SoundAlarmManager
import com.example.detectionsystem.hardware.VoiceAssistantManager
import com.example.detectionsystem.hardware.WatchNotificationManager
import com.example.detectionsystem.hardware.VibrationManager
import com.example.detectionsystem.navigation.AppNavigation
import com.example.detectionsystem.ui.theme.screens.EmergencyContactDialog
import com.example.detectionsystem.vision.EyeLandmarkAnalyzer
import com.example.detectionsystem.vision.VisionAnalysis
import com.example.detectionsystem.vision.VisionStatus
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.isActive

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    var isInPipMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchNotificationManager.createNotificationChannel(this)
        setContent {
            AppNavigation()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(3, 4))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }
}

// ==========================================
// 2. ANA EKRAN BİLEŞENİ (Smart DMS Screen)
// ==========================================
@Composable
fun ModernDMSAppScreen(
    viewModel: DmsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // UI Durumları (State)
    var showSosDialog by remember { mutableStateOf(false) }
    var showCameraView by remember { mutableStateOf(true) }
    var isLightMode by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }

    val voiceAssistant = remember { VoiceAssistantManager(context) }
    val savedSessions by viewModel.allSessions.collectAsState(initial = emptyList())

    // İzin Yönetimi
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasRequestedCameraPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasCameraPermission = it }
    )
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.onSmsPermissionResult(it) }
    )

    val requestCameraAccess: () -> Unit = {
        val activity = context as? ComponentActivity
        if (hasRequestedCameraPermission && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        } else {
            hasRequestedCameraPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Yaşam Döngüsü (Lifecycle)
    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !hasRequestedCameraPermission) requestCameraAccess()
        viewModel.startTracking()

        viewModel.updateSmsPermission(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    viewModel.updateSmsPermission(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    )
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.pauseVisionTracking()
                    viewModel.clearBraceletAlert()
                    SoundAlarmManager.stopAlarm()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel.shouldRequestSmsPermission) {
        if (viewModel.shouldRequestSmsPermission) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    LaunchedEffect(showCameraView, hasCameraPermission) {
        if (!showCameraView || !hasCameraPermission) viewModel.pauseVisionTracking()
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceAssistant.stop()
            viewModel.clearBraceletAlert()
            viewModel.stopTracking()
            SoundAlarmManager.stopAlarm()
        }
    }

    // Alarm ve Titreşim Tetikleyicileri
    LaunchedEffect(viewModel.isDanger, viewModel.isYawning) {
        if (viewModel.isDanger || viewModel.isYawning) {
            VibrationManager.triggerVibration(context)
            SoundAlarmManager.startAlarm()
            WatchNotificationManager.sendWatchAlert(
                context,
                if (viewModel.isDanger) "Smart DMS: Uyku uyarısı" else "Smart DMS: Yorgunluk uyarısı",
                if (viewModel.isDanger) "Uzun süreli göz kapanması algılandı. Güvenli bir yerde durun."
                    else "Esneme algılandı. Mola vermeyi değerlendirin."
            )
        } else {
            SoundAlarmManager.stopAlarm()
        }
    }

    LaunchedEffect(viewModel.isDanger, viewModel.isYawning, viewModel.braceletState.supportsDirectAlerts) {
        if (viewModel.isDanger || viewModel.isYawning) viewModel.alertBracelet()
        else viewModel.clearBraceletAlert()
    }

    // Temel Renk ve Animasyonlar
    val activeColors = getAppColors(isLightMode)
    val backgroundColor by animateColorAsState(
        targetValue = when {
            viewModel.isDanger -> UIConstants.ColorDangerBg
            viewModel.isYawning -> UIConstants.ColorWarningBg
            else -> activeColors.background
        },
        animationSpec = tween(durationMillis = 400),
        label = "BgColorAnimation"
    )

    // ==========================================
    // RESPONSIVE ANA TASARIM (LocalConfiguration)
    // ==========================================

    // HATA VEREN KISIM BURADA DÜZELTİLDİ:
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > 600

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (isLandscape) {
            // YATAY EKRAN DÜZENİ (Tablet / Araç İçi)
            Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Sol Taraf: Kamera
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CameraOrRadarSection(
                        showCameraView = showCameraView,
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraAccess = requestCameraAccess,
                        viewModel = viewModel,
                        voiceAssistant = voiceAssistant,
                        isLightMode = isLightMode,
                    )
                }
                // Sağ Taraf: Kontroller
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    TopBarSection(viewModel, isLightMode, activeColors, onHistoryClick = { showHistoryScreen = true }, onCameraToggle = { showCameraView = !showCameraView }, onThemeToggle = { isLightMode = !isLightMode }, onSosContactClick = { showSosDialog = true })
                    if (viewModel.showBreakSuggestion) BreakSuggestionCard(viewModel, context)
                    TelemetryCard(viewModel.currentEar, activeColors)
                    SaveDriveButton(viewModel, context)
                }
            }
        } else {
            // DİKEY EKRAN DÜZENİ (Telefon)
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))
                TopBarSection(viewModel, isLightMode, activeColors, onHistoryClick = { showHistoryScreen = true }, onCameraToggle = { showCameraView = !showCameraView }, onThemeToggle = { isLightMode = !isLightMode }, onSosContactClick = { showSosDialog = true })
                Spacer(modifier = Modifier.height(16.dp))

                if (viewModel.showBreakSuggestion) {
                    BreakSuggestionCard(viewModel, context)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CameraOrRadarSection(
                        showCameraView = showCameraView,
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraAccess = requestCameraAccess,
                        viewModel = viewModel,
                        voiceAssistant = voiceAssistant,
                        isLightMode = isLightMode,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                TelemetryCard(viewModel.currentEar, activeColors)
                Spacer(modifier = Modifier.height(12.dp))
                SaveDriveButton(viewModel, context)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Arayüz Üstü Bileşenler (Dialoglar)
        DriveSaveErrorDialog(viewModel)
        BreakSuggestionDialog(viewModel = viewModel)

        if (showSosDialog) {
            EmergencyContactDialog(
                initialName = viewModel.emergencyContactName,
                initialPhone = viewModel.emergencyPhoneNumber,
                onDismiss = { showSosDialog = false },
                onSave = { name, phone ->
                    viewModel.saveEmergencyContact(phone, name)
                    showSosDialog = false
                    if (phone.isNotBlank()) viewModel.requestSmsPermissionForContactSetup()
                }
            )
        }

        viewModel.sosStatusMessage?.let { message ->
            val needsContact = viewModel.emergencyPhoneNumber.isBlank()
            val needsSmsPermission = !viewModel.isSmsPermissionGranted
            AlertDialog(
                onDismissRequest = viewModel::dismissSosStatus,
                title = { Text("SOS durumu") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissSosStatus()
                        when {
                            needsContact -> showSosDialog = true
                            viewModel.canRetrySos -> viewModel.retryEmergencySos()
                            needsSmsPermission -> viewModel.retrySmsPermissionRequest()
                        }
                    }) {
                        Text(
                            when {
                                needsContact -> "Kişi ekle"
                                viewModel.canRetrySos -> "Yeniden dene"
                                needsSmsPermission -> "SMS iznini ver"
                                else -> "Tamam"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissSosStatus) { Text("Kapat") }
                }
            )
        }

        if (showHistoryScreen) {
            HistoryScreenOverlay(
                sessions = savedSessions,
                activeColors = activeColors,
                onClose = { showHistoryScreen = false }
            )
        }
    }
}
// ==========================================
// 3. ALT BİLEŞENLER (MODÜLER - SRP)
// ==========================================

@Composable
private fun TopBarSection(
    viewModel: DmsViewModel,
    isLightMode: Boolean,
    colors: AppColors,
    onHistoryClick: () -> Unit,
    onCameraToggle: () -> Unit,
    onThemeToggle: () -> Unit,
    onSosContactClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = UIConstants.APP_TITLE, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = "Aktif Sürüş: ${viewModel.getFormattedDrivingTime()}", color = UIConstants.ColorSuccess, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHistoryClick) { Icon(Icons.Default.DateRange, contentDescription = "Geçmiş", tint = colors.icon) }
            IconButton(onClick = onCameraToggle) { Icon(Icons.Default.Visibility, contentDescription = "Kamera", tint = colors.icon) }
            IconButton(onClick = onSosContactClick) { Icon(Icons.Default.Call, contentDescription = "SOS kişisi", tint = colors.icon) }
            IconButton(onClick = onThemeToggle) { Icon(if (isLightMode) Icons.Default.Brightness4 else Icons.Default.Brightness7, contentDescription = "Tema", tint = colors.icon) }
        }
    }
}

@Composable
private fun BreakSuggestionCard(viewModel: DmsViewModel, context: Context) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = UIConstants.ColorCardWarningBg)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "☕ Akıllı Mola Önerisi", color = UIConstants.ColorWarningText, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(text = "Kısa süre içinde çok esnediniz. Mola verin.", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { viewModel.dismissBreakSuggestion() }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                    Text("Kapat")
                }
                Button(onClick = {
                    viewModel.dismissBreakSuggestion()
                    IntentUtils.openMapsForRestStops(context)
                }, colors = ButtonDefaults.buttonColors(containerColor = UIConstants.ColorWarningText)) {
                    Icon(Icons.Default.Place, contentDescription = "Harita", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tesis Bul")
                }
            }
        }
    }
}

@Composable
private fun CameraOrRadarSection(
    showCameraView: Boolean,
    hasCameraPermission: Boolean,
    onRequestCameraAccess: () -> Unit,
    viewModel: DmsViewModel,
    voiceAssistant: VoiceAssistantManager,
    isLightMode: Boolean
) {
    if (showCameraView) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            if (hasCameraPermission) {
                CameraPreviewScreen { analysis ->
                    viewModel.updateVisionAnalysis(analysis, voiceAssistant)
                }
                val statusText = when (viewModel.visionStatus) {
                    VisionStatus.TRACKING -> null
                    VisionStatus.FACE_NOT_DETECTED -> "Yüz görünmüyor. Takip duraklatıldı."
                    VisionStatus.MODEL_UNAVAILABLE -> "Görüntü işleme modeli yüklenemedi."
                    VisionStatus.ERROR -> "Görüntü analizi başarısız. Kamerayı kontrol edin."
                    VisionStatus.PAUSED -> "Görüntü takibi duraklatıldı."
                }
                statusText?.let { message ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        color = UIConstants.ColorDangerBg.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(message, color = Color.White, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
                    }
                }
            } else {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sürücü takibi için kamera izni gerekiyor.", color = Color.White, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRequestCameraAccess) { Text("Kamera erişimini aç") }
                }
            }
        }
    } else {
        val radarColor = when {
            viewModel.isDanger -> UIConstants.ColorDangerBg
            viewModel.isYawning || viewModel.isDistracted -> UIConstants.ColorWarningText
            else -> Color.Gray
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = radarColor.copy(alpha = if (isLightMode) 0.1f else 0.2f), modifier = Modifier.size(220.dp)) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(imageVector = Icons.Default.Face, contentDescription = "Radar", tint = radarColor, modifier = Modifier.size(80.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Kamera önizlemesi gizli\nGörüntü takibi duraklatıldı", fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(earValue: Float, colors: AppColors) {
    Surface(shape = RoundedCornerShape(16.dp), color = colors.card, shadowElevation = colors.elevation, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Göz Açıklık Oranı (EAR):", color = colors.subText, fontSize = 14.sp)
            Text(
                text = String.format(Locale.US, "%.3f", earValue),
                color = if (earValue < UIConstants.EAR_CRITICAL_THRESHOLD) UIConstants.ColorDangerBg else UIConstants.ColorSuccess,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun SaveDriveButton(viewModel: DmsViewModel, context: Context) {
    val buttonScope = rememberCoroutineScope()
    Button(
        onClick = {
            viewModel.finishAndSaveDrive {
                if (buttonScope.isActive) {
                    Toast.makeText(context, "Sürüş başarıyla kaydedildi!", Toast.LENGTH_SHORT).show()
                    (context as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                }
            }
        },
        enabled = !viewModel.isSavingDrive,
        modifier = Modifier.fillMaxWidth().height(55.dp),
        colors = ButtonDefaults.buttonColors(containerColor = UIConstants.ColorPrimaryAction),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = if (viewModel.isSavingDrive) "Kaydediliyor…" else "Sürüşü Bitir ve Kaydet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DriveSaveErrorDialog(viewModel: DmsViewModel) {
    viewModel.driveSaveErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDriveSaveError,
            title = { Text("Sürüş kaydı") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDriveSaveError) { Text("Tamam") }
            }
        )
    }
}

@Composable
fun HistoryScreenOverlay(sessions: List<DrivingSession>, activeColors: AppColors, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(activeColors.background).clickable { /* Clicks blocked */ }) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Geçmiş Sürüşlerim", color = activeColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Kapat", tint = activeColors.text) }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Henüz kaydedilmiş bir sürüş bulunmuyor.", color = activeColors.subText)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        val formattedDate = remember(session.dateTimestamp) {
                            SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
                                .format(Date(session.dateTimestamp))
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = activeColors.card)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(formattedDate, color = activeColors.text, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Süre: ${formatDuration(session.durationSeconds)}", color = activeColors.subText)
                                Text("Esneme: ${session.yawnCount} · Uyku uyarısı: ${session.sleepWarningCount}", color = activeColors.subText)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "${hours} sa ${minutes} dk" else "${minutes} dk ${seconds} sn"
}

@Composable
fun CameraPreviewScreen(onAnalysisUpdate: (VisionAnalysis) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val latestOnAnalysisUpdate by rememberUpdatedState(onAnalysisUpdate)
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    DisposableEffect(lifecycleOwner, cameraProviderFuture, analysisExecutor, previewView) {
        val disposed = AtomicBoolean(false)
        var boundCameraProvider: ProcessCameraProvider? = null
        var eyeAnalyzer: EyeLandmarkAnalyzer? = null

        cameraProviderFuture.addListener({
            if (disposed.get()) return@addListener

            try {
                val cameraProvider = cameraProviderFuture.get()
                boundCameraProvider = cameraProvider

                val analyzer = EyeLandmarkAnalyzer(context) { result ->
                    mainExecutor.execute {
                        if (!disposed.get() && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            latestOnAnalysisUpdate(result)
                        }
                    }
                }
                eyeAnalyzer = analyzer

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(UIConstants.LOG_TAG_CAMERA, "Could not start camera analysis", exc)
                latestOnAnalysisUpdate(VisionAnalysis(VisionStatus.ERROR))
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed.set(true)
            boundCameraProvider?.unbindAll()
            val analyzerToClose = eyeAnalyzer
            analysisExecutor.execute { analyzerToClose?.close() }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

// ==========================================
// 4. CLEAN CODE UTILS & CONSTANTS
// ==========================================

object IntentUtils {
    fun openMapsForRestStops(context: Context) {
        val gmmIntentUri = Uri.parse("geo:0,0?q=dinlenme+tesisi+benzin+istasyonu")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
        try {
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/dinlenme+tesisi"))
            context.startActivity(browserIntent)
        }
    }
}

object UIConstants {
    const val APP_TITLE = "Smart DMS"
    const val LOG_TAG_CAMERA = "CameraX"

    const val EAR_CRITICAL_THRESHOLD = 0.21f

    val ColorDangerBg = Color(0xFF3B0909)
    val ColorWarningBg = Color(0xFF332B00)
    val ColorWarningText = Color(0xFFFF9800)
    val ColorCardWarningBg = Color(0xFF2E2000)
    val ColorSuccess = Color(0xFF4CAF50)
    val ColorPrimaryAction = Color(0xFFE91E63)
}

data class AppColors(val background: Color, val card: Color, val text: Color, val subText: Color, val icon: Color, val elevation: androidx.compose.ui.unit.Dp)

fun getAppColors(isLightMode: Boolean): AppColors {
    return if (isLightMode) {
        AppColors(background = Color(0xFFF5F5F5), card = Color.White, text = Color.Black, subText = Color.DarkGray, icon = Color.Black, elevation = 4.dp)
    } else {
        AppColors(background = Color(0xFF121212), card = Color(0xFF1A1A1A), text = Color.White, subText = Color.LightGray, icon = Color.White, elevation = 0.dp)
    }
}
