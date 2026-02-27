package com.edgeclaw.mobile.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.edgeclaw.mobile.R
import com.edgeclaw.mobile.core.model.SyncClientConfig
import com.edgeclaw.mobile.core.model.SyncConnectionState
import com.edgeclaw.mobile.core.sync.SyncManager
import com.edgeclaw.mobile.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service for maintaining Desktop-Mobile synchronization.
 *
 * Runs in the background to:
 * - Maintain TCP connection to Desktop agent
 * - Receive config sync and status push messages
 * - Handle remote execution requests
 * - Auto-reconnect on disconnect
 */
class SyncService : Service() {

    companion object {
        const val CHANNEL_ID = "edgeclaw_sync"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_SYNC = "com.edgeclaw.mobile.START_SYNC"
        const val ACTION_STOP_SYNC = "com.edgeclaw.mobile.STOP_SYNC"
        const val EXTRA_DESKTOP_ADDRESS = "desktop_address"

        // Broadcast actions for UI updates
        const val BROADCAST_STATUS_UPDATE = "com.edgeclaw.mobile.STATUS_UPDATE"
        const val BROADCAST_CONFIG_UPDATE = "com.edgeclaw.mobile.CONFIG_UPDATE"
        const val BROADCAST_EXEC_RESULT = "com.edgeclaw.mobile.EXEC_RESULT"
    }

    private var syncManager: SyncManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SYNC -> {
                stopSync()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_SYNC, null -> {
                val address = intent?.getStringExtra(EXTRA_DESKTOP_ADDRESS)
                    ?: "192.168.1.100:8443"
                startSync(address)
            }
        }

        val notification = createNotification("Connecting to Desktop agent...")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopSync()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSync(address: String) {
        val config = SyncClientConfig(
            desktopAddress = address,
            autoReconnect = true
        )
        syncManager = SyncManager(config)

        // Monitor connection state
        serviceScope.launch {
            syncManager?.connectionState?.collectLatest { state ->
                val statusText = when (state) {
                    SyncConnectionState.DISCONNECTED -> "Disconnected"
                    SyncConnectionState.CONNECTING -> "Connecting..."
                    SyncConnectionState.HANDSHAKING -> "Handshaking..."
                    SyncConnectionState.CONNECTED -> "Connected to Desktop"
                    SyncConnectionState.SYNCING -> "Syncing..."
                    SyncConnectionState.ERROR -> "Connection error"
                }
                updateNotification(statusText)
            }
        }

        // Monitor status pushes and broadcast to UI
        serviceScope.launch {
            syncManager?.lastStatus?.collectLatest { status ->
                if (status != null) {
                    val intent = Intent(BROADCAST_STATUS_UPDATE).apply {
                        putExtra("cpu_usage", status.cpuUsage)
                        putExtra("memory_usage", status.memoryUsage)
                        putExtra("disk_usage", status.diskUsage)
                        putExtra("uptime_secs", status.uptimeSecs)
                        putExtra("ai_status", status.aiStatus)
                    }
                    sendBroadcast(intent)
                }
            }
        }

        // Monitor config sync
        serviceScope.launch {
            syncManager?.lastConfig?.collectLatest { config ->
                if (config != null) {
                    val intent = Intent(BROADCAST_CONFIG_UPDATE).apply {
                        putExtra("config_hash", config.configHash)
                        putExtra("config_data", config.configData)
                    }
                    sendBroadcast(intent)
                }
            }
        }

        // Monitor exec results
        serviceScope.launch {
            syncManager?.lastExecResult?.collectLatest { result ->
                if (result != null) {
                    val intent = Intent(BROADCAST_EXEC_RESULT).apply {
                        putExtra("command", result.command)
                        putExtra("exit_code", result.exitCode)
                        putExtra("stdout", result.stdout)
                        putExtra("stderr", result.stderr)
                    }
                    sendBroadcast(intent)

                    // Show notification for exec result
                    showExecResultNotification(result)
                }
            }
        }

        // Initiate connection
        serviceScope.launch {
            syncManager?.connect(address)
        }
    }

    private fun stopSync() {
        syncManager?.shutdown()
        syncManager = null
    }

    /** Expose SyncManager for remote exec from UI */
    fun getSyncManager(): SyncManager? = syncManager

    private fun showExecResultNotification(
        result: com.edgeclaw.mobile.core.model.SyncMessage.RemoteExecResult
    ) {
        val icon = if (result.exitCode == 0) "✅" else "❌"
        val text = "$icon ${result.command}: ${result.stdout.take(100)}"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Execution Result")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EdgeClaw Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Desktop-Mobile synchronization service"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SyncService::class.java).apply { action = ACTION_STOP_SYNC },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EdgeClaw Sync")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
