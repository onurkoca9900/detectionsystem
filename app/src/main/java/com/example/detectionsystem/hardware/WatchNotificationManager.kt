package com.example.detectionsystem.hardware

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

// ==========================================
// 1. AKILLI SAAT BİLDİRİM YÖNETİCİSİ
// ==========================================
object WatchNotificationManager {
    private var lastAlertTime = 0L
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    WatchAlertConstants.CHANNEL_ID,
                    WatchAlertConstants.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = WatchAlertConstants.CHANNEL_DESC
                    enableVibration(true)
                    vibrationPattern = WatchAlertConstants.VIBRATION_PATTERN
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)

                Log.i(WatchAlertConstants.LOG_TAG, "Saat bildirim kanalı başarıyla oluşturuldu.")
            } catch (e: Exception) {
                Log.e(WatchAlertConstants.LOG_TAG, "Bildirim kanalı oluşturulurken hata: ${e.message}")
            }
        }
    }

    @Synchronized
    fun sendWatchAlert(context: Context, title: String, message: String) {
        val currentTime = System.currentTimeMillis()

        // Soğuma Süresi (Cooldown) Kontrolü
        if (currentTime - lastAlertTime < WatchAlertConstants.COOLDOWN_MS) {
            return // Süre dolmadıysa sessizce işlemi iptal et
        }

        lastAlertTime = currentTime

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val uniqueNotificationId = generateUniqueId()

            val builder = NotificationCompat.Builder(context, WatchAlertConstants.CHANNEL_ID)
                .setSmallIcon(R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOnlyAlertOnce(false) // Her seferinde ses/titreşim çalmasını zorla
                .setAutoCancel(true)
                .setVibrate(WatchAlertConstants.VIBRATION_PATTERN)

            notificationManager.notify(uniqueNotificationId, builder.build())
            Log.i(WatchAlertConstants.LOG_TAG, "Saat titreşim uyarısı gönderildi (ID: $uniqueNotificationId)")

        } catch (e: Exception) {
            Log.e(WatchAlertConstants.LOG_TAG, "Saat uyarısı gönderilemedi: ${e.message}")
        }
    }

    // Xiaomi ve diğer saatlerin aynı bildirimi ezmesini önlemek için benzersiz ID üretici (SRP)
    private fun generateUniqueId(): Int {
        // Int.MAX_VALUE kullanarak ID'nin eksi değere düşmesini engelliyoruz
        return (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    }
}

// ==========================================
// 2. SABİTLER (Magic Strings / Numbers Temizliği)
// ==========================================
object WatchAlertConstants {
    const val LOG_TAG = "WatchManager"

    // Kanal Ayarları
    const val CHANNEL_ID = "dms_watch_alert_channel_v2"
    const val CHANNEL_NAME = "Sürücü Acil Durum Uyarısı"
    const val CHANNEL_DESC = "Akıllı saat titreşimi için acil durum bildirimleri"

    // Donanım & Zamanlama
    const val COOLDOWN_MS = 4000L // 4 saniyede bir tetiklenme sınırı
    val VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500)
}