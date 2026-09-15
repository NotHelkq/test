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
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.CCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@SuppressLint("MissingPermission")
class PulseBleService : Service() {

    companion object {
        private const val TAG = "PulseBridge"
        private const val TARGET_MAC = "24:B2:31:75:87:50"
        private const val AUTH_KEY_HEX = "a3a94bfc609bc30b6c41a5b8b61688ef"
        private const val SERVER_URL = "https://y.shit.vc:68/api/hr"
        private const val SECRET_TOKEN = "MY_SUPER_SECRET_PULSE_KEY"

        // Xiaomi Smart Band 9 Active (BLE V2)
        val SERVICE_FE95: UUID = UUID.fromString("0000fe95-0000-1000-8000-00805f9b34fb")
        val CHAR_RX_005E: UUID = UUID.fromString("0000005e-0000-1000-8000-00805f9b34fb")
        val CHAR_TX_005F: UUID = UUID.fromString("0000005f-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val PREAMBLE = byteArrayOf(0xA5.toByte(), 0xA5.toByte())
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var txChar: BluetoothGattCharacteristic? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val rxBuffer = ByteArrayOutputStream()
    private val seqCounter = AtomicInteger(0)

    private val secretKey = hexStringToByteArray(AUTH_KEY_HEX)
    private val phoneNonce = ByteArray(16)
    private var watchNonce = ByteArray(16)
    private var encryptionKey = ByteArray(16)
    private var decryptionKey = ByteArray(16)
    private var encryptionNonce = ByteArray(4)
    private var decryptionNonce = ByteArray(4)

    private var isSessionConfigured = false
    private var isAuthenticated = false
    private var isStreaming = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "pulse_channel")
            .setContentTitle("PulseBridge")
            .setContentText("??????????? ? $TARGET_MAC...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        sendLog("?????? ??????? Band 9 Active. ????: $TARGET_MAC")
        sendState("???????????...")

        connectDirect()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHeartRateStream()
        bluetoothGatt?.close()
        bluetoothGatt = null
        sendLog("?????? ??????????")
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
            sendLog("??????: Bluetooth ????????!")
            sendState("Bluetooth ????????!")
            return
        }

        sendLog("?????? ??????????? ? $TARGET_MAC...")
        val device = adapter.getRemoteDevice(TARGET_MAC)
        bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                sendLog("GATT ?????????! ?????? MTU 512...")
                sendState("GATT ?????????. ????? ?????...")
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendLog("GATT ???????? (?????? $status). ????????? ????? 3 ???...")
                sendState("?????????. ?????????...")
                resetState()
                scope.launch {
                    delay(3000)
                    connectDirect()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            sendLog("MTU ??????????: $mtu. ????? ????????...")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val sFe95 = gatt.getService(SERVICE_FE95)
            if (sFe95 == null) {
                sendLog("??????: ?????? 0xfe95 ?? ??????? ?? ????????!")
                return
            }

            rxChar = sFe95.getCharacteristic(CHAR_RX_005E)
            txChar = sFe95.getCharacteristic(CHAR_TX_005F)

            if (rxChar == null || txChar == null) {
                sendLog("??????: ?????????????? 0x005e/0x005f ?? ???????!")
                return
            }

            sendLog("?????? ????? Xiaomi V2 (RX: 0x005e, TX: 0x005f)!")
            sendLog("???????? ?? ??????????? 0x005e...")

            gatt.setCharacteristicNotification(rxChar, true)
            val desc = rxChar?.getDescriptor(CLIENT_CONFIG)
            if (desc != null) {
                writeDescriptorCompat(gatt, desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                sendLog("??????: Client config descriptor ?? ?????? ?? 0x005e")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.characteristic.uuid == CHAR_RX_005E) {
                sendLog("???????? ?? 0x005e ???????! ?????? ???????????? ?????? V2...")
                sendSessionConfigRequest(gatt)
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleRxData(gatt, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleRxData(gatt, value)
        }
    }

    private fun resetState() {
        rxBuffer.reset()
        seqCounter.set(0)
        isSessionConfigured = false
        isAuthenticated = false
        isStreaming = false
    }

    // =========================================================================
    // ???????? ??????? Xiaomi SPP V2
    // =========================================================================

    private fun sendSessionConfigRequest(gatt: BluetoothGatt) {
        val payload = byteArrayOf(
            0x01,
            0x01, 0x03, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x00, 0x00, 0xFC.toByte(),
            0x03, 0x02, 0x00, 0x20, 0x00,
            0x04, 0x02, 0x00, 0x10, 0x27
        )

        val packet = buildPacket(packetType = 2, seq = 0, payload = payload)
        sendLog(">> [0x005f] ????? ?????? V2 (${packet.size} ????)")
        writeTx(gatt, packet)
    }

    private fun sendAck(gatt: BluetoothGatt, seq: Int) {
        val ack = buildPacket(packetType = 1, seq = seq, payload = ByteArray(0))
        writeTx(gatt, ack)
    }

    private fun sendDataPacket(gatt: BluetoothGatt, rawChannel: Int, opCode: Int, data: ByteArray) {
        val payload = ByteBuffer.allocate(2 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .put((rawChannel and 0x0F).toByte())
            .put((opCode and 0xFF).toByte())
            .put(data)
            .array()

        val seq = seqCounter.getAndIncrement() and 0xFF
        val packet = buildPacket(packetType = 3, seq = seq, payload = payload)
        writeTx(gatt, packet)
    }

    private fun buildPacket(packetType: Int, seq: Int, payload: ByteArray): ByteArray {
        val crc = calculateCrc16Arc(payload)
        return ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(PREAMBLE)
            .put((packetType and 0x0F).toByte())
            .put((seq and 0xFF).toByte())
            .putShort(payload.size.toShort())
            .putShort(crc.toShort())
            .put(payload)
            .array()
    }

    private fun calculateCrc16Arc(payload: ByteArray): Int {
        var crc = 0
        for (b in payload) {
            val ub = b.toInt() and 0xFF
            for (j in 0 until 8) {
                crc = crc shl 1
                if ((((crc ushr 16) and 1) xor ((ub ushr j) and 1)) == 1) {
                    crc = crc xor 0x8005
                }
            }
        }
        return Integer.reverse(crc) ushr 16
    }

    private fun writeTx(gatt: BluetoothGatt, data: ByteArray) {
        val ch = txChar ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(ch, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ch.value = data
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
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

    // =========================================================================
    // ????? ? ?????? ??????? (RX Buffer)
    // =========================================================================

    private fun handleRxData(gatt: BluetoothGatt, chunk: ByteArray) {
        synchronized(rxBuffer) {
            rxBuffer.write(chunk)
            val buf = rxBuffer.toByteArray()
            var offset = 0

            while (offset + 8 <= buf.size) {
                if (buf[offset] != 0xA5.toByte() || buf[offset + 1] != 0xA5.toByte()) {
                    offset++
                    continue
                }

                val packetType = buf[offset + 2].toInt() and 0x0F
                val seq = buf[offset + 3].toInt() and 0xFF
                val payloadLen = (buf[offset + 4].toInt() and 0xFF) or ((buf[offset + 5].toInt() and 0xFF) shl 8)
                val totalPacketLen = 8 + payloadLen

                if (offset + totalPacketLen > buf.size) {
                    break
                }

                val payload = buf.copyOfRange(offset + 8, offset + totalPacketLen)
                offset += totalPacketLen

                processPacket(gatt, packetType, seq, payload)
            }

            rxBuffer.reset()
            if (offset < buf.size) {
                rxBuffer.write(buf, offset, buf.size - offset)
            }
        }
    }

    private fun processPacket(gatt: BluetoothGatt, packetType: Int, seq: Int, payload: ByteArray) {
        when (packetType) {
            1 -> {
                // ACK
            }
            2 -> {
                // SessionConfig
                val opCode = if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else -1
                sendLog("<< [SessionConfig] OpCode: $opCode")
                if (opCode == 2) {
                    isSessionConfigured = true
                    sendLog("?????? V2 ????????????! ???????? Nonce (Auth ??? 1)...")
                    sendAck(gatt, seq)
                    sendPhoneNonce(gatt)
                }
            }
            3 -> {
                // DATA
                sendAck(gatt, seq)
                if (payload.size < 2) return

                val channel = payload[0].toInt() and 0x0F
                val opCode = payload[1].toInt() and 0xFF
                var data = payload.copyOfRange(2, payload.size)

                if (opCode == 2 && isAuthenticated) {
                    try {
                        data = decryptV2(decryptionKey, data)
                    } catch (e: Exception) {
                        sendLog("?????? ?????????? V2: ${e.message}")
                        return
                    }
                }

                handleProtobufCommand(gatt, data)
            }
        }
    }

    // =========================================================================
    // ?????????????? Xiaomi Protobuf
    // =========================================================================

    private fun sendPhoneNonce(gatt: BluetoothGatt) {
        SecureRandom().nextBytes(phoneNonce)
        val nonceMsg = ProtoWriter.encodeBytes(1, phoneNonce)
        val authMsg = ProtoWriter.encodeBytes(30, nonceMsg)
        val cmdMsg = ProtoWriter.encodeVarint(1, 1) +
                ProtoWriter.encodeVarint(2, 26) +
                ProtoWriter.encodeBytes(3, authMsg)

        sendLog(">> [Auth ??? 1] ???????? Phone Nonce...")
        sendDataPacket(gatt, rawChannel = 1, opCode = 1, data = cmdMsg)
    }

    private fun handleProtobufCommand(gatt: BluetoothGatt, data: ByteArray) {
        val cmd = ProtoReader.parseFields(data)
        val type = cmd[1]?.asLong()?.toInt() ?: -1
        val subtype = cmd[2]?.asLong()?.toInt() ?: -1

        if (type == 1 && subtype == 26) {
            // WatchNonce
            val authBytes = cmd[3]?.asBytes() ?: return
            val authFields = ProtoReader.parseFields(authBytes)
            val watchNonceBytes = authFields[31]?.asBytes() ?: return
            val watchNonceFields = ProtoReader.parseFields(watchNonceBytes)

            watchNonce = watchNonceFields[1]?.asBytes() ?: return
            val watchHmac = watchNonceFields[2]?.asBytes() ?: return

            sendLog("<< [Auth ??? 2] Watch Nonce ???????! ???????? ?????...")
            deriveKeysAndFinishAuth(gatt, watchHmac)
        } else if (type == 1 && subtype == 27) {
            // Auth result
            val status = cmd[100]?.asLong()?.toInt() ?: 1
            if (status == 1) {
                isAuthenticated = true
                sendLog("?? ???????? ??????????? Band 9 Active! ???? ???????!")
                sendState("????????????! ?????? ??????...")

                startRealtimeHeartRate(gatt)
            } else {
                sendLog("??????: ??????? ???????? ??????????? (?????? $status)!")
                sendState("?????? ???????????")
            }
        } else if (type == 8 && subtype == 47) {
            // RealTimeStats event
            val healthBytes = cmd[10]?.asBytes() ?: return
            val healthFields = ProtoReader.parseFields(healthBytes)
            val rtsBytes = healthFields[39]?.asBytes() ?: return
            val rtsFields = ProtoReader.parseFields(rtsBytes)

            val hr = rtsFields[4]?.asLong()?.toInt() ?: 0
            val steps = rtsFields[1]?.asLong()?.toInt() ?: 0
            val calories = rtsFields[2]?.asLong()?.toInt() ?: 0

            if (hr in 35..230) {
                sendLog("?? ?????: $hr BPM | ????: $steps | ???????: $calories")
                sendState("?????????? ($hr BPM)")
                sendBpmUpdate(hr)
                updateNotification(hr, steps)
                sendPulseToServer(hr)
            }
        }
    }

    private fun deriveKeysAndFinishAuth(gatt: BluetoothGatt, watchHmac: ByteArray) {
        val step2Hmac = computeAuthStep3Hmac(secretKey, phoneNonce, watchNonce)
        decryptionKey = step2Hmac.copyOfRange(0, 16)
        encryptionKey = step2Hmac.copyOfRange(16, 32)
        decryptionNonce = step2Hmac.copyOfRange(32, 36)
        encryptionNonce = step2Hmac.copyOfRange(36, 40)

        val expectedWatchHmac = hmacSha256(decryptionKey, watchNonce + phoneNonce)
        if (!expectedWatchHmac.contentEquals(watchHmac)) {
            sendLog("??????: HMAC ???????? ?? ??????! ????????? auth key.")
            sendState("?????? ????? Auth!")
            return
        }

        sendLog("HMAC ????? ???????????! ?????????? ????? ????????.")
        sendLog(">> [Auth ??? 3] ???????? AuthStep3...")

        val encryptedNonces = hmacSha256(encryptionKey, phoneNonce + watchNonce)

        val deviceInfoMsg = ProtoWriter.encodeVarint(1, 0) +
                ProtoWriter.encodeFloat(2, 33f) +
                ProtoWriter.encodeString(3, "PulseBridge") +
                ProtoWriter.encodeVarint(4, 224) +
                ProtoWriter.encodeString(5, "RU")

        val ccmNonce = encryptionNonce + ByteArray(8)
        val encryptedDeviceInfo = encryptCcm(encryptionKey, ccmNonce, deviceInfoMsg)

        val step3Msg = ProtoWriter.encodeBytes(1, encryptedNonces) +
                ProtoWriter.encodeBytes(2, encryptedDeviceInfo)
        val authMsg = ProtoWriter.encodeBytes(32, step3Msg)
        val cmdMsg = ProtoWriter.encodeVarint(1, 1) +
                ProtoWriter.encodeVarint(2, 27) +
                ProtoWriter.encodeBytes(3, authMsg)

        sendDataPacket(gatt, rawChannel = 1, opCode = 1, data = cmdMsg)
    }

    private fun startRealtimeHeartRate(gatt: BluetoothGatt) {
        isStreaming = true
        // Command { type = 8, subtype = 45 }
        val cmd = byteArrayOf(0x08, 0x08, 0x10, 0x2D)
        val encryptedCmd = encryptV2(encryptionKey, cmd)

        sendLog(">> [Realtime Stats] ????? ???????????? ?????? (type=8, subtype=45)...")
        sendDataPacket(gatt, rawChannel = 1, opCode = 2, data = encryptedCmd)
    }

    private fun stopHeartRateStream() {
        if (!isAuthenticated || !isStreaming) return
        val gatt = bluetoothGatt ?: return
        try {
            // Command { type = 8, subtype = 46 }
            val cmd = byteArrayOf(0x08, 0x08, 0x10, 0x2E)
            val encryptedCmd = encryptV2(encryptionKey, cmd)
            sendDataPacket(gatt, rawChannel = 1, opCode = 2, data = encryptedCmd)
            isStreaming = false
        } catch (_: Exception) {}
    }

    // =========================================================================
    // ????????????
    // =========================================================================

    private fun computeAuthStep3Hmac(secretKey: ByteArray, phoneNonce: ByteArray, watchNonce: ByteArray): ByteArray {
        val miwearAuthBytes = "miwear-auth".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(phoneNonce + watchNonce, "HmacSHA256"))
        val hmacKeyBytes = mac.doFinal(secretKey)
        val key = SecretKeySpec(hmacKeyBytes, "HmacSHA256")
        mac.init(key)

        val output = ByteArray(64)
        var tmp = ByteArray(0)
        var b = 1.toByte()
        var i = 0
        while (i < output.size) {
            mac.update(tmp)
            mac.update(miwearAuthBytes)
            mac.update(b)
            tmp = mac.doFinal()
            var j = 0
            while (j < tmp.size && i < output.size) {
                output[i] = tmp[j]
                j++
                i++
            }
            b++
        }
        return output
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun encryptCcm(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = CCMBlockCipher(AESEngine())
        cipher.init(true, AEADParameters(KeyParameter(key), 32, nonce, null))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len)
        return out
    }

    private fun encryptV2(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
        return cipher.doFinal(plaintext)
    }

    private fun decryptV2(key: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
        return cipher.doFinal(ciphertext)
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

    // =========================================================================
    // ???????? ?? VPS ?????? ? ???????????
    // =========================================================================

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
            } catch (_: Exception) {}
        }
    }

    private fun updateNotification(bpm: Int, steps: Int) {
        val notification = NotificationCompat.Builder(this, "pulse_channel")
            .setContentTitle("PulseBridge: $bpm BPM")
            .setContentText("OBS ?????? | ?????: $steps")
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

    // =========================================================================
    // ??????????? Protobuf ?????
    // =========================================================================

    object ProtoWriter {
        fun encodeVarint(fieldNum: Int, value: Long): ByteArray {
            return encodeTag(fieldNum, 0) + writeVarint(value)
        }

        fun encodeFloat(fieldNum: Int, value: Float): ByteArray {
            val bits = java.lang.Float.floatToIntBits(value)
            return encodeTag(fieldNum, 5) + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bits).array()
        }

        fun encodeString(fieldNum: Int, value: String): ByteArray {
            return encodeBytes(fieldNum, value.toByteArray(Charsets.UTF_8))
        }

        fun encodeBytes(fieldNum: Int, data: ByteArray): ByteArray {
            return encodeTag(fieldNum, 2) + writeVarint(data.size.toLong()) + data
        }

        private fun encodeTag(fieldNum: Int, wireType: Int): ByteArray {
            return writeVarint(((fieldNum.toLong() shl 3) or wireType.toLong()))
        }

        private fun writeVarint(v: Long): ByteArray {
            var value = v
            val out = ByteArrayOutputStream()
            while (value and 0x7FL.inv() != 0L) {
                out.write(((value and 0x7F) or 0x80).toInt())
                value = value ushr 7
            }
            out.write((value and 0x7F).toInt())
            return out.toByteArray()
        }
    }

    object ProtoReader {
        sealed class Value {
            data class Varint(val v: Long) : Value()
            data class LengthDelimited(val bytes: ByteArray) : Value()
            data class Fixed32(val bytes: ByteArray) : Value()

            fun asLong(): Long? = (this as? Varint)?.v
            fun asBytes(): ByteArray? = (this as? LengthDelimited)?.bytes
        }

        fun parseFields(buf: ByteArray): Map<Int, Value> {
            val fields = mutableMapOf<Int, Value>()
            var pos = 0
            while (pos < buf.size) {
                val (tag, newPos) = readVarint(buf, pos) ?: break
                pos = newPos
                val wireType = (tag and 0x07).toInt()
                val fieldNum = (tag ushr 3).toInt()

                when (wireType) {
                    0 -> {
                        val (valLong, p) = readVarint(buf, pos) ?: break
                        pos = p
                        fields[fieldNum] = Value.Varint(valLong)
                    }
                    2 -> {
                        val (len, p) = readVarint(buf, pos) ?: break
                        pos = p
                        val length = len.toInt()
                        if (pos + length > buf.size) break
                        val data = buf.copyOfRange(pos, pos + length)
                        pos += length
                        fields[fieldNum] = Value.LengthDelimited(data)
                    }
                    5 -> {
                        if (pos + 4 > buf.size) break
                        val data = buf.copyOfRange(pos, pos + 4)
                        pos += 4
                        fields[fieldNum] = Value.Fixed32(data)
                    }
                    else -> break
                }
            }
            return fields
        }

        private fun readVarint(buf: ByteArray, startPos: Int): Pair<Long, Int>? {
            var pos = startPos
            var result = 0L
            var shift = 0
            while (pos < buf.size) {
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return Pair(result, pos)
                shift += 7
                if (shift >= 64) return null
            }
            return null
        }
    }
}