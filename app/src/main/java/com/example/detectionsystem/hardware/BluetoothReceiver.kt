package com.example.detectionsystem.hardware

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.detectionsystem.MainActivity

// ==========================================
// 1. BROADCAST RECEIVER (Sadece Sinyalleri Dinler)
// ==========================================
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> handleDeviceConnected(context, intent)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleDeviceDisconnected()
        }
    }

    private fun handleDeviceConnected(context: Context, intent: Intent) {
        val device = extractBluetoothDevice(intent)
        val deviceName = getSafeDeviceName(device)

        Log.i(BluetoothConstants.LOG_TAG, "Bağlantı Kuruldu: $deviceName")

        // İşlemi Bildirim Yöneticisine Devret (SRP)
        BluetoothNotificationHelper.sendDriveNotification(context, deviceName)
    }

    private fun handleDeviceDisconnected() {
        Log.i(BluetoothConstants.LOG_TAG, "Bağlantı Koptu. Sürüş Bitti.")
    }

    // Android'in farklı sürümlerindeki getParcelableExtra değişikliklerini güvenli yönetir
    @Suppress("DEPRECATION")
    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun getSafeDeviceName(device: BluetoothDevice?): String {
        return try {
            device?.name ?: BluetoothConstants.UNKNOWN_DEVICE_NAME
        } catch (e: SecurityException) {
            Log.w(BluetoothConstants.LOG_TAG, "Bluetooth isim okuma izni yok.")
            BluetoothConstants.DEFAULT_DEVICE_NAME
        }
    }
}

// ==========================================
// 2. BİLDİRİM YÖNETİCİSİ (Sadece Bildirim İşleriyle İlgilenir)
// ==========================================
object BluetoothNotificationHelper {

    fun sendDriveNotification(context: Context, deviceName: String) {
        createNotificationChannel(context)

        val pendingIntent = createLaunchIntent(context)
        val builder = buildNotification(context, deviceName, pendingIntent)

        showNotification(context, builder)
    }

    private fun createNotificationChannel(context: Context) {
        // NotificationChannel is required for Android 8.0 (API 26) and above.
        // Since minSdk is 26, we create it directly.
        val channel = NotificationChannel(
            BluetoothConstants.CHANNEL_ID,
            BluetoothConstants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = BluetoothConstants.CHANNEL_DESC
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createLaunchIntent(context: Context): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(context: Context, deviceName: String, pendingIntent: PendingIntent): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, BluetoothConstants.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(BluetoothConstants.NOTIF_TITLE)
            .setContentText("$deviceName bağlandı. Sürüş takibini başlatmak için dokunun.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
    }

    private fun showNotification(context: Context, builder: NotificationCompat.Builder) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(BluetoothConstants.NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e(BluetoothConstants.LOG_TAG, "Bildirim izni reddedilmiş.", e)
        }
    }
}

// ==========================================
// 3. SABİTLER (Magic Strings / Numbers Temizliği)
// ==========================================
object BluetoothConstants {
    const val LOG_TAG = "BluetoothReceiver"
    const val NOTIFICATION_ID = 999

    // Kanal Sabitleri
    const val CHANNEL_ID = "dms_auto_start_channel"
    const val CHANNEL_NAME = "Otomatik Başlatma"
    const val CHANNEL_DESC = "Bluetooth bağlandığında sürüş modunu önerir."

    // UI Metinleri
    const val NOTIF_TITLE = "🚗 Smart DMS - Araç Bağlantısı"
    const val DEFAULT_DEVICE_NAME = "Araç Bluetooth'u"
    const val UNKNOWN_DEVICE_NAME = "Yeni Araç/Cihaz"
}
