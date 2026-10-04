package com.example.detectionsystem.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

// ==========================================
// 1. TELEFON TİTREŞİM YÖNETİCİSİ
// ==========================================
object VibrationManager {

    fun triggerVibration(context: Context) {
        try {
            val vibrator = getVibrator(context)

            // Cihazın fiziksel bir titreşim motoru yoksa işlemi güvenli bir şekilde iptal et
            if (vibrator == null || !vibrator.hasVibrator()) {
                Log.w(VibrationConstants.LOG_TAG, "Cihazda titreşim motoru bulunmuyor, işlem atlandı.")
                return
            }

            executeVibration(vibrator)
            Log.i(VibrationConstants.LOG_TAG, "Telefon titreşimi tetiklendi (${VibrationConstants.DURATION_MS} ms).")

        } catch (e: Exception) {
            Log.e(VibrationConstants.LOG_TAG, "Titreşim tetiklenirken donanım hatası oluştu: ${e.message}")
        }
    }

    // Sorumluluk 1: Android sürümüne göre doğru servisi bulmak (SRP)
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Sorumluluk 2: Titreşim komutunu çalıştırmak (SRP)
    private fun executeVibration(vibrator: Vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                VibrationConstants.DURATION_MS,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationConstants.DURATION_MS)
        }
    }
}

// ==========================================
// 2. SABİTLER (Magic Numbers Temizliği)
// ==========================================
object VibrationConstants {
    const val LOG_TAG = "VibrationManager"
    const val DURATION_MS = 1000L // 1 saniye (Titreşim süresi)
}