package com.edgeclaw.mobile.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sync message types for Desktop-Mobile communication.
 * Maps to Rust `SyncMessage` enum.
 */
@Serializable
sealed class SyncMessage {

    /** Desktop → Mobile: configuration update */
    @Serializable
    @SerialName("config_sync")
    data class ConfigSync(
        val configHash: String,
        val configData: String
    ) : SyncMessage()

    /** Mobile → Desktop: remote command execution request */
    @Serializable
    @SerialName("remote_exec")
    data class RemoteExec(
        val command: String,
        val args: List<String> = emptyList()
    ) : SyncMessage()

    /** Desktop → Mobile: system status push */
    @Serializable
    @SerialName("status_push")
    data class StatusPush(
        val cpuUsage: Double = 0.0,
        val memoryUsage: Double = 0.0,
        val diskUsage: Double = 0.0,
        val uptimeSecs: Long = 0,
        val activeSessions: Int = 0,
        val aiStatus: String = "unknown"
    ) : SyncMessage()

    /** Desktop → Mobile: remote execution result */
    @Serializable
    @SerialName("remote_exec_result")
    data class RemoteExecResult(
        val command: String,
        val exitCode: Int,
        val stdout: String = "",
        val stderr: String = ""
    ) : SyncMessage()
}

/**
 * Sync connection state
 */
enum class SyncConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    CONNECTED,
    SYNCING,
    ERROR
}

/**
 * Sync client configuration
 */
@Serializable
data class SyncClientConfig(
    val desktopAddress: String = "192.168.1.100:8443",
    val heartbeatIntervalSecs: Long = 30,
    val statusIntervalSecs: Long = 30,
    val connectTimeoutSecs: Long = 10,
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 0
)

/**
 * Sync runtime statistics
 */
data class SyncStats(
    val messagesSent: Long = 0,
    val messagesReceived: Long = 0,
    val reconnectCount: Int = 0,
    val lastConfigHash: String? = null,
    val lastStatusPush: SyncMessage.StatusPush? = null
)

/**
 * Device group for batch operations
 */
@Serializable
data class DeviceGroup(
    val groupId: String,
    val name: String,
    val description: String = "",
    val memberPeerIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Transport preference for Desktop connection
 */
enum class TransportPreference {
    BLE_FIRST,
    TCP_LAN,
    AUTO
}
