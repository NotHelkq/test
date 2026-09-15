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
            sendLog("ОШИБКА: Bluetooth выключен!")
            sendState("Bluetooth выключен!")
            return
        }

        sendLog("Прямое подключение к $TARGET_MAC...")
        val device = adapter.getRemoteDevice(TARGET_MAC)
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
                sendLog("УСПЕХ: GATT подключен! Опрос доступных служб...")
                sendState("GATT подключен. Опрос служб...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendLog("GATT отключился (код $status). Реконнект через 3 сек...")
                sendState("Отключено. Реконнект...")
                scope.launch {
                    delay(3000)
                    connectDirectOrScan()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            sendLog("--- АНАЛИЗ СЛУЖБ И ПОРТОВ XIAOMI ---")
            
            var targetAuthChar: BluetoothGattCharacteristic? = null
            var targetDataChar: BluetoothGattCharacteristic? = null

            for (s in gatt.services) {
                val sUuidStr = s.uuid.toString()
                val sShort = if (sUuidStr.startsWith("0000")) sUuidStr.substring(4, 8) else sUuidStr.substring(0, 8)
                
                // Пропускаем стандартные системные, смотрим только Xiaomi сервисы
                if (sShort in listOf("1800", "1801", "180a", "180f", "1812")) continue

                sendLog("Служба [0x$sShort]:")
                for (c in s.characteristics) {
                    val cUuidStr = c.uuid.toString()
                    val cShort = if (cUuidStr.startsWith("0000")) cUuidStr.substring(4, 8) else cUuidStr.substring(0, 8)
                    
                    val props = mutableListOf<String>()
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("R")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 || c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add("W")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("N")
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("I")
                    
                    sendLog("  -> Х-ка 0x$cShort [${props.joinToString(",")}]")

                    // Ищем Auth порт в fe95 или fdab (обычно 0010, 0001 или с правами Write+Notify)
                    if (sShort.equals("fe95", true) || sShort.equals("fdab", true)) {
                        if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            targetAuthChar = c
                        }
                    }

                    // Ищем порт потока данных пульса
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 && c != targetAuthChar) {
                        targetDataChar = c
                    }
                }
            }

            // Подписываемся на уведомления найденных портов
            if (targetAuthChar != null) {
                sendLog("Подписка на порт Xiaomi Auth: 0x${targetAuthChar.uuid.toString().substring(4, 8)}")
                gatt.setCharacteristicNotification(targetAuthChar, true)
                val desc = targetAuthChar.getDescriptor(CLIENT_CONFIG)
                desc?.let {
                    writeDescriptorCompat(gatt, it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
            } else if (targetDataChar != null) {
                sendLog("Подписка на поток данных: 0x${targetDataChar.uuid.toString().substring(4, 8)}")
                gatt.setCharacteristicNotification(targetDataChar, true)
                val desc = targetDataChar.getDescriptor(CLIENT_CONFIG)
                desc?.let {
                    writeDescriptorCompat(gatt, it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            sendLog("Дескриптор записан! Запрос Auth Challenge (0x01, 0x08)...")
            val ch = descriptor.characteristic
            writeCharacteristicCompat(gatt, ch, byteArrayOf(0x01, 0x08))
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleData(characteristic, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleData(characteristic, value)
        }

        private fun handleData(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
            val cUuid = characteristic.uuid.toString().substring(4, 8)
            val hexData = data.joinToString(" ") { String.format("%02X", it) }
            sendLog("<< [0x$cUuid]: $hexData")

            // Проверяем Challenge от Xiaomi
            if (data.size >= 19 && data[0] == 0x10.toByte() && data[1] == 0x01.toByte() && data[2] == 0x01.toByte()) {
                sendLog("Получен Nonce! Шифрование AES...")
                val nonce = data.copyOfRange(3, 19)
                val encrypted = encryptAes(nonce, hexStringToByteArray(AUTH_KEY_HEX))
                val response = byteArrayOf(0x03, 0x08) + encrypted
                bluetoothGatt?.let { writeCharacteristicCompat(it, characteristic, response) }
            } else if (data.size >= 3 && data[0] == 0x10.toByte() && data[1] == 0x03.toByte() && data[2] == 0x01.toByte()) {
                sendLog("УСПЕХ АВТОРИЗАЦИИ! Ключ принят!")
                sendState("Авторизовано! Замер пульса...")
            }

            // Поиск байта пульса (обычно число 40-200)
            for (i in data.indices) {
                val b = data[i].toInt() and 0xFF
                if (b in 45..195 && data.size <= 8) {
                    // Вероятный пульс
                    sendLog("❤️ ОБНАРУЖЕН ПУЛЬС: $b BPM")
                    sendState("Трансляция пульса ($b BPM)")
                    sendBpmUpdate(b)
                    updateNotification(b)
                    sendPulseToServer(b)
                    break
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
                conn.connectTimeout = 1500
                conn.readTimeout = 1500

                val payload = JSONObject().apply {
                    put("bpm", bpm)
                    put("token", SECRET_TOKEN)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                // Игнорируем мелкие таймауты сети
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
