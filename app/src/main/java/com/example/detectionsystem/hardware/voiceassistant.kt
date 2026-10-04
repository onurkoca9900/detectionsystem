package com.example.detectionsystem.hardware

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

// ==========================================
// 1. SESLİ ASİSTAN YÖNETİCİSİ
// ==========================================
class VoiceAssistantManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpeakTime = 0L

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            configureLanguage()
        } else {
            Log.e(VoiceConstants.LOG_TAG, "TextToSpeech motoru başlatılamadı. Hata Kodu: $status")
        }
    }

    // Dil yapılandırmasını ayrı bir fonksiyona çıkararak (SRP) okunabilirliği artırdık
    private fun configureLanguage() {
        val localeTR = Locale(VoiceConstants.LANG_TR, VoiceConstants.COUNTRY_TR)
        val result = tts?.setLanguage(localeTR)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Türkçe yoksa varsayılan olarak İngilizce/Amerikan aksanına geç
            tts?.setLanguage(Locale.US)
            Log.w(VoiceConstants.LOG_TAG, "Türkçe ses paketi bulunamadı, varsayılan (EN-US) dil kullanılıyor.")
        } else {
            tts?.setSpeechRate(VoiceConstants.SPEECH_RATE)
        }

        isReady = true
    }

    // @Synchronized: Yapay zeka çekirdeği ile arayüz çekirdeğinin aynı anda ses motorunu çökertmesini engeller
    @Synchronized
    fun speak(message: String) {
        val currentTime = System.currentTimeMillis()

        if (isReady && (currentTime - lastSpeakTime) > VoiceConstants.SPEAK_COOLDOWN_MS) {
            lastSpeakTime = currentTime

            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                VoiceConstants.UTTERANCE_ID
            )

            Log.i(VoiceConstants.LOG_TAG, "Sesli Uyarı Verildi: $message")
        }
    }

    @Synchronized
    fun stop() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
            Log.i(VoiceConstants.LOG_TAG, "Sesli asistan güvenli bir şekilde kapatıldı.")
        } catch (e: Exception) {
            Log.e(VoiceConstants.LOG_TAG, "Asistan kapatılırken hata oluştu", e)
        }
    }
}

// ==========================================
// 2. SABİTLER VE AYARLAR (Clean Code)
// ==========================================
object VoiceConstants {
    const val LOG_TAG = "VoiceAssistant"

    // Zamanlama
    const val SPEAK_COOLDOWN_MS = 6000L // Aynı uyarıyı 6 saniyede bir defadan sık söylemesin

    // Dil ve Konuşma Ayarları
    const val LANG_TR = "tr"
    const val COUNTRY_TR = "TR"
    const val SPEECH_RATE = 1.0f

    // Motor Tanımlayıcıları
    const val UTTERANCE_ID = "DMS_VOICE_ALERT"
}