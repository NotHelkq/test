package com.pulsebridge

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
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

    private val notifyCharsQueue = LinkedList<BluetoothGattCharacteristic>()
    private var authChar005e: BluetoothGattCharacteristic? = null
    private var aa02WriteChar: BluetoothGattCharacteristic? = null

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

        connectDirect()
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

    private fun connectDirect() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            sendLog("ОШИБКА: Bluetooth выключен!")
            sendState("Bluetooth выключен!")
            return
        }

        sendLog("Прямой коннект к $TARGET_MAC...")
        val device = adapter.getRemoteDevice(TARGET_MAC)
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun writeCharacteristicCompat(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
        val hex = value.joinToString(" ") { String.format("%02X", it) }
        val shortUuid = char.uuid.toString().substring(4, 8)
        sendLog(">> [0x$shortUuid] Запись: $hex")
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

    private fun subscribeNextNotification(gatt: BluetoothGatt) {
        if (notifyCharsQueue.isEmpty()) {
            sendLog("Все уведомления активированы! Старт Auth на 0x005e...")
            authChar005e?.let {
                // Шлем запрос Nonce на 0x005e
                writeCharacteristicCompat(gatt, it, byteArrayOf(0x01, 0x08))
            }
            return
        }
        val ch = notifyCharsQueue.poll() ?: return
        val shortUuid = ch.uuid.toString().substring(4, 8)
        sendLog("Подписка на уведомления [0x$shortUuid]...")
        gatt.setCharacteristicNotification(ch, true)
        val desc = ch.getDescriptor(CLIENT_CONFIG)
        if (desc != null) {
            writeDescriptorCompat(gatt, desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            subscribeNextNotification(gatt)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                sendLog("GATT подключен! Опрос служб...")
                sendState("GATT подключен. Опрос служб...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendLog("GATT отключен (код $status). Реконнект...")
                sendState("Отключено. Реконнект...")
                scope.launch {
                    delay(3000)
                    connectDirect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            notifyCharsQueue.clear()

            for (s in gatt.services) {
                val sShort = s.uuid.toString().substring(4, 8)
                for (c in s.characteristics) {
                    val cShort = c.uuid.toString().substring(4, 8)
                    if (sShort.equals("fe95", true) && cShort.equals("005e", true)) {
                        authChar005e = c
                        notifyCharsQueue.add(c)
                    } else if (sShort.equals("aa01", true) && cShort.equals("0003", true)) {
                        notifyCharsQueue.add(c)
                    } else if (sShort.equals("aa01", true) && cShort.equals("0002", true)) {
                        aa02WriteChar = c
                    } else if (sShort.equals("fdab", true) && (cShort.equals("0002", true) || cShort.equals("0003", true))) {
                        notifyCharsQueue.add(c)
                    }
                }
            }

            sendLog("Найдено целевых каналов для подписки: ${notifyCharsQueue.size}")
            subscribeNextNotification(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val cShort = descriptor.characteristic.uuid.toString().substring(4, 8)
            sendLog("Подписан на [0x$cShort] OK")
            // Переходим к следующей подписке в очереди
            subscribeNextNotification(gatt)
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleData(gatt, characteristic, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleData(gatt, characteristic, value)
        }

        private fun handleData(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
            val cUuid = characteristic.uuid.toString().substring(4, 8)
            val hexData = data.joinToString(" ") { String.format("%02X", it) }
            sendLog("<< [0x$cUuid]: $hexData")

            // Проверяем Challenge
            if (data.size >= 19 && data[0] == 0x10.toByte() && (data[1] == 0x01.toByte() || data[1] == 0x02.toByte()) && data[2] == 0x01.toByte()) {
                sendLog("Получен Nonce! Шифрование AES...")
                val nonce = data.copyOfRange(3, 19)
                val encrypted = encryptAes(nonce, hexStringToByteArray(AUTH_KEY_HEX))
                val response = byteArrayOf(0x03, 0x08) + encrypted
                writeCharacteristicCompat(gatt, characteristic, response)
            } else if (data.size >= 3 && data[0] == 0x10.toByte() && data[1] == 0x03.toByte() && data[2] == 0x01.toByte()) {
                sendLog("УСПЕХ АВТОРИЗАЦИИ! Ключ принят!")
                sendState("Авторизовано! Запрос пульса...")

                // Запускаем постоянный замер пульса через 0xaa02 или 0x005e
                aa02WriteChar?.let {
                    writeCharacteristicCompat(gatt, it, byteArrayOf(0x15, 0x01, 0x01))
                }
            }

            // Ищем байт пульса (от 45 до 195)
            for (i in data.indices) {
                val b = data[i].toInt() and 0xFF
                if (b in 45..195 && data.size <= 8) {
                    sendLog("❤️ ОБНАРУЖЕН ПУЛЬС: $b BPM")
                    sendState("Трансляция ($b BPM)")
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
