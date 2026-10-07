package com.nico2.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper

internal object VoiceSessionNotification {
    const val ACTION_OPEN = "com.nico2.app.voice.OPEN"
    const val ACTION_TOGGLE_MIC = "com.nico2.app.voice.TOGGLE_MIC"
    const val ACTION_END = "com.nico2.app.voice.END"

    private const val CHANNEL_ID = "active_voice_session"
    private const val NOTIFICATION_ID = 4201

    fun canShow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || manager.areNotificationsEnabled()) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                manager.getNotificationChannel(CHANNEL_ID)?.importance !=
                NotificationManager.IMPORTANCE_NONE)
    }

    fun show(
        context: Context,
        status: String,
        muteLabel: String,
        endLabel: String,
    ): Boolean {
        if (!canShow(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.voice_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_LOW)
        }
        val openIntent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.voice_notification_title))
            .setContentText(status)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_btn_speak_now),
                    muteLabel,
                    actionPendingIntent(context, ACTION_TOGGLE_MIC, 1),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    endLabel,
                    actionPendingIntent(context, ACTION_END, 2),
                ).build(),
            )
        return try {
            manager.notify(NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, VoiceNotificationActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

internal object VoiceNotificationCommands {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var activeSessionId: String? = null
    @Volatile private var handler: ((String) -> Unit)? = null

    fun register(sessionId: String, actionHandler: (String) -> Unit) {
        activeSessionId = sessionId
        handler = actionHandler
    }

    fun unregister(sessionId: String) {
        if (activeSessionId == sessionId) {
            activeSessionId = null
            handler = null
        }
    }

    fun dispatch(action: String?) {
        val sessionId = activeSessionId ?: return
        val currentHandler = handler ?: return
        mainHandler.post {
            if (activeSessionId == sessionId) currentHandler(action.orEmpty())
        }
    }
}

class VoiceNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VoiceNotificationCommands.dispatch(intent.action)
    }
}
