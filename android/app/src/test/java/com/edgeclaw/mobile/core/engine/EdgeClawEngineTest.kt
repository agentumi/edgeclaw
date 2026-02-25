package com.edgeclaw.mobile.core.engine

import com.edgeclaw.mobile.core.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EdgeClawEngine
 */
class EdgeClawEngineTest {

    private lateinit var engine: EdgeClawEngine

    @Before
    fun setup() {
        engine = EdgeClawEngine.create(EngineConfig(
            deviceName = "test-device",
            deviceType = "smartphone"
        ))
    }

    @Test
    fun `generate identity creates valid identity`() {
        val identity = engine.generateIdentity()

        assertNotNull(identity)
        assertTrue(identity.deviceId.isNotEmpty())
        assertTrue(identity.publicKeyHex.isNotEmpty())
        assertTrue(identity.fingerprint.isNotEmpty())
        assertTrue(identity.createdAt.isNotEmpty())
    }

    @Test
    fun `get identity returns generated identity`() {
        val generated = engine.generateIdentity()
        val retrieved = engine.getIdentity()

        assertNotNull(retrieved)
        assertEquals(generated.deviceId, retrieved?.deviceId)
    }

    @Test
    fun `add and remove peer works`() {
        val peer = PeerInfo(
            peerId = "peer-001",
            deviceName = "test-pc",
            deviceType = "pc",
            address = "192.168.1.10",
            capabilities = listOf("file_read"),
            lastSeen = "2024-01-01T00:00:00Z"
        )

        engine.addPeer(peer)
        assertEquals(1, engine.getPeers().size)

        val result = engine.removePeer("peer-001")
        assertTrue(result)
        assertEquals(0, engine.getPeers().size)
    }

    @Test
    fun `remove nonexistent peer returns false`() {
        assertFalse(engine.removePeer("nobody"))
    }

    @Test
    fun `get peer returns correct peer`() {
        val peer = PeerInfo(
            peerId = "peer-002",
            deviceName = "test-tablet",
            deviceType = "tablet",
            address = "10.0.0.1",
            capabilities = emptyList(),
            lastSeen = "2024-01-01T00:00:00Z"
        )

        engine.addPeer(peer)
        val retrieved = engine.getPeer("peer-002")

        assertNotNull(retrieved)
        assertEquals("test-tablet", retrieved?.deviceName)
    }

    @Test
    fun `create session succeeds`() {
        engine.generateIdentity()
        val session = engine.createSession("peer-001", "0".repeat(64))

        assertNotNull(session)
        assertEquals("peer-001", session.peerId)
        assertEquals(SessionState.ESTABLISHED, session.state)
    }

    @Test
    fun `evaluate viewer cannot shell exec`() {
        val decision = engine.evaluateCapability("shell_exec", Role.VIEWER)
        assertFalse(decision.allowed)
        assertEquals(3, decision.riskLevel)
    }

    @Test
    fun `evaluate owner can shell exec`() {
        val decision = engine.evaluateCapability("shell_exec", Role.OWNER)
        assertTrue(decision.allowed)
    }

    @Test
    fun `evaluate unknown capability is denied`() {
        val decision = engine.evaluateCapability("launch_missiles", Role.OWNER)
        assertFalse(decision.allowed)
    }

    @Test
    fun `create ECM requires identity`() {
        engine.generateIdentity()
        val ecm = engine.createEcm()
        assertTrue(ecm.contains("device_id"))
        assertTrue(ecm.contains("capabilities"))
    }

    @Test
    fun `create heartbeat requires identity`() {
        engine.generateIdentity()
        val hb = engine.createHeartbeat(3600, 25.0, 40.0)
        assertTrue(hb.contains("uptime_secs"))
        assertTrue(hb.contains("3600"))
    }
}
