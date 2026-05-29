package com.scamslayer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.scamslayer.app.MainActivity
import com.scamslayer.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AudioBridgeService : Service() {

    companion object {
        const val TAG = "AudioBridgeService"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_PERSONA_ID = "extra_persona_id"
        const val EXTRA_BACKEND_URL = "extra_backend_url"
        const val CHANNEL_ID = "scamslayer_bridge_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP_BRIDGE = "com.scamslayer.app.ACTION_STOP_BRIDGE"

        private const val SAMPLE_RATE = 8000
        private const val FRAME_SIZE_SAMPLES = 160  // 20ms at 8kHz
        private const val PCM_FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2  // 16-bit = 2 bytes per sample
        private const val MULAW_FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES  // 1 byte per sample

        @Volatile
        var isRunning = false
            private set

        // Mulaw compress table
        private val MU_LAW_COMPRESS_TABLE = intArrayOf(
            0,0,1,1,2,2,2,2,3,3,3,3,3,3,3,3,
            4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
            5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
            5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
            6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
            6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
            6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
            6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7
        )

        // Mulaw decode table (precomputed for all 256 byte values)
        private val MU_LAW_DECODE_TABLE = ShortArray(256).also { table ->
            for (i in 0 until 256) {
                val mu = i.inv() and 0xFF
                val sign = mu and 0x80
                val exponent = (mu shr 4) and 0x07
                val mantissa = mu and 0x0F
                var sample = ((mantissa shl 4) + 0x08) shl exponent
                sample -= 0x84
                if (sign != 0) sample = -sample
                table[i] = sample.toShort()
            }
        }

        fun pcm16ToMulaw(pcm: Short): Byte {
            val BIAS = 0x84
            val MAX = 32635
            var sample = pcm.toInt()
            val sign = (sample shr 8) and 0x80
            if (sign != 0) sample = -sample
            if (sample > MAX) sample = MAX
            sample += BIAS

            val exponent = MU_LAW_COMPRESS_TABLE[(sample shr 7) and 0xFF]
            val mantissa = (sample shr (exponent + 3)) and 0x0F
            val mulaw = (sign or (exponent shl 4) or mantissa).inv()
            return mulaw.toByte()
        }

        fun mulawToPcm16(mulaw: Byte): Short {
            return MU_LAW_DECODE_TABLE[mulaw.toInt() and 0xFF]
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var recordJob: Job? = null
    private val active = AtomicBoolean(false)

    private var callerNumber: String = "Unknown"
    private var personaId: String = "babicka_bozena"
    private var backendUrl: String = ""

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphone: Boolean = false

    // Call state monitoring — auto-stop when phone call ends
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_BRIDGE) {
            stopBridge()
            return START_NOT_STICKY
        }

        callerNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER) ?: "Unknown"
        personaId = intent?.getStringExtra(EXTRA_PERSONA_ID) ?: "babicka_bozena"
        backendUrl = intent?.getStringExtra(EXTRA_BACKEND_URL) ?: ""

        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        active.set(true)

        registerCallStateListener()
        setupAudioMode()
        setupAudioTrack()
        connectWebSocket()

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bridge_notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.bridge_notification_text)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, AudioBridgeService::class.java).apply {
                action = ACTION_STOP_BRIDGE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bridge_notification_title))
            .setContentText(getString(R.string.bridge_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.bridge_notification_stop),
                stopIntent
            )
            .build()
    }

    private fun setupAudioMode() {
        audioManager?.let { am ->
            previousAudioMode = am.mode
            @Suppress("DEPRECATION")
            previousSpeakerphone = am.isSpeakerphoneOn

            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
        }
    }

    private fun restoreAudioMode() {
        audioManager?.let { am ->
            am.mode = previousAudioMode
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = previousSpeakerphone
        }
    }

    @Suppress("MissingPermission")
    private fun setupAudioRecord(): AudioRecord? {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "AudioRecord.getMinBufferSize failed: $minBufferSize")
            return null
        }

        val bufferSize = maxOf(minBufferSize, PCM_FRAME_SIZE_BYTES * 4)

        return try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).also {
                if (it.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    it.release()
                    return null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            null
        }
    }

    private fun setupAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, PCM_FRAME_SIZE_BYTES * 4)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.play()
    }

    private fun connectWebSocket() {
        val wsUrl = buildWebSocketUrl()
        Log.i(TAG, "Connecting WebSocket to: $wsUrl")

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // no read timeout for WS
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                sendStartMessage(webSocket)
                startRecording()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                if (active.get()) {
                    stopBridge()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
                if (active.get()) {
                    stopBridge()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                if (active.get()) {
                    stopBridge()
                }
            }
        })
    }

    private fun buildWebSocketUrl(): String {
        val baseUrl = backendUrl.trimEnd('/')
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        return "$wsBase/ws/call"
    }

    private fun sendStartMessage(ws: WebSocket) {
        val startMsg = JSONObject().apply {
            put("event", "start")
            put("start", JSONObject().apply {
                put("persona", personaId)
                put("callerNumber", callerNumber)
            })
        }
        ws.send(startMsg.toString())
        Log.i(TAG, "Sent start message: persona=$personaId, caller=$callerNumber")
    }

    private fun startRecording() {
        audioRecord = setupAudioRecord()
        if (audioRecord == null) {
            Log.e(TAG, "Cannot start recording - AudioRecord setup failed")
            stopBridge()
            return
        }

        audioRecord?.startRecording()
        Log.i(TAG, "AudioRecord started")

        recordJob = serviceScope.launch(Dispatchers.IO) {
            val pcmBuffer = ShortArray(FRAME_SIZE_SAMPLES)
            val mulawBuffer = ByteArray(MULAW_FRAME_SIZE_BYTES)

            while (isActive && active.get()) {
                val shortsRead = audioRecord?.read(pcmBuffer, 0, FRAME_SIZE_SAMPLES)
                    ?: break

                if (shortsRead <= 0) {
                    if (shortsRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "AudioRecord read error: ERROR_INVALID_OPERATION")
                        break
                    }
                    if (shortsRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord read error: ERROR_BAD_VALUE")
                        break
                    }
                    continue
                }

                // Convert PCM16 to mulaw
                for (i in 0 until shortsRead) {
                    mulawBuffer[i] = pcm16ToMulaw(pcmBuffer[i])
                }

                // Base64 encode and send via WebSocket
                val encoded = Base64.encodeToString(
                    mulawBuffer, 0, shortsRead,
                    Base64.NO_WRAP
                )

                val msg = JSONObject().apply {
                    put("event", "media")
                    put("media", JSONObject().apply {
                        put("payload", encoded)
                    })
                }

                try {
                    webSocket?.send(msg.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send audio frame", e)
                    break
                }
            }

            Log.i(TAG, "Recording loop ended")
        }
    }

    private fun handleWebSocketMessage(text: String) {
        try {
            val msg = JSONObject(text)
            val event = msg.optString("event", "")

            when (event) {
                "media" -> {
                    val media = msg.optJSONObject("media") ?: return
                    val payload = media.optString("payload", "")
                    if (payload.isNotEmpty()) {
                        playMulawAudio(payload)
                    }
                }
                "stop" -> {
                    Log.i(TAG, "Received stop event from backend")
                    stopBridge()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket message", e)
        }
    }

    private fun playMulawAudio(base64Payload: String) {
        try {
            val mulawBytes = Base64.decode(base64Payload, Base64.NO_WRAP)
            val pcmSamples = ShortArray(mulawBytes.size)

            for (i in mulawBytes.indices) {
                pcmSamples[i] = mulawToPcm16(mulawBytes[i])
            }

            // Convert ShortArray to ByteArray (little-endian PCM16)
            val pcmBytes = ByteArray(pcmSamples.size * 2)
            for (i in pcmSamples.indices) {
                val sample = pcmSamples[i].toInt()
                pcmBytes[i * 2] = (sample and 0xFF).toByte()
                pcmBytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }

            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing mulaw audio", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun registerCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_IDLE && active.get()) {
                        Log.i(TAG, "Phone call ended (IDLE) — stopping bridge")
                        stopBridge()
                    }
                }
            }
            telephonyCallback = callback
            telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
        } else {
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_IDLE && active.get()) {
                        Log.i(TAG, "Phone call ended (IDLE) — stopping bridge")
                        stopBridge()
                    }
                }
            }
            phoneStateListener = listener
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
        Log.i(TAG, "Call state listener registered")
    }

    @Suppress("DEPRECATION")
    private fun unregisterCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                telephonyManager?.unregisterTelephonyCallback(it)
            }
        } else {
            phoneStateListener?.let {
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
        telephonyCallback = null
        phoneStateListener = null
    }

    fun stopBridge() {
        if (!active.compareAndSet(true, false)) {
            return // already stopping
        }

        Log.i(TAG, "Stopping audio bridge")

        // Unregister call state listener
        unregisterCallStateListener()

        // Send stop event to backend
        try {
            val stopMsg = JSONObject().apply {
                put("event", "stop")
            }
            webSocket?.send(stopMsg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending stop message", e)
        }

        // Cancel recording coroutine
        recordJob?.cancel()
        recordJob = null

        // Stop and release AudioRecord
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        // Stop and release AudioTrack
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack", e)
        }
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null

        // Close WebSocket
        try {
            webSocket?.close(1000, "Bridge stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        }
        webSocket = null

        // End the phone call
        endPhoneCall()

        // Restore audio mode
        restoreAudioMode()

        isRunning = false
        stopSelf()
    }

    @Suppress("MissingPermission")
    private fun endPhoneCall() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
                Log.i(TAG, "Phone call ended via TelecomManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end phone call", e)
        }
    }

    override fun onDestroy() {
        if (active.get()) {
            stopBridge()
        }
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }
}
