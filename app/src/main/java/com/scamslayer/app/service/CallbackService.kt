package com.scamslayer.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.scamslayer.app.data.SettingsRepository
import com.scamslayer.app.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Detects rejected/missed calls and offers to call back with AI.
 * Triggered by PHONE_STATE broadcast when callback mode is enabled.
 */
class CallbackReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallbackReceiver"
        private const val CHANNEL_ID = "scamslayer_callback"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var incomingNumber: String? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                lastState = TelephonyManager.CALL_STATE_RINGING
                Log.i(TAG, "Ringing from: $incomingNumber")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_RINGING && incomingNumber != null) {
                    // Call went from RINGING to IDLE = rejected or missed
                    Log.i(TAG, "Call rejected/missed from: $incomingNumber")
                    val number = incomingNumber!!
                    scope.launch {
                        handleRejectedCall(context, number)
                    }
                }
                lastState = TelephonyManager.CALL_STATE_IDLE
                incomingNumber = null
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call was answered — ignore
                lastState = TelephonyManager.CALL_STATE_OFFHOOK
            }
        }
    }

    private suspend fun handleRejectedCall(context: Context, callerNumber: String) {
        // Check if callback mode is enabled
        val settings = SettingsRepository.getInstance(context)
        val callbackEnabled = settings.getCallbackModeSync()
        if (!callbackEnabled) return

        Log.i(TAG, "Callback mode active — showing notification for $callerNumber")

        // Small delay to avoid race conditions
        delay(500)

        showCallbackNotification(context, callerNumber)
    }

    private fun showCallbackNotification(context: Context, callerNumber: String) {
        val nm = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "AI Callback",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Nabídka zavolat podvodníkovi zpět s AI"
                }
            )
        }

        // Intent to trigger callback
        val callbackIntent = Intent(context, CallbackActionReceiver::class.java).apply {
            action = "com.scamslayer.CALLBACK"
            putExtra("caller_number", callerNumber)
        }
        val callbackPending = PendingIntent.getBroadcast(
            context, callerNumber.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent to dismiss
        val dismissIntent = Intent(context, CallbackActionReceiver::class.java).apply {
            action = "com.scamslayer.DISMISS"
            putExtra("notification_id", callerNumber.hashCode())
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, callerNumber.hashCode() + 1,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Spam hovor odmítnut")
            .setContentText("Zavolat zpět na $callerNumber s AI personou?")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Zavolat s AI", callbackPending)
            .addAction(0, "Ignorovat", dismissPending)
            .build()

        nm.notify(callerNumber.hashCode(), notification)
    }
}

/**
 * Handles notification actions — triggers Twilio callback or dismisses.
 */
class CallbackActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallbackAction"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.scamslayer.CALLBACK" -> {
                val callerNumber = intent.getStringExtra("caller_number") ?: return
                Log.i(TAG, "Triggering callback to $callerNumber")

                // Dismiss notification
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(callerNumber.hashCode())

                scope.launch {
                    try {
                        val settings = SettingsRepository.getInstance(context)
                        val backendUrl = settings.getBackendUrlSync()
                        val personaId = settings.getSelectedPersonaIdSync()

                        ApiClient.getService(backendUrl).callbackScammer(
                            mapOf(
                                "scammer_number" to callerNumber,
                                "persona_id" to personaId.ifBlank { "babicka_bozena" }
                            )
                        )
                        Log.i(TAG, "Callback initiated to $callerNumber")
                    } catch (e: Exception) {
                        Log.e(TAG, "Callback failed: ${e.message}")
                    }
                }
            }
            "com.scamslayer.DISMISS" -> {
                val notificationId = intent.getIntExtra("notification_id", 0)
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(notificationId)
            }
        }
    }
}
