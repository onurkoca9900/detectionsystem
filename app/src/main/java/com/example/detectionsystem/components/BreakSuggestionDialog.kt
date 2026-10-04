package com.example.detectionsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.detectionsystem.DmsViewModel
import com.example.detectionsystem.IntentUtils

@Composable
fun BreakSuggestionDialog(
    viewModel: DmsViewModel
) {
    // Eğer ViewModel'deki mola önerisi aktifse bu pencereyi göster
    if (viewModel.showBreakSuggestion) {
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = {
                // Dışarı tıklanınca kapanmasın, sürücü bir eylem seçmek zorunda olsun
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Uyarı",
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = "Yorgunluk Belirtisi!")
            },
            text = {
                Text(text = "Sistemimiz son birkaç dakika içinde sık sık esnediğinizi tespit etti. Güvenliğiniz için lütfen en kısa sürede mola verin.")
            },
            // Sağ alt buton (Haritayı Aç)
            confirmButton = {
                Button(
                    onClick = {
                        // 1. Uyarıyı kapat
                        viewModel.dismissBreakSuggestion()
                        // 2. Google Haritalar'ı dinlenme tesisi aramasıyla aç
                        IntentUtils.openMapsForRestStops(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Harita",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tesis Bul")
                }
            },
            // Sol alt buton (Geç)
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissBreakSuggestion()
                    }
                ) {
                    Text("Şimdilik Geç")
                }
            }
        )
    }
}
