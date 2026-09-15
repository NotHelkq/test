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
    private val TARGET_MAC = "24:B2:31:75:87:50"
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
            .setContentText("Подключение к $TARGET_MAC...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        sendLog("Запуск сервиса. Цель: $TARGET_MAC")
        sendState("Подключение к $TARGET_MAC...")

        connectDirectOrScan()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        bluetoothGatt = null
        sendLog("Сервис остановлен")
    }

    private fun sendLog(msg: String) {
        Log.d(TAG, msg)
        sendBroadcast(Intent("com.pulsebridge.LOG").apply { putExtra("msg", msg) })
    }

    private fun sendState(status: String) {
        sendBroadcast(Intent("com.pulsebridge.STATUS").apply { putExtra("status", status) })
    }

    private fun sendBpmUpdate(bpm: Int) {
        sendBroadcast(Intent("com.pulsebridge.BPM").apply { putExtra("bpm", bpm) })
    }

    private fun connectDirectOrScan() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            sendLog("ОШИБКА: Bluetooth выключен на телефоне!")
            sendState("Bluetooth выключен!")
            return
        }

        sendLog("Прямое подключение к $TARGET_MAC...")
        val device = adapter.getRemoteDevice(TARGET_MAC)
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        // Параллельно запускаем точечный скан по MAC для быстрого пробуждения
        val scanner = adapter.bluetoothLeScanner
        if (scanner != null) {
            val filter = ScanFilter.Builder().setDeviceAddress(TARGET_MAC).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(listOf(filter), settings, object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    sendLog("Скан обнаружил браслет в эфире (RSSI: ${result.rssi})")
                    scanner.stopScan(this)
                    if (bluetoothGatt == null) {
                        bluetoothGatt = result.device.connectGatt(this@PulseBleService, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    }
                }
            })
        }
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
                sendLog("УСПЕХ: GATT подключен! Опрос доступных служб...")
                sendState("GATT подключен. Опрос служб...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendLog("GATT отключился (status: $status). Повтор через 3 сек...")
                sendState("Отключено. Реконнект...")
                scope.launch {
                    delay(3000)
                    connectDirectOrScan()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            sendLog("Службы обнаружены (всего ${gatt.services.size}):")
            for (s in gatt.services) {
                val shortUuid = s.uuid.toString().substring(4, 8)
                sendLog(" -> Служба: 0x$shortUuid")
            }

            val authService = gatt.getService(SERVICE_FEE1) ?: gatt.getService(SERVICE_FEE0)
            val authChar = authService?.getCharacteristic(CHAR_AUTH)

            if (authChar != null) {
                sendLog("Служба авторизации найдена. Подписка на Auth...")
                sendState("Авторизация ключом...")
                gatt.setCharacteristicNotification(authChar, true)
                val descriptor = authChar.getDescriptor(CLIENT_CONFIG)
                descriptor?.let {
                    writeDescriptorCompat(gatt, it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
            } else {
                sendLog("Служба Auth 0x0009 не найдена. Пробуем Heart Rate напрямую...")
                subscribeHeartRate(gatt)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.characteristic.uuid == CHAR_AUTH) {
                sendLog("Отправка запроса на Auth Challenge (0x01, 0x08)...")
                val authChar = descriptor.characteristic
                writeCharacteristicCompat(gatt, authChar, byteArrayOf(0x01, 0x08))
            } else if (descriptor.characteristic.uuid == CHAR_HR_MEASURE) {
                sendLog("Подписка на пульс активна! Отправка команды старта замера...")
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
                    sendLog("Получен Auth Challenge от браслета. Шифрование AES...")
                    val nonce = data.copyOfRange(3, 19)
                    val encrypted = encryptAes(nonce, hexStringToByteArray(AUTH_KEY_HEX))
                    val response = byteArrayOf(0x03, 0x08) + encrypted
                    writeCharacteristicCompat(gatt, characteristic, response)
                } else if (data.size >= 3 && data[0] == 0x10.toByte() && data[1] == 0x03.toByte() && data[2] == 0x01.toByte()) {
                    sendLog("УСПЕХ АВТОРИЗАЦИИ! Ключ подошел!")
                    sendState("Авторизовано! Подключение пульса...")
                    subscribeHeartRate(gatt)
                } else {
                    sendLog("Ответ Auth: ${data.joinToString(" ") { String.format("%02X", it) }}")
                }
            } else if (characteristic.uuid == CHAR_HR_MEASURE) {
                if (data.isNotEmpty()) {
                    val bpm = if (data[0].toInt() and 0x01 == 0) {
                        if (data.size > 1) data[1].toInt() and 0xFF else 0
                    } else {
                        if (data.size > 2) (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8) else 0
                    }
                    if (bpm > 0) {
                        sendLog("❤️ ПУЛЬС: $bpm BPM")
                        sendState("Трансляция пульса активна ($bpm BPM)")
                        sendBpmUpdate(bpm)
                        updateNotification(bpm)
                        sendPulseToServer(bpm)
                    }
                }
            }
        }

        private fun subscribeHeartRate(gatt: BluetoothGatt) {
            val hrService = gatt.getService(SERVICE_HR)
            val hrChar = hrService?.getCharacteristic(CHAR_HR_MEASURE)
            if (hrChar != null) {
                sendLog("Подписка на службу Heart Rate 0x180D...")
                gatt.setCharacteristicNotification(hrChar, true)
                val desc = hrChar.getDescriptor(CLIENT_CONFIG)
                desc?.let { d ->
                    writeDescriptorCompat(gatt, d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
            } else {
                sendLog("Служба 0x180D не найдена. Проверяем сервисы...")
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
                conn.connectTimeout = 1500
                conn.readTimeout = 1500

                val payload = JSONObject().apply {
                    put("bpm", bpm)
                    put("token", SECRET_TOKEN)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val code = conn.responseCode
                if (code == 200) {
                    // Отправлено успешно
                } else {
                    sendLog("VPS ответил кодом: $code")
                }
                conn.disconnect()
            } catch (e: Exception) {
                sendLog("Ошибка отправки на VPS: ${e.message}")
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
        manager?.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("pulse_channel", "Pulse Bridge", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
