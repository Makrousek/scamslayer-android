package com.scamslayer.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.scamslayer.app.MainActivity
import com.scamslayer.app.data.SettingsRepository
import com.scamslayer.app.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScamSlayerFCMService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "ScamSlayerFCM"
        private const val CHANNEL_ID = "scamslayer_recording"
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "New FCM token: $token")
        serviceScope.launch {
            sendTokenToBackend(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "FCM message received: ${message.data}")

        val event = message.data["event"] ?: return
        if (event == "recording_complete") {
            val title = message.data["title"] ?: "Nahrávka připravena"
            val persona = message.data["persona"] ?: ""
            val recordingId = message.data["recording_id"] ?: ""
            showRecordingNotification(title, persona, recordingId)
        }
    }

    private fun showRecordingNotification(title: String, persona: String, recordingId: String) {
        val nm = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Nahrávka dokončena",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Oznámení o dokončené nahrávce hovoru"
                }
            )
        }

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to", "recordings")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (persona.isNotBlank()) "$title ($persona)" else title

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScamSlayer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    private suspend fun sendTokenToBackend(token: String) {
        try {
            val settings = SettingsRepository.getInstance(applicationContext)
            val backendUrl = settings.getBackendUrlSync()
            val userNumber = settings.getUserPhoneNumberSync()
            if (backendUrl.isBlank()) return

            ApiClient.getService(backendUrl).registerFcmToken(
                mapOf("user_number" to userNumber, "fcm_token" to token)
            )
            Log.i(TAG, "FCM token sent to backend")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send FCM token: ${e.message}")
        }
    }
}
