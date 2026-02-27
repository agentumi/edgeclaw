package com.edgeclaw.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.edgeclaw.mobile.R
import com.edgeclaw.mobile.ui.MainActivity

/**
 * FCM-compatible push notification handler for Desktop status alerts.
 *
 * Receives push messages from the Desktop agent (via FCM or local relay)
 * and displays system status notifications.
 *
 * Note: Full FCM integration requires google-services.json and Firebase SDK.
 * This implementation provides the local notification logic used by both
 * FCM push and the SyncService's status push.
 */
class PushNotificationHandler(
    private val context: android.content.Context
) {
    companion object {
        const val CHANNEL_ID = "edgeclaw_push"
        const val NOTIFICATION_BASE_ID = 3000
        private var notificationCounter = 0
    }

    init {
        createNotificationChannel()
    }

    /**
     * Display a Desktop status notification.
     */
    fun showStatusNotification(
        title: String,
        cpuUsage: Double,
        memoryUsage: Double,
        aiStatus: String
    ) {
        val text = "CPU: ${"%.1f".format(cpuUsage)}% | RAM: ${"%.1f".format(memoryUsage)}% | AI: $aiStatus"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_BASE_ID + notificationCounter++, notification)
    }

    /**
     * Display an alert notification (e.g., high CPU, agent offline).
     */
    fun showAlertNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_BASE_ID + notificationCounter++, notification)
    }

    /**
     * Display a remote execution result notification.
     */
    fun showExecResultNotification(command: String, exitCode: Int, output: String) {
        val statusIcon = if (exitCode == 0) "✅" else "❌"
        val title = "$statusIcon $command (exit: $exitCode)"
        val text = output.take(200)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(output.take(500)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_BASE_ID + notificationCounter++, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EdgeClaw Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Desktop agent status and alert notifications"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
