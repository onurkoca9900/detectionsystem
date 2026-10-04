package com.example.detectionsystem.hardware

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BleDeviceChoice(val address: String, val name: String)

enum class BleBraceletStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class BleBraceletState(
    val status: BleBraceletStatus = BleBraceletStatus.DISCONNECTED,
    val devices: List<BleDeviceChoice> = emptyList(),
    val connectedName: String? = null,
    val message: String? = null
)

/** Discovers nearby BLE devices and holds a real GATT connection to the selected device. */
class BleBraceletManager(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothAdapter = appContext.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val discoveredDevices = linkedMapOf<String, BluetoothDevice>()
    private var scanner: BluetoothLeScanner? = null
    private var activeGatt: BluetoothGatt? = null

    private val mutableState = MutableStateFlow(BleBraceletState())
    val state: StateFlow<BleBraceletState> = mutableState.asStateFlow()

    private val stopScanRunnable = Runnable { stopScan() }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::addScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.removeCallbacks(stopScanRunnable)
            scanner = null
            mutableState.value = mutableState.value.copy(
                status = BleBraceletStatus.ERROR,
                message = "Bluetooth taraması başarısız oldu (kod: $errorCode)."
            )
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (activeGatt !== gatt) {
                gatt.close()
                return
            }
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                mutableState.value = mutableState.value.copy(
                    status = BleBraceletStatus.CONNECTED,
                    message = "BLE bağlantısı kuruldu."
                )
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    setError("Bluetooth bağlantı izni gerekiyor.")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mutableState.value = mutableState.value.copy(
                    status = if (status == BluetoothGatt.GATT_SUCCESS) BleBraceletStatus.DISCONNECTED else BleBraceletStatus.ERROR,
                    connectedName = null,
                    message = if (status == BluetoothGatt.GATT_SUCCESS) "Bileklik bağlantısı kesildi." else "BLE bağlantısı kurulamadı (kod: $status)."
                )
                gatt.close()
                if (activeGatt === gatt) activeGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mutableState.value = mutableState.value.copy(
                    status = BleBraceletStatus.CONNECTED,
                    message = "BLE bağlantısı kuruldu; ${gatt.services.size} hizmet bulundu."
                )
            } else {
                mutableState.value = mutableState.value.copy(
                    status = BleBraceletStatus.CONNECTED,
                    message = "BLE bağlantısı kuruldu; hizmet listesi okunamadı."
                )
            }
        }
    }

    fun startScan() {
        stopScan(updateStatus = false)
        discoveredDevices.clear()
        mutableState.value = BleBraceletState(
            status = BleBraceletStatus.SCANNING,
            message = "Yakındaki BLE cihazları aranıyor…"
        )

        try {
            val adapter = bluetoothAdapter
            if (adapter == null) {
                setError("Bu telefonda Bluetooth LE desteklenmiyor.")
                return
            }
            if (!adapter.isEnabled) {
                setError("Önce telefonun Bluetooth özelliğini açın.")
                return
            }

            val bleScanner = adapter.bluetoothLeScanner
            if (bleScanner == null) {
                setError("Bluetooth LE tarayıcısı kullanılamıyor.")
                return
            }

            scanner = bleScanner
            bleScanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback
            )
            mainHandler.postDelayed(stopScanRunnable, SCAN_DURATION_MS)
        } catch (e: SecurityException) {
            setError("Yakındaki BLE cihazlarını aramak için Bluetooth izinleri gerekiyor.")
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan could not start", e)
            setError("BLE taraması başlatılamadı.")
        }
    }

    fun stopScan() = stopScan(updateStatus = true)

    private fun stopScan(updateStatus: Boolean) {
        mainHandler.removeCallbacks(stopScanRunnable)
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth scan permission was revoked", e)
        } catch (e: Exception) {
            Log.w(TAG, "BLE scan could not be stopped", e)
        }
        scanner = null
        if (updateStatus && mutableState.value.status == BleBraceletStatus.SCANNING) {
            mutableState.value = mutableState.value.copy(
                status = BleBraceletStatus.DISCONNECTED,
                message = if (mutableState.value.devices.isEmpty()) "BLE cihazı bulunamadı." else "Bir bileklik seçin."
            )
        }
    }

    fun connect(deviceAddress: String) {
        stopScan(updateStatus = false)
        val device = try {
            discoveredDevices[deviceAddress] ?: bluetoothAdapter?.getRemoteDevice(deviceAddress)
        } catch (e: SecurityException) {
            setError("Bilekliğe bağlanmak için Bluetooth bağlantı izni gerekiyor.")
            return
        } catch (e: IllegalArgumentException) {
            null
        }

        if (device == null) {
            setError("Seçilen BLE cihazı artık bulunamıyor. Yeniden tarayın.")
            return
        }

        closeGatt()
        val deviceName = mutableState.value.devices.firstOrNull { it.address == deviceAddress }?.name
            ?: readDeviceName(device)
        mutableState.value = mutableState.value.copy(
            status = BleBraceletStatus.CONNECTING,
            connectedName = deviceName,
            message = "$deviceName cihazına bağlanılıyor…"
        )

        try {
            activeGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (activeGatt == null) setError("BLE bağlantısı başlatılamadı.")
        } catch (e: SecurityException) {
            setError("Bilekliğe bağlanmak için Bluetooth bağlantı izni gerekiyor.")
        } catch (e: Exception) {
            Log.e(TAG, "BLE connection could not start", e)
            setError("BLE bağlantısı başlatılamadı.")
        }
    }

    fun disconnect() {
        closeGatt()
        mutableState.value = mutableState.value.copy(
            status = BleBraceletStatus.DISCONNECTED,
            connectedName = null,
            message = "Bileklik bağlantısı kesildi."
        )
    }

    fun close() {
        stopScan(updateStatus = false)
        closeGatt()
    }

    private fun addScanResult(result: ScanResult) {
        try {
            val device = result.device
            val address = device.address
            val name = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                ?: readDeviceName(device).takeIf(String::isNotBlank)
                ?: "BLE cihazı (${address.takeLast(5)})"
            discoveredDevices[address] = device
            val choices = discoveredDevices.map { (deviceAddress, _) ->
                val knownName = mutableState.value.devices.firstOrNull { it.address == deviceAddress }?.name
                BleDeviceChoice(deviceAddress, if (deviceAddress == address) name else knownName ?: "BLE cihazı (${deviceAddress.takeLast(5)})")
            }.sortedBy { it.name.lowercase() }
            mutableState.value = mutableState.value.copy(devices = choices)
        } catch (e: SecurityException) {
            setError("BLE cihaz bilgilerini okumak için Bluetooth izinleri gerekiyor.")
        }
    }

    private fun readDeviceName(device: BluetoothDevice): String = try {
        device.name.orEmpty()
    } catch (_: SecurityException) {
        ""
    }

    private fun closeGatt() {
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth connection permission was revoked", e)
        } finally {
            activeGatt = null
        }
    }

    private fun setError(message: String) {
        mutableState.value = mutableState.value.copy(status = BleBraceletStatus.ERROR, message = message)
    }

    private companion object {
        const val TAG = "BleBraceletManager"
        const val SCAN_DURATION_MS = 12_000L
    }
}
