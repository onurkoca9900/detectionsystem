package com.example.detectionsystem.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airbnb.lottie.compose.*
import com.example.detectionsystem.DmsViewModel
import com.example.detectionsystem.ModernDMSAppScreen
import com.example.detectionsystem.ui.theme.screens.HomeScreen
import com.example.detectionsystem.ui.theme.screens.LoginScreen
import com.example.detectionsystem.ui.theme.screens.SignUpScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // ORTAK BEYİN: Hem HomeScreen hem ModernDMSAppScreen bu ViewModel'i paylaşacak
    val sharedViewModel: DmsViewModel = hiltViewModel()

    // BAŞLANGIÇ EKRANI: "splash" (Açılış Ekranı)
    NavHost(navController = navController, startDestination = "splash") {

        // 1. AÇILIŞ EKRANI (Lottie Animasyonlu + Firebase Kontrollü)
        composable("splash") {
            SplashScreen(
                onNavigateNext = {
                    // Firebase'de aktif oturum açmış kullanıcı var mı kontrol et
                    val currentUser = FirebaseAuth.getInstance().currentUser

                    val targetDestination = if (currentUser != null) "home" else "login"

                    navController.navigate(targetDestination) {
                        // Splash ekranını geçmişten sil, geri tuşuna basınca tekrar açılmasın
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // 2. GİRİŞ EKRANI
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    // Kayıt Ol yazısına tıklanınca signup rotasına git
                    navController.navigate("signup")
                }
            )
        }

        // 3. KAYIT OL EKRANI (YENİ)
        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate("home") {
                        // Kayıt başarılı olursa login ve signup sayfalarını geçmişten sil
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateBack = {
                    // Sol üstteki geri okuna veya telefonun geri tuşuna basılınca
                    navController.popBackStack()
                }
            )
        }

        composable("home") {
            HomeScreen(
                onNavigateToCamera = { navController.navigate("camera") },
                onLogoutSuccess = {
                    // Çıkış yapıldığında Giriş ekranına dön ve ana ekranı geçmişten sil
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                viewModel = sharedViewModel
            )
        }

        // 4. KAMERA VE TAKİP EKRANI
        composable("camera") {
            ModernDMSAppScreen(
                viewModel = sharedViewModel
            )
        }
    }
}

// --- SPLASH SCREEN (AÇILIŞ EKRANI) TASARIMI ---
@Composable
fun SplashScreen(onNavigateNext: () -> Unit) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Url("https://lottie.host/80ec01d9-8395-4424-bca5-bdfbaebff197/1XoM9D4X7m.json")
    )

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        speed = 1.2f
    )

    // Sayfalar arası çift yönlendirmeyi önlemek için kilit bayrağı
    var hasNavigated by remember { mutableStateOf(false) }

    fun safeNavigate() {
        if (!hasNavigated) {
            hasNavigated = true
            onNavigateNext()
        }
    }

    // Animasyon bittiğinde yönlendir
    LaunchedEffect(progress) {
        if (progress == 1f) {
            safeNavigate()
        }
    }

    // Güvenlik Ağı: 3.5 saniye sonra yönlendir
    LaunchedEffect(Unit) {
        delay(3500)
        safeNavigate()
    }

    // Açılış Ekranı Tasarımı
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(250.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Smart DMS",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Yapay Zeka Hazırlanıyor...",
                color = Color(0xFF00E676),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
