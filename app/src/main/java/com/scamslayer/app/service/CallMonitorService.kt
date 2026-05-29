package com.scamslayer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.scamslayer.app.MainActivity
import com.scamslayer.app.R
import com.scamslayer.app.data.SettingsRepository
import com.scamslayer.app.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that monitors incoming calls.
 * When the user is on a call, the notification shows a "CALL AI" button
 * that dials the Twilio number to conference the scammer with the AI.
 */
class CallMonitorService : Service() {

    companion object {
        private const val TAG = "CallMonitorService"
        const val CHANNEL_ID = "scamslayer_monitor"
        const val OVERLAY_CHANNEL_ID = "scamslayer_overlay"
        const val NOTIFICATION_ID = 3001
        const val ACTION_STOP_MONITORING = "com.scamslayer.app.STOP_MONITORING"
        const val RECORDING_CHANNEL_ID = "scamslayer_recording"

        private val _recordingsUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val recordingsUpdated = _recordingsUpdated.asSharedFlow()

        @Volatile
        var isMonitoring = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var telephonyManager: TelephonyManager? = null
    private var currentCallerNumber: String = "Unknown"

    // Phone state tracking
    private var lastCallState = TelephonyManager.CALL_STATE_IDLE
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null
    private var callWasRejected = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createMonitoringNotification())
        isMonitoring = true
        registerPhoneStateListener()
        Log.i(TAG, "CallMonitorService started — listening for incoming calls")

        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                "Call Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ScamSlayer is monitoring incoming calls"
            }

            val overlayChannel = NotificationChannel(
                OVERLAY_CHANNEL_ID,
                "Incoming Call Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alert when an incoming call is detected"
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }

            val recordingChannel = NotificationChannel(
                RECORDING_CHANNEL_ID,
                "Recording Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notification when a scam call recording is ready"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(monitorChannel)
            nm.createNotificationChannel(overlayChannel)
            nm.createNotificationChannel(recordingChannel)
        }
    }

    private fun createMonitoringNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, CallMonitorService::class.java).apply { action = ACTION_STOP_MONITORING },
            PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScamSlayer Active")
            .setContentText("Monitoring incoming calls")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }


    // =========================================================
    //  PHONE STATE LISTENING
    // =========================================================

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChanged(state)
                }
            }
            telephonyCallback = callback
            telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
            Log.i(TAG, "Registered TelephonyCallback (API 31+)")
        } else {
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    currentCallerNumber = phoneNumber ?: "Unknown"
                    handleCallStateChanged(state)
                }
            }
            phoneStateListener = listener
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.i(TAG, "Registered PhoneStateListener (legacy)")
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                telephonyManager?.unregisterTelephonyCallback(it)
            }
        } else {
            phoneStateListener?.let {
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun handleCallStateChanged(state: Int) {
        Log.i(TAG, "Call state changed: $state (last: $lastCallState) rejected=$callWasRejected")

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.i(TAG, "Incoming call from: $currentCallerNumber")
                callWasRejected = true
                preparePersonaForForwarding()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.i(TAG, "Call answered — not a rejection")
                callWasRejected = false
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (callWasRejected) {
                    Log.i(TAG, "Call was rejected/missed — polling for new recording")
                    pollForNewRecording()
                } else {
                    Log.d(TAG, "Call ended normally")
                }
                callWasRejected = false
            }
        }

        lastCallState = state
    }

    /**
     * Fire-and-forget: tell backend which persona to use.
     * When the call is forwarded (unanswered) to Twilio, the webhook
     * picks up the prepared persona.
     */
    private fun preparePersonaForForwarding() {
        serviceScope.launch {
            try {
                val settings = SettingsRepository.getInstance(applicationContext)
                val personaId = settings.getSelectedPersonaIdSync().ifBlank { "babicka_bozena" }
                val userNumber = settings.getUserPhoneNumberSync()
                val backendUrl = settings.getBackendUrlSync()
                val body = mutableMapOf("persona_id" to personaId)
                if (userNumber.isNotBlank()) {
                    body["user_number"] = userNumber
                }
                ApiClient.getService(backendUrl).prepareBridgeCall(body)
                Log.i(TAG, "Prepared persona $personaId for user $userNumber")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to prepare persona: ${e.message}")
            }
        }
    }

    /**
     * After a rejected call, poll the backend for a new recording.
     * The AI call typically lasts 30-120s, so we check periodically.
     */
    private fun pollForNewRecording() {
        serviceScope.launch {
            try {
                val settings = SettingsRepository.getInstance(applicationContext)
                val backendUrl = settings.getBackendUrlSync()
                val api = ApiClient.getService(backendUrl)

                // Get current recording count (unfiltered — just detect any new recording)
                val before = try {
                    api.getRecordings().size
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get initial recording count: ${e.message}")
                    0
                }
                Log.i(TAG, "Recording count before: $before — polling for new ones")

                // Poll every 15s for up to 3 minutes
                repeat(12) { attempt ->
                    delay(15_000)
                    try {
                        val current = api.getRecordings()
                        Log.d(TAG, "Poll #${attempt + 1}: count=${current.size} (was $before)")
                        if (current.size > before) {
                            val newest = current.first()
                            Log.i(TAG, "New recording detected: ${newest.id} — ${newest.title}")
                            showRecordingNotification(newest.title ?: newest.persona)
                            _recordingsUpdated.tryEmit(Unit)
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Poll #${attempt + 1} failed: ${e.message}")
                    }
                }
                Log.i(TAG, "Polling ended — no new recording found")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start polling: ${e.message}")
            }
        }
    }

    private fun showRecordingNotification(title: String) {
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, RECORDING_CHANNEL_ID)
            .setContentTitle("Recording Ready")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    // =========================================================
    //  CLEANUP
    // =========================================================

    override fun onDestroy() {
        Log.i(TAG, "CallMonitorService stopping")
        unregisterPhoneStateListener()
        isMonitoring = false
        super.onDestroy()
    }
}
