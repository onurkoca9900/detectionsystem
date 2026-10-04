package com.example.detectionsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// Accompanist kütüphanesinden gerekli sınıflar:
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen() {
    // 1. İzin Durumunu Hatırla (Hangi izni istiyoruz?)
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    // 2. İzin Durumuna Göre Ekranı Çiz
    if (cameraPermissionState.status.isGranted) {
        // İZİN VERİLDİYSE: Daha önce tasarladığımız ana arayüzü çağır
        ModernDMSAppScreen()
    } else {
        // İZİN YOKSA VEYA REDDEDİLDİYSE: Kullanıcıyı ikna etme ekranı çiz
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                // Kullanıcı daha önce reddettiyse, neden gerekli olduğunu açıkla
                "Yorgunluk tespiti yapabilmemiz için kamerayı yüzünüze odaklamamız gerekiyor. Lütfen kamera erişimine izin verin."
            } else {
                // Uygulama ilk kez açıldığında gösterilecek standart mesaj
                "Sürüş güvenliğiniz için yüzünüzü analiz etmemiz gerekiyor. Uygulama kameraya ihtiyaç duyar."
            }

            Text(text = textToShow, modifier = Modifier.padding(16.dp))
            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                // BUTONA BASILINCA: Android'in standart izin popup'ını fırlat
                cameraPermissionState.launchPermissionRequest()
            }) {
                Text("Kamera İznini Ver")
            }
        }
    }
}