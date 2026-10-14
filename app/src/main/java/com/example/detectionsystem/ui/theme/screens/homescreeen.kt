package com.example.detectionsystem.ui.theme.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.detectionsystem.DmsViewModel
import com.example.detectionsystem.DriveSaveErrorDialog
import com.example.detectionsystem.data.DrivingSession
import com.example.detectionsystem.hardware.BleBraceletState
import com.example.detectionsystem.hardware.BleBraceletStatus
import com.example.detectionsystem.hardware.PermissionChecks
import com.example.detectionsystem.vision.VisionStatus
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.isActive

// ==========================================
// 1. ANA EKRAN BİLEŞENİ
// ==========================================
@Composable
fun HomeScreen(
    onNavigateToCamera: () -> Unit,
    onLogoutSuccess: () -> Unit,
    viewModel: DmsViewModel = hiltViewModel() // Clean Code: Hilt Entegrasyonu
) {
    val context = LocalContext.current
    val homeScope = rememberCoroutineScope()
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val userEmail = currentUser?.email ?: HomeConstants.DEFAULT_USER_NAME

    // İzinler ve İlk Giriş Kontrolleri (Modüler Fonksiyonlar)
    HandleNotificationPermission(context)
    val showOnboardingDialog = HandleOnboardingState(context)

    // UI Durumları
    val riskPercentage = viewModel.riskPercentage
    val drivingTimeText = viewModel.getFormattedDrivingTime()
    val braceletState = viewModel.braceletState
    val isBraceletConnected = braceletState.status == BleBraceletStatus.CONNECTED
    var showBraceletDialog by remember { mutableStateOf(false) }
    var bluetoothPermissionDenied by remember { mutableStateOf(false) }
    val requiredBluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grantResults ->
            bluetoothPermissionDenied = !PermissionChecks.areAllGranted(requiredBluetoothPermissions, grantResults) {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (bluetoothPermissionDenied) viewModel.stopBraceletScan() else viewModel.scanForBracelets()
        }
    )
    val requestBraceletScan = {
        val missingPermissions = requiredBluetoothPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            bluetoothPermissionDenied = false
            viewModel.scanForBracelets()
        } else {
            bluetoothPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    val sessions by viewModel.allSessions.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            HomeTopAppBar(
                onGuideClick = { showOnboardingDialog.value = true },
                onLogoutClick = {
                    viewModel.signOut()
                    onLogoutSuccess()
                }
            )
        },
        containerColor = HomeConstants.BgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = HomeConstants.PaddingMedium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(HomeConstants.SpacingMedium)
        ) {
            Spacer(modifier = Modifier.height(HomeConstants.SpacingSmall))

            WelcomeSection(userEmail, riskPercentage)

            StartDriveButton(enabled = !viewModel.isSavingDrive, onStart = {
                viewModel.startNewDrive {
                    if (homeScope.isActive) onNavigateToCamera()
                }
            })

            RiskRatioCard(riskPercentage = riskPercentage)

            SectionTitle(title = HomeConstants.TitleTelemetry)

            TelemetryGridSection(
                viewModel = viewModel,
                braceletState = braceletState,
                isConnected = isBraceletConnected,
                onBraceletClick = {
                    showBraceletDialog = true
                    if (braceletState.status != BleBraceletStatus.CONNECTED) requestBraceletScan()
                },
                drivingTimeText = drivingTimeText
            )

            AdviceCard(riskPercentage = riskPercentage, isMonitoringAvailable = viewModel.visionStatus == VisionStatus.TRACKING)

            SectionTitle(title = HomeConstants.TitleHistory)

            HistorySection(sessions = sessions)

            Spacer(modifier = Modifier.height(HomeConstants.PaddingLarge))
        }
    }

    if (showBraceletDialog) {
        BraceletConnectionDialog(
            state = braceletState,
            permissionDenied = bluetoothPermissionDenied,
            onScan = requestBraceletScan,
            onConnect = { viewModel.connectToBracelet(it) },
            onDisconnect = viewModel::disconnectBracelet,
            onDismiss = {
                viewModel.stopBraceletScan()
                showBraceletDialog = false
            }
        )
    }
    DriveSaveErrorDialog(viewModel)
}

// ==========================================
// 2. MANTIKSAL ALT BİLEŞENLER (Logic Helpers)
// ==========================================

@Composable
private fun HandleNotificationPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {}
        )
        LaunchedEffect(Unit) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun HandleOnboardingState(context: Context): MutableState<Boolean> {
    val sharedPrefs = remember { context.getSharedPreferences(HomeConstants.PREFS_NAME, Context.MODE_PRIVATE) }
    val isShownBefore = remember { sharedPrefs.getBoolean(HomeConstants.PREF_ONBOARDING, false) }
    val showDialog = remember { mutableStateOf(!isShownBefore) }

    if (showDialog.value) {
        DriverOnboardingDialog(
            onDismiss = {
                showDialog.value = false
                sharedPrefs.edit().putBoolean(HomeConstants.PREF_ONBOARDING, true).apply()
            }
        )
    }
    return showDialog
}

