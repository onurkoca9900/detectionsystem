package com.example.detectionsystem.hardware

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

// ==========================================
// 1. ALARM YÖNETİCİSİ
// ==========================================
object SoundAlarmManager {

    private var toneGenerator: ToneGenerator? = null
    private var isPlaying = false

    // @Synchronized: Farklı işlemcilerden aynı anda çağrılırsa çökmesini engeller (Thread Safety)
    @Synchronized
    fun startAlarm() {
        if (isPlaying) return

        try {
            // ALARM kanalından belirlenen ses seviyesiyle bir siren oluşturuyoruz
            toneGenerator = ToneGenerator(AlarmConstants.STREAM_TYPE, AlarmConstants.MAX_VOLUME)

            // Keskin ve uyarıcı bir bip sesi başlat
            toneGenerator?.startTone(AlarmConstants.TONE_TYPE)
            isPlaying = true

            Log.i(AlarmConstants.LOG_TAG, "Tehlike alarmı başlatıldı.")
        } catch (e: Exception) {
            Log.e(AlarmConstants.LOG_TAG, "Alarm başlatılırken donanım hatası oluştu", e)
        }
    }

    @Synchronized
    fun stopAlarm() {
        if (!isPlaying) return

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
            isPlaying = false

            Log.i(AlarmConstants.LOG_TAG, "Alarm durduruldu ve kaynaklar serbest bırakıldı.")
        } catch (e: Exception) {
            Log.e(AlarmConstants.LOG_TAG, "Alarm durdurulurken hata oluştu", e)
        }
    }
}

// ==========================================
// 2. SABİTLER (Magic Numbers Temizliği)
// ==========================================
object AlarmConstants {
    const val LOG_TAG = "SoundAlarmManager"

    // Donanım Ses Ayarları
    const val STREAM_TYPE = AudioManager.STREAM_ALARM
    const val MAX_VOLUME = 100
    const val TONE_TYPE = ToneGenerator.TONE_SUP_ERROR
}