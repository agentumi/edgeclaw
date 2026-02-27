package com.edgeclaw.mobile.core.sync

import com.edgeclaw.mobile.core.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SyncManager.
 */
class SyncManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ─── SyncMessage serialization tests ───

    @Test
    fun `test ConfigSync serialization roundtrip`() {
        val msg = SyncMessage.ConfigSync(
            configHash = "sha256:abc123",
            configData = """{"agent":{"name":"test"}}"""
        )
        val jsonStr = json.encodeToString(SyncMessage.serializer(), msg)
        val decoded = json.decodeFromString(SyncMessage.serializer(), jsonStr)

        assertTrue(decoded is SyncMessage.ConfigSync)
        val cs = decoded as SyncMessage.ConfigSync
        assertEquals("sha256:abc123", cs.configHash)
        assertEquals("""{"agent":{"name":"test"}}""", cs.configData)
    }

    @Test
    fun `test RemoteExec serialization roundtrip`() {
        val msg = SyncMessage.RemoteExec(
            command = "systemctl",
            args = listOf("status", "nginx")
        )
        val jsonStr = json.encodeToString(SyncMessage.serializer(), msg)
        val decoded = json.decodeFromString(SyncMessage.serializer(), jsonStr)

        assertTrue(decoded is SyncMessage.RemoteExec)
        val re = decoded as SyncMessage.RemoteExec
        assertEquals("systemctl", re.command)
        assertEquals(listOf("status", "nginx"), re.args)
    }

    @Test
    fun `test StatusPush serialization roundtrip`() {
        val msg = SyncMessage.StatusPush(
            cpuUsage = 45.5,
            memoryUsage = 60.0,
            diskUsage = 72.3,
            uptimeSecs = 86400,
            activeSessions = 3,
            aiStatus = "ollama:ready"
        )
        val jsonStr = json.encodeToString(SyncMessage.serializer(), msg)
        val decoded = json.decodeFromString(SyncMessage.serializer(), jsonStr)

        assertTrue(decoded is SyncMessage.StatusPush)
        val sp = decoded as SyncMessage.StatusPush
        assertEquals(45.5, sp.cpuUsage, 0.001)
        assertEquals(86400L, sp.uptimeSecs)
        assertEquals("ollama:ready", sp.aiStatus)
    }

    @Test
    fun `test RemoteExecResult serialization roundtrip`() {
        val msg = SyncMessage.RemoteExecResult(
            command = "hostname",
            exitCode = 0,
            stdout = "edgeclaw-pc\n",
            stderr = ""
        )
        val jsonStr = json.encodeToString(SyncMessage.serializer(), msg)
        val decoded = json.decodeFromString(SyncMessage.serializer(), jsonStr)

        assertTrue(decoded is SyncMessage.RemoteExecResult)
        val rer = decoded as SyncMessage.RemoteExecResult
        assertEquals("hostname", rer.command)
        assertEquals(0, rer.exitCode)
        assertEquals("edgeclaw-pc\n", rer.stdout)
        assertTrue(rer.stderr.isEmpty())
    }

    // ─── SyncManager state tests ───

    @Test
    fun `test SyncManager initial state`() {
        val manager = SyncManager()
        assertEquals(SyncConnectionState.DISCONNECTED, manager.connectionState.value)
        assertFalse(manager.isConnected)
        assertNull(manager.lastStatus.value)
        assertNull(manager.lastConfig.value)
    }

    @Test
    fun `test SyncManager initial stats`() {
        val manager = SyncManager()
        val stats = manager.getStats()
        assertEquals(0L, stats.messagesSent)
        assertEquals(0L, stats.messagesReceived)
        assertEquals(0, stats.reconnectCount)
        assertNull(stats.lastConfigHash)
        assertNull(stats.lastStatusPush)
    }

    @Test
    fun `test SyncManager processIncoming ConfigSync`() {
        val manager = SyncManager()

        val msg = SyncMessage.ConfigSync(
            configHash = "sha256:def456",
            configData = """{"setting":"value"}"""
        )
        val jsonBytes = json.encodeToString(SyncMessage.serializer(), msg).toByteArray()
        val payload = ByteArray(1 + jsonBytes.size)
        payload[0] = 0x10 // SYNC_CONFIG
        System.arraycopy(jsonBytes, 0, payload, 1, jsonBytes.size)

        val result = manager.processIncoming(payload)
        assertNotNull(result)
        assertTrue(result is SyncMessage.ConfigSync)
        assertEquals("sha256:def456", (result as SyncMessage.ConfigSync).configHash)
        assertEquals(1L, manager.getStats().messagesReceived)
    }

    @Test
    fun `test SyncManager processIncoming StatusPush`() {
        val manager = SyncManager()

        val msg = SyncMessage.StatusPush(
            cpuUsage = 30.0,
            memoryUsage = 55.0,
            diskUsage = 40.0,
            uptimeSecs = 7200,
            activeSessions = 2,
            aiStatus = "running"
        )
        val jsonBytes = json.encodeToString(SyncMessage.serializer(), msg).toByteArray()
        val payload = ByteArray(1 + jsonBytes.size)
        payload[0] = 0x12 // SYNC_STATUS_PUSH
        System.arraycopy(jsonBytes, 0, payload, 1, jsonBytes.size)

        val result = manager.processIncoming(payload)
        assertNotNull(result)
        assertTrue(result is SyncMessage.StatusPush)
        assertNotNull(manager.lastStatus.value)
        assertEquals(7200L, manager.lastStatus.value?.uptimeSecs)
    }

    @Test
    fun `test SyncManager processIncoming invalid data returns null`() {
        val manager = SyncManager()
        val result = manager.processIncoming(ByteArray(0))
        assertNull(result)
    }

    @Test
    fun `test SyncManager disconnect resets state`() {
        val manager = SyncManager()
        manager.disconnect()
        assertEquals(SyncConnectionState.DISCONNECTED, manager.connectionState.value)
        assertFalse(manager.isConnected)
    }

    @Test
    fun `test SyncManager shutdown`() {
        val manager = SyncManager()
        manager.shutdown()
        assertEquals(SyncConnectionState.DISCONNECTED, manager.connectionState.value)
    }

    // ─── SyncClientConfig tests ───

    @Test
    fun `test SyncClientConfig defaults`() {
        val config = SyncClientConfig()
        assertEquals("192.168.1.100:8443", config.desktopAddress)
        assertEquals(30L, config.heartbeatIntervalSecs)
        assertEquals(30L, config.statusIntervalSecs)
        assertEquals(10L, config.connectTimeoutSecs)
        assertTrue(config.autoReconnect)
        assertEquals(0, config.maxReconnectAttempts)
    }

    // ─── DeviceGroup tests ───

    @Test
    fun `test DeviceGroup creation`() {
        val group = DeviceGroup(
            groupId = "grp-001",
            name = "Server Cluster",
            description = "Production servers",
            memberPeerIds = listOf("peer-1", "peer-2", "peer-3")
        )
        assertEquals("grp-001", group.groupId)
        assertEquals("Server Cluster", group.name)
        assertEquals(3, group.memberPeerIds.size)
    }

    @Test
    fun `test DeviceGroup serialization`() {
        val group = DeviceGroup(
            groupId = "grp-002",
            name = "Test Group",
            memberPeerIds = listOf("peer-a")
        )
        val jsonStr = json.encodeToString(DeviceGroup.serializer(), group)
        val decoded = json.decodeFromString(DeviceGroup.serializer(), jsonStr)
        assertEquals(group.groupId, decoded.groupId)
        assertEquals(group.name, decoded.name)
        assertEquals(group.memberPeerIds, decoded.memberPeerIds)
    }
}