// ==========================================
// 3. ARAYÜZ (UI) ALT BİLEŞENLERİ (SRP)
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(onGuideClick: () -> Unit, onLogoutClick: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(HomeConstants.AppTitle, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                Text(HomeConstants.AppSubtitle, fontSize = 12.sp, color = Color.Gray)
            }
        },
        actions = {
            IconButton(onClick = onGuideClick) { Icon(Icons.Default.Info, contentDescription = "Rehber", tint = Color.LightGray) }
            IconButton(onClick = onLogoutClick) { Icon(Icons.Default.Logout, contentDescription = "Çıkış Yap", tint = HomeConstants.DangerColor) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = HomeConstants.BgColor)
    )
}

@Composable
private fun WelcomeSection(userEmail: String, riskPercentage: Int) {
    val isCritical = riskPercentage >= HomeConstants.CRITICAL_RISK_THRESHOLD

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(HomeConstants.CardColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = HomeConstants.AccentColor, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Hoş Geldiniz,", color = Color.Gray, fontSize = 13.sp)
                Text(text = userEmail, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isCritical) HomeConstants.DangerBgColor else HomeConstants.SafeBgColor,
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isCritical) HomeConstants.DangerColor else HomeConstants.AccentColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCritical) "Kritik Risk" else "Sistem Aktif",
                    color = if (isCritical) HomeConstants.DangerColor else HomeConstants.AccentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StartDriveButton(enabled: Boolean, onStart: () -> Unit) {
    Button(
        onClick = onStart,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(75.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HomeConstants.AccentColor),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = if (enabled) "Sürüş Takibini Başlat" else "Kaydediliyor…", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TelemetryGridSection(
    viewModel: DmsViewModel,
    braceletState: BleBraceletState,
    isConnected: Boolean,
    onBraceletClick: () -> Unit,
    drivingTimeText: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                modifier = Modifier.weight(1f),
                title = "Bileklik Bağlantısı",
                subtitle = if (isConnected) "Bağlı: ${braceletState.connectedName ?: "BLE cihazı"}" else "BLE cihazı seç",
                icon = Icons.Default.CheckCircle,
                containerColor = if (isConnected) HomeConstants.SafeBgColor else HomeConstants.DangerBgColor,
                contentColor = if (isConnected) HomeConstants.AccentColor else HomeConstants.DangerColor,
                onClick = onBraceletClick
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                title = "Canlı Süre",
                subtitle = "$drivingTimeText (Sıfırla)",
                icon = Icons.Default.Info,
                containerColor = HomeConstants.CardColor,
                contentColor = Color.White,
                onClick = { viewModel.resetTimer() }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                modifier = Modifier.weight(1f),
                title = "Sesli Alarm",
                subtitle = if (viewModel.isDanger) "SİREN ÇALIYOR!" else "Test İçin Dokun",
                icon = Icons.Default.Notifications,
                containerColor = HomeConstants.CardColor,
                contentColor = Color.White,
                onClick = { viewModel.testAlarm() }
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                title = "Göz (EAR)",
                subtitle = if (viewModel.visionStatus == VisionStatus.TRACKING)
                    "EAR: ${String.format(Locale.US, "%.2f", viewModel.currentEar)}" else "Sürüşte ölçülür",
                icon = Icons.Default.Visibility,
                containerColor = HomeConstants.CardColor,
                contentColor = Color.White
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

@Composable
private fun HistorySection(sessions: List<DrivingSession>) {
    if (sessions.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = HomeConstants.CardColor)) {
            Text(text = "Henüz kaydedilmiş bir sürüş bulunmuyor.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sessions.take(5).forEach { session ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = HomeConstants.CardColor)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val dateString = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr")).format(Date(session.dateTimestamp))
                        Text(text = dateString, fontWeight = FontWeight.Bold, color = Color.White)

                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(text = "Süre: ${session.durationSeconds / 3600}s ${(session.durationSeconds % 3600) / 60}dk", fontSize = 13.sp, color = Color.LightGray)
                            Text(text = "Esneme: ${session.yawnCount}", color = HomeConstants.WarningColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Uyku: ${session.sleepWarningCount}", color = HomeConstants.DangerColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// YENİDEN KULLANILABİLİR KARTLAR
// ------------------------------------------

@Composable
fun RiskRatioCard(riskPercentage: Int) {
    val isCritical = riskPercentage >= HomeConstants.CRITICAL_RISK_THRESHOLD
    val isWarning = riskPercentage >= HomeConstants.WARNING_RISK_THRESHOLD

    val riskColor = when {
        isCritical -> HomeConstants.DangerColor
        isWarning -> HomeConstants.WarningColor
        else -> HomeConstants.AccentColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCritical) HomeConstants.DangerBgColor else HomeConstants.CardColor)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Yorgunluk Risk Oranı", fontWeight = FontWeight.Bold, color = if (isCritical) HomeConstants.DangerColor else Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { riskPercentage / 100f }, modifier = Modifier.size(100.dp), strokeWidth = 10.dp, color = riskColor, trackColor = Color.DarkGray)
                Text(text = "%$riskPercentage", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (isCritical) HomeConstants.DangerColor else Color.White)
            }
        }
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, title: String, subtitle: String, icon: ImageVector, containerColor: Color, contentColor: Color, onClick: (() -> Unit)? = null) {
    Card(onClick = onClick ?: {}, enabled = onClick != null, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = contentColor)
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = contentColor)
            Text(text = subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun AdviceCard(riskPercentage: Int, isMonitoringAvailable: Boolean) {
    val isCritical = riskPercentage >= HomeConstants.CRITICAL_RISK_THRESHOLD
    val isWarning = riskPercentage >= HomeConstants.WARNING_RISK_THRESHOLD

    val (adviceText, icon, color, bgColor) = when {
        !isMonitoringAvailable -> listOf("Kamera takibi aktif değil. Sürüşü başlatıp yüzünüzün göründüğünü kontrol edin.", Icons.Default.VideocamOff, Color.LightGray, HomeConstants.CardColor)
        isCritical -> listOf("ACİL: Lütfen aracı güvenli bir yerde durdurun ve dinlenin!", Icons.Default.Warning, HomeConstants.DangerColor, HomeConstants.DangerBgColor)
        isWarning -> listOf("Dikkat: Yorgunluk belirtileri artıyor. Mola vermeyi düşünün.", Icons.Default.Info, HomeConstants.WarningColor, HomeConstants.CardColor)
        else -> listOf("Durum İyi: Sürüşe güvenle devam edebilirsiniz.", Icons.Default.CheckCircle, HomeConstants.AccentColor, HomeConstants.SafeBgColor)
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = bgColor as Color)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon as ImageVector, contentDescription = null, tint = color as Color)
            Text(text = adviceText as String, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

// ==========================================
// 4. SABİTLER (Magic Strings / Numbers)
// ==========================================
private object HomeConstants {
    // Renkler (Otomotiv Teması)
    val BgColor = Color(0xFF121212)
    val CardColor = Color(0xFF1E1E1E)
    val AccentColor = Color(0xFF00E676)
    val DangerColor = Color(0xFFFF4C4C)
    val DangerBgColor = Color(0xFF3B0909)
    val SafeBgColor = Color(0xFF1E3A2A)
    val WarningColor = Color(0xFFFFB74D)

    // Metinler
    const val AppTitle = "Smart DMS"
    const val AppSubtitle = "Yapay Zeka Destekli Panel"
    const val DEFAULT_USER_NAME = "Sürücü"
    const val TitleTelemetry = "Donanım & Sürüş Telemetrisi"
    const val TitleHistory = "Geçmiş Sürüşlerim (Son 5 Kayıt)"

    // Sistem Verileri
    const val PREFS_NAME = "DMS_PREFERENCES"
    const val PREF_ONBOARDING = "IS_ONBOARDING_SHOWN_BEFORE"
    const val CRITICAL_RISK_THRESHOLD = 70
    const val WARNING_RISK_THRESHOLD = 40

    // Boyutlar
    val PaddingMedium = 16.dp
    val PaddingLarge = 32.dp
    val SpacingSmall = 8.dp
    val SpacingMedium = 16.dp
}

@Composable
private fun BraceletConnectionDialog(
    state: BleBraceletState,
    permissionDenied: Boolean,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BLE bileklik bağlantısı") },
        text = {
            Column {
                Text(
                    text = when {
                        permissionDenied -> "Bileklik aramak için Bluetooth izinlerini verin."
                        else -> state.message ?: "Yakındaki BLE cihazlarını arayın."
                    }
                )
                if (state.status == BleBraceletStatus.SCANNING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.devices.forEach { device ->
                        TextButton(onClick = { onConnect(device.address) }) {
                            Text(device.name)
                        }
                    }
                }
                if (state.status == BleBraceletStatus.CONNECTED) {
                    TextButton(onClick = onDisconnect) { Text("Bağlantıyı kes") }
                } else {
                    TextButton(onClick = onScan) { Text("Yeniden tara") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kapat") }
        }
    )
}
