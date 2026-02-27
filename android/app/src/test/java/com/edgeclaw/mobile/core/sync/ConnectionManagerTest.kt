package com.edgeclaw.mobile.core.sync

import com.edgeclaw.mobile.core.model.SyncConnectionState
import com.edgeclaw.mobile.core.model.TransportPreference
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ConnectionManager.
 */
class ConnectionManagerTest {

    @Test
    fun `test ConnectionManager initial state`() {
        val manager = ConnectionManager()
        assertEquals(ConnectionMode.IDLE, manager.connectionMode.value)
        assertFalse(manager.isConnected)
        assertNull(manager.desktopPeer.value)
    }

    @Test
    fun `test ConnectionManager TCP direct start`() {
        val syncManager = SyncManager()
        val manager = ConnectionManager(
            bleScanner = null,
            syncManager = syncManager,
            preference = TransportPreference.TCP_LAN
        )
        // startConnection changes mode to TCP_DIRECT
        manager.startConnection("192.168.1.100:8443")
        assertEquals(ConnectionMode.TCP_DIRECT, manager.connectionMode.value)
    }

    @Test
    fun `test ConnectionManager auto without BLE`() {
        val syncManager = SyncManager()
        val manager = ConnectionManager(
            bleScanner = null,
            syncManager = syncManager,
            preference = TransportPreference.AUTO
        )
        // AUTO without BLE and with address should go TCP
        manager.startConnection("10.0.0.5:8443")
        assertEquals(ConnectionMode.TCP_DIRECT, manager.connectionMode.value)
    }

    @Test
    fun `test ConnectionManager auto without BLE and no address`() {
        val manager = ConnectionManager(
            bleScanner = null,
            preference = TransportPreference.AUTO
        )
        // AUTO with no BLE and no address stays IDLE
        manager.startConnection()
        assertEquals(ConnectionMode.IDLE, manager.connectionMode.value)
    }

    @Test
    fun `test ConnectionManager stopConnection resets state`() {
        val manager = ConnectionManager(preference = TransportPreference.TCP_LAN)
        manager.startConnection("192.168.1.100:8443")
        manager.stopConnection()
        assertEquals(ConnectionMode.IDLE, manager.connectionMode.value)
    }

    @Test
    fun `test ConnectionManager sync state`() {
        val manager = ConnectionManager()
        assertEquals(SyncConnectionState.DISCONNECTED, manager.syncState.value)
    }

    @Test
    fun `test ConnectionManager getSyncManager returns instance`() {
        val syncManager = SyncManager()
        val manager = ConnectionManager(syncManager = syncManager)
        assertSame(syncManager, manager.getSyncManager())
    }

    @Test
    fun `test ConnectionManager shutdown`() {
        val manager = ConnectionManager()
        manager.startConnection("192.168.1.100:8443")
        manager.shutdown()
        assertEquals(ConnectionMode.IDLE, manager.connectionMode.value)
    }

    @Test
    fun `test TransportPreference values`() {
        assertEquals(3, TransportPreference.entries.size)
        assertNotNull(TransportPreference.valueOf("BLE_FIRST"))
        assertNotNull(TransportPreference.valueOf("TCP_LAN"))
        assertNotNull(TransportPreference.valueOf("AUTO"))
    }

    @Test
    fun `test ConnectionMode values`() {
        assertEquals(5, ConnectionMode.entries.size)
        assertNotNull(ConnectionMode.valueOf("IDLE"))
        assertNotNull(ConnectionMode.valueOf("BLE_SCAN"))
        assertNotNull(ConnectionMode.valueOf("BLE_ONLY"))
        assertNotNull(ConnectionMode.valueOf("BLE_TO_TCP"))
        assertNotNull(ConnectionMode.valueOf("TCP_DIRECT"))
    }
}
