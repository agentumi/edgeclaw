package com.edgeclaw.mobile.core.model

import kotlinx.serialization.Serializable

/**
 * Device identity — maps to Rust DeviceIdentity
 */
@Serializable
data class DeviceIdentity(
    val deviceId: String,
    val publicKeyHex: String,
    val fingerprint: String,
    val createdAt: String
)

/**
 * Discovered peer information
 */
@Serializable
data class PeerInfo(
    val peerId: String,
    val deviceName: String,
    val deviceType: String,
    val address: String,
    val capabilities: List<String>,
    val lastSeen: String,
    val isConnected: Boolean = false,
    val rssi: Int = 0,
    val transport: Transport = Transport.BLE
)

/**
 * Transport type for peer connection
 */
@Serializable
enum class Transport {
    BLE, WIFI, QUIC
}

/**
 * Encrypted session information
 */
@Serializable
data class SessionInfo(
    val sessionId: String,
    val peerId: String,
    val state: SessionState,
    val createdAt: String,
    val expiresAt: String,
    val messagesSent: Long = 0,
    val messagesReceived: Long = 0
)

/**
 * Session state
 */
@Serializable
enum class SessionState {
    INITIATING, ESTABLISHED, EXPIRED
}

/**
 * Policy evaluation decision
 */
@Serializable
data class PolicyDecision(
    val allowed: Boolean,
    val reason: String,
    val riskLevel: Int
)

/**
 * Engine configuration
 */
@Serializable
data class EngineConfig(
    val deviceName: String = "edgeclaw-mobile",
    val deviceType: String = "smartphone",
    val listenPort: Int = 8443,
    val maxConnections: Int = 16,
    val quicEnabled: Boolean = false,
    val logLevel: String = "info"
)

/**
 * ECNP message types
 */
enum class MessageType(val code: Byte) {
    HANDSHAKE(0x01),
    DATA(0x02),
    CONTROL(0x03),
    HEARTBEAT(0x04),
    ACK(0x05),
    ERROR(0x06);

    companion object {
        fun fromCode(code: Byte): MessageType? =
            entries.find { it.code == code }
    }
}

/**
 * Capability risk levels
 */
enum class RiskLevel(val level: Int) {
    NONE(0),     // Passive read-only
    LOW(1),      // Local data access
    MEDIUM(2),   // State modification
    HIGH(3);     // System-level operations

    companion object {
        fun fromLevel(level: Int): RiskLevel =
            entries.find { it.level == level } ?: HIGH
    }
}

/**
 * User role for RBAC
 */
enum class Role(val maxRisk: RiskLevel) {
    VIEWER(RiskLevel.NONE),
    OPERATOR(RiskLevel.LOW),
    ADMIN(RiskLevel.MEDIUM),
    OWNER(RiskLevel.HIGH);

    companion object {
        fun fromString(s: String): Role? =
            entries.find { it.name.equals(s, ignoreCase = true) }
    }
}
