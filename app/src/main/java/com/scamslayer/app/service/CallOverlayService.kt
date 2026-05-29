package com.scamslayer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.telecom.TelecomManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.scamslayer.app.MainActivity
import com.scamslayer.app.R
import com.scamslayer.app.data.SettingsRepository
import com.scamslayer.app.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CallOverlayService : Service() {

    companion object {
        private const val TAG = "CallOverlayService"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val CHANNEL_ID = "scamslayer_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_FORWARD = "com.scamslayer.app.ACTION_FORWARD_TO_AI"
        const val ACTION_DISMISS = "com.scamslayer.app.ACTION_DISMISS"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var callerNumber: String = "Unknown"
    private var isAiActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        callerNumber = intent?.getStringExtra(EXTRA_CALLER_NUMBER) ?: "Unknown"

        try {
            startForeground(NOTIFICATION_ID, createNotification())
            showOverlay()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground or show overlay", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_desc)
                enableLights(true)
                lightColor = Color.RED
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

        val forwardIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(ACTION_FORWARD).apply {
                setPackage(packageName)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismissIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent(ACTION_DISMISS).apply {
                setPackage(packageName)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, callerNumber))
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_play,
                getString(R.string.notification_action_forward),
                forwardIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_dismiss),
                dismissIntent
            )
            .build()
    }

    @Suppress("DEPRECATION")
    private fun showOverlay() {
        if (overlayView != null) {
            removeOverlay()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6000000.toInt())
            setPadding(48, 36, 48, 36)
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.overlay_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val numberText = TextView(this).apply {
            text = callerNumber
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val personaText = TextView(this).apply {
            text = getString(R.string.no_persona_selected)
            setTextColor(0xFFFF6D00.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        serviceScope.launch {
            val settingsRepository = SettingsRepository.getInstance(applicationContext)
            val personaId = settingsRepository.selectedPersonaId.first()
            if (personaId.isNotBlank()) {
                try {
                    val backendUrl = settingsRepository.getBackendUrlSync()
                    val personas = ApiClient.getService(backendUrl).getPersonas()
                    val persona = personas.find { it.id == personaId }
                    persona?.let {
                        personaText.text = getString(R.string.persona_prefix, it.name)
                    }
                } catch (_: Exception) {
                    // Keep default text if API fails
                }
            }
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val forwardButton = Button(this).apply {
            text = getString(R.string.forward_to_ai)
            setBackgroundColor(0xFFD32F2F.toInt())
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                handleForwardToAi()
            }
        }

        val dismissButton = Button(this).apply {
            text = getString(R.string.dismiss_overlay)
            setBackgroundColor(0xFF424242.toInt())
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(32, 24, 32, 24)
            setOnClickListener {
                removeOverlay()
                stopSelf()
            }
        }

        val spacer = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 0)
        }

        buttonLayout.addView(forwardButton)
        buttonLayout.addView(spacer)
        buttonLayout.addView(dismissButton)

        layout.addView(titleText)
        layout.addView(numberText)
        layout.addView(personaText)
        layout.addView(buttonLayout)

        overlayView = layout

        try {
            windowManager?.addView(layout, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun showAiActiveOverlay() {
        if (overlayView != null) {
            removeOverlay()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6000000.toInt())
            setPadding(48, 36, 48, 36)
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.overlay_ai_active)
            setTextColor(0xFF4CAF50.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val subtitleText = TextView(this).apply {
            text = getString(R.string.overlay_tap_to_end)
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val stopButton = Button(this).apply {
            text = getString(R.string.bridge_notification_stop)
            setBackgroundColor(0xFFD32F2F.toInt())
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(48, 24, 48, 24)
            setOnClickListener {
                handleStopAi()
            }
        }

        layout.addView(titleText)
        layout.addView(subtitleText)
        layout.addView(stopButton)

        overlayView = layout

        try {
            windowManager?.addView(layout, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleForwardToAi() {
        serviceScope.launch {
            try {
                val settingsRepository = SettingsRepository.getInstance(applicationContext)
                val personaId = settingsRepository.getSelectedPersonaIdSync()
                val backendUrl = settingsRepository.getBackendUrlSync()

                // Answer the incoming call instead of rejecting it
                answerIncomingCall()

                // Start the AudioBridgeService to stream audio
                val bridgeIntent = Intent(this@CallOverlayService, AudioBridgeService::class.java).apply {
                    putExtra(AudioBridgeService.EXTRA_CALLER_NUMBER, callerNumber)
                    putExtra(AudioBridgeService.EXTRA_PERSONA_ID, personaId)
                    putExtra(AudioBridgeService.EXTRA_BACKEND_URL, backendUrl)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(bridgeIntent)
                } else {
                    startService(bridgeIntent)
                }

                isAiActive = true
                // Switch overlay to "AI Active" mode
                showAiActiveOverlay()

            } catch (e: Exception) {
                Log.e(TAG, "Error forwarding to AI", e)
                removeOverlay()
                stopSelf()
            }
        }
    }

    private fun handleStopAi() {
        // Stop the AudioBridgeService (which also ends the call)
        val stopIntent = Intent(this, AudioBridgeService::class.java).apply {
            action = AudioBridgeService.ACTION_STOP_BRIDGE
        }
        startService(stopIntent)

        isAiActive = false
        removeOverlay()
        stopSelf()
    }

    @Suppress("MissingPermission")
    private fun answerIncomingCall() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telecomManager.acceptRingingCall()
                Log.i(TAG, "Call answered via TelecomManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer call", e)
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // View might already be removed
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }
}
