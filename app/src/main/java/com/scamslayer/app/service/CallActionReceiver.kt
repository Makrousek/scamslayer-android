package com.scamslayer.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallOverlayService.ACTION_FORWARD -> {
                val callerNumber = intent.getStringExtra(CallOverlayService.EXTRA_CALLER_NUMBER) ?: "Unknown"
                val serviceIntent = Intent(context, CallOverlayService::class.java).apply {
                    putExtra(CallOverlayService.EXTRA_CALLER_NUMBER, callerNumber)
                    putExtra("action", "forward")
                }
                // Stop the current overlay service — the forward action
                // is handled by the overlay service's button directly.
                // This receiver handles notification-based actions.
                val stopIntent = Intent(context, CallOverlayService::class.java)
                context.stopService(stopIntent)

                // Start a fresh service instance to handle the forward
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }

            CallOverlayService.ACTION_DISMISS -> {
                val stopIntent = Intent(context, CallOverlayService::class.java)
                context.stopService(stopIntent)
            }
        }
    }
}
