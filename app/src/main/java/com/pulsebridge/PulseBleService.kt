package com.pulsebridge

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@SuppressLint("MissingPermission")
class PulseBleService : Service() {

    private val TAG = "PulseBridge"
    private val AUTH_KEY_HEX = "a3a94bfc609bc30b6c41a5b8b61688ef"
    private val SERVER_URL = "https://y.shit.vc:68/api/hr"
    private val SECRET_TOKEN = "MY_SUPER_SECRET_PULSE_KEY"

    private val SERVICE_FEE0 = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")
    private val SERVICE_FEE1 = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
    private val CHAR_AUTH = UUID.fromString("00000009-0000-1000-8000-00805f9b34fb")
    private val SERVICE_HR = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val CHAR_HR_MEASURE = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CHAR_HR_CONTROL = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")
    private val CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "pulse_channel")
            .setContentTitle("PulseBridge")
            .setContentText("Поиск и подключение к Mi Band...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        startBleScan()
        return START_STICKY
    }

    private fun startBleScan() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = adapter?.bluetoothLeScanner ?: return
        Log.d(TAG, "Starting BLE Scan...")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: ""
                if (name.contains("Band", ignoreCase = true) || name.contains("Xiaomi", ignoreCase = true)) {
                    Log.d(TAG, "Found device: $name [${result.device.address}]")
                    scanner.stopScan(this)
                    connectToDevice(result.device)
                }
            }
        }
        scanner.startScan(callback)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun writeCharacteristicCompat(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }

    private fun writeDescriptorCompat(gatt: BluetoothGatt, desc: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(desc, value)
        } else {
            @Suppress("DEPRECATION")
            desc.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(desc)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT, discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected. Reconnecting in 3 sec...")
                scope.launch {
                    delay(3000)
                    startBleScan()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "Services discovered. Starting Auth...")
            val authService = gatt.getService(SERVICE_FEE1) ?: gatt.getService(SERVICE_FEE0)
            val authChar = authService?.getCharacteristic(CHAR_AUTH)
            if (authChar != null) {
                gatt.setCharacteristicNotification(authChar, true)
                val descriptor = authChar.getDescriptor(CLIENT_CONFIG)
                descriptor?.let {
                    writeDescriptorCompat(gatt, it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.characteristic.uuid == CHAR_AUTH) {
                val authChar = descriptor.characteristic
                writeCharacteristicCompat(gatt, authChar, byteArrayOf(0x01, 0x08))
            } else if (descriptor.characteristic.uuid == CHAR_HR_MEASURE) {
                val hrService = gatt.getService(SERVICE_HR)
                val ctrlChar = hrService?.getCharacteristic(CHAR_HR_CONTROL)
                ctrlChar?.let {
                    writeCharacteristicCompat(gatt, it, byteArrayOf(0x15, 0x01, 0x01))
                }
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChanged(gatt, characteristic, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChanged(gatt, characteristic, value)
        }

        private fun handleCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
            if (characteristic.uuid == CHAR_AUTH) {
                if (data.size >= 19 && data[0] == 0x10.toByte() && data[1] == 0x01.toByte() && data[2] == 0x01.toByte()) {
                    val nonce = data.copyOfRange(3, 19)
                    val encrypted = encryptAes(nonce, hexStringToByteArray(AUTH_KEY_HEX))
                    val response = byteArrayOf(0x03, 0x08) + encrypted
                    writeCharacteristicCompat(gatt, characteristic, response)
                } else if (data.size >= 3 && data[0] == 0x10.toByte() && data[1] == 0x03.toByte() && data[2] == 0x01.toByte()) {
                    Log.d(TAG, "AUTH SUCCESS! Subscribing to Heart Rate...")
                    val hrService = gatt.getService(SERVICE_HR)
                    val hrChar = hrService?.getCharacteristic(CHAR_HR_MEASURE)
                    hrChar?.let {
                        gatt.setCharacteristicNotification(it, true)
                        val desc = it.getDescriptor(CLIENT_CONFIG)
                        desc?.let { d ->
                            writeDescriptorCompat(gatt, d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        }
                    }
                }
            } else if (characteristic.uuid == CHAR_HR_MEASURE) {
                if (data.isNotEmpty()) {
                    val bpm = if (data[0].toInt() and 0x01 == 0) {
                        if (data.size > 1) data[1].toInt() and 0xFF else 0
                    } else {
                        if (data.size > 2) (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8) else 0
                    }
                    if (bpm > 0) {
                        Log.d(TAG, "Heart Rate: $bpm BPM")
                        updateNotification(bpm)
                        sendPulseToServer(bpm)
                    }
                }
            }
        }
    }

    private fun encryptAes(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(data)
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun sendPulseToServer(bpm: Int) {
        scope.launch {
            try {
                val url = URL(SERVER_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 2000
                conn.readTimeout = 2000

                val payload = JSONObject().apply {
                    put("bpm", bpm)
                    put("token", SECRET_TOKEN)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send: ${e.message}")
            }
        }
    }

    private fun updateNotification(bpm: Int) {
        val notification = NotificationCompat.Builder(this, "pulse_channel")
            .setContentTitle("PulseBridge: $bpm BPM")
            .setContentText("Трансляция в OBS активна")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("pulse_channel", "Pulse Bridge", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
