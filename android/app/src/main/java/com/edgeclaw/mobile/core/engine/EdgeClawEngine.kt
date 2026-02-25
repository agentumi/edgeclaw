package com.edgeclaw.mobile.core.engine

import com.edgeclaw.mobile.core.model.*
import com.edgeclaw.mobile.core.crypto.CryptoEngine
import com.edgeclaw.mobile.core.policy.PolicyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * EdgeClaw Core Engine — Kotlin-side orchestrator.
 *
 * This mirrors the Rust EdgeClawEngine API, providing a pure-Kotlin
 * fallback until Rust JNI/UniFFI bindings are integrated.
 */
class EdgeClawEngine private constructor(
    val config: EngineConfig
) {
    private val cryptoEngine = CryptoEngine()
    private val policyEngine = PolicyEngine()

    private val _identity = MutableStateFlow<DeviceIdentity?>(null)
    val identity: StateFlow<DeviceIdentity?> = _identity.asStateFlow()

    private val peers = ConcurrentHashMap<String, PeerInfo>()
    private val _peerList = MutableStateFlow<List<PeerInfo>>(emptyList())
    val peerList: StateFlow<List<PeerInfo>> = _peerList.asStateFlow()

    private val sessions = ConcurrentHashMap<String, SessionInfo>()

    // ─── Identity ───

    fun generateIdentity(): DeviceIdentity {
        val id = cryptoEngine.generateIdentity()
        _identity.value = id
        return id
    }

    fun getIdentity(): DeviceIdentity? = _identity.value

    // ─── Peers ───

    fun addPeer(peer: PeerInfo): PeerInfo {
        peers[peer.peerId] = peer
        refreshPeerList()
        return peer
    }

    fun removePeer(peerId: String): Boolean {
        val removed = peers.remove(peerId)
        refreshPeerList()
        return removed != null
    }

    fun getPeers(): List<PeerInfo> = peers.values.toList()

    fun getPeer(peerId: String): PeerInfo? = peers[peerId]

    private fun refreshPeerList() {
        _peerList.value = peers.values.toList()
    }

    // ─── Sessions ───

    fun createSession(peerId: String, peerPublicKeyHex: String): SessionInfo {
        val session = cryptoEngine.createSession(peerId, peerPublicKeyHex)
        sessions[session.sessionId] = session
        return session
    }

    fun encryptMessage(sessionId: String, plaintext: ByteArray): ByteArray {
        return cryptoEngine.encrypt(sessionId, plaintext)
    }

    fun decryptMessage(sessionId: String, ciphertext: ByteArray): ByteArray {
        return cryptoEngine.decrypt(sessionId, ciphertext)
    }

    fun getSession(sessionId: String): SessionInfo? = sessions[sessionId]

    // ─── Policy ───

    fun evaluateCapability(capabilityName: String, role: Role): PolicyDecision {
        return policyEngine.evaluate(capabilityName, role)
    }

    // ─── Protocol ───

    fun createEcm(): String {
        val id = _identity.value ?: throw IllegalStateException("Identity not generated")
        return """
        {
            "device_id": "${id.deviceId}",
            "device_type": "${config.deviceType}",
            "capabilities": ["status", "file_read", "heartbeat"],
            "os": "android",
            "version": "1.0.0"
        }
        """.trimIndent()
    }

    fun createHeartbeat(uptimeSecs: Long, cpuUsage: Double, memoryUsage: Double): String {
        val id = _identity.value ?: throw IllegalStateException("Identity not generated")
        val activeSessions = sessions.values.count { it.state == SessionState.ESTABLISHED }
        return """
        {
            "device_id": "${id.deviceId}",
            "uptime_secs": $uptimeSecs,
            "cpu_usage": $cpuUsage,
            "memory_usage": $memoryUsage,
            "active_sessions": $activeSessions
        }
        """.trimIndent()
    }

    companion object {
        @Volatile
        private var instance: EdgeClawEngine? = null

        fun create(config: EngineConfig = EngineConfig()): EdgeClawEngine {
            return EdgeClawEngine(config).also { instance = it }
        }

        fun getInstance(): EdgeClawEngine {
            return instance ?: throw IllegalStateException("Engine not initialized")
        }
    }
}
