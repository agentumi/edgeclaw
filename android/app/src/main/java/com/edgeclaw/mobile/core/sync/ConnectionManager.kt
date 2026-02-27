package com.edgeclaw.mobile.core.sync

import com.edgeclaw.mobile.ble.BleScanner
import com.edgeclaw.mobile.core.model.PeerInfo
import com.edgeclaw.mobile.core.model.SyncClientConfig
import com.edgeclaw.mobile.core.model.SyncConnectionState
import com.edgeclaw.mobile.core.model.TransportPreference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ConnectionManager — orchestrates BLE discovery and TCP connection.
 *
 * Implements the Auto transport mode:
 * 1. Start BLE scan to discover nearby Desktop agents
 * 2. Extract LAN address from BLE advertisement data
 * 3. Automatically switch to TCP/WiFi for data transfer
 * 4. Fall back to BLE-only if no LAN address available
 *
 * This provides seamless "walk up and connect" UX.
 */
class ConnectionManager(
    private val bleScanner: BleScanner? = null,
    private val syncManager: SyncManager = SyncManager(),
    private val preference: TransportPreference = TransportPreference.AUTO
) {
    private val _desktopPeer = MutableStateFlow<PeerInfo?>(null)
    val desktopPeer: StateFlow<PeerInfo?> = _desktopPeer.asStateFlow()

    private val _connectionMode = MutableStateFlow<ConnectionMode>(ConnectionMode.IDLE)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null

    /** Current sync connection state */
    val syncState: StateFlow<SyncConnectionState>
        get() = syncManager.connectionState

    /** Whether actively connected */
    val isConnected: Boolean
        get() = syncManager.isConnected

    /**
     * Start the connection process based on transport preference.
     *
     * In AUTO mode:
     * - Starts BLE scan
     * - Waits for Desktop agent advertisement
     * - Extracts TCP address from peer info
     * - Switches to TCP for data
     *
     * In TCP_LAN mode:
     * - Connects directly via TCP to configured address
     *
     * In BLE_FIRST mode:
     * - Uses BLE for discovery, then TCP if address available
     */
    fun startConnection(desktopAddress: String? = null) {
        when (preference) {
            TransportPreference.TCP_LAN -> {
                _connectionMode.value = ConnectionMode.TCP_DIRECT
                val addr = desktopAddress ?: "192.168.1.100:8443"
                scope.launch { syncManager.connect(addr) }
            }

            TransportPreference.BLE_FIRST -> {
                _connectionMode.value = ConnectionMode.BLE_SCAN
                startBleDiscovery(desktopAddress)
            }

            TransportPreference.AUTO -> {
                if (bleScanner?.isBluetoothAvailable == true) {
                    _connectionMode.value = ConnectionMode.BLE_SCAN
                    startBleDiscovery(desktopAddress)
                } else if (desktopAddress != null) {
                    _connectionMode.value = ConnectionMode.TCP_DIRECT
                    scope.launch { syncManager.connect(desktopAddress) }
                } else {
                    _connectionMode.value = ConnectionMode.IDLE
                }
            }
        }
    }

    /**
     * Stop all connection activities.
     */
    fun stopConnection() {
        discoveryJob?.cancel()
        bleScanner?.stopScan()
        syncManager.disconnect()
        _connectionMode.value = ConnectionMode.IDLE
    }

    /**
     * Manually connect to a specific address (bypass auto-detection).
     */
    suspend fun connectDirect(address: String): Result<Unit> {
        _connectionMode.value = ConnectionMode.TCP_DIRECT
        return syncManager.connect(address)
    }

    /**
     * Get the underlying SyncManager for sending messages.
     */
    fun getSyncManager(): SyncManager = syncManager

    // ─── BLE Discovery ───

    private fun startBleDiscovery(fallbackAddress: String?) {
        bleScanner?.startScan()

        discoveryJob = scope.launch {
            // Monitor discovered peers for Desktop agent
            bleScanner?.discoveredPeers?.collectLatest { peers ->
                val desktopPeer = peers.find { peer ->
                    peer.deviceName.contains("EdgeClaw", ignoreCase = true) &&
                    (peer.deviceType == "desktop" || peer.deviceType == "pc" ||
                     peer.deviceName.contains("PC", ignoreCase = true))
                }

                if (desktopPeer != null) {
                    _desktopPeer.value = desktopPeer
                    bleScanner.stopScan()

                    // Try to extract TCP address from peer address
                    // In production, BLE GATT characteristics would provide this
                    val tcpAddress = extractTcpAddress(desktopPeer)
                    if (tcpAddress != null) {
                        _connectionMode.value = ConnectionMode.BLE_TO_TCP
                        syncManager.connect(tcpAddress)
                    } else if (fallbackAddress != null) {
                        _connectionMode.value = ConnectionMode.TCP_DIRECT
                        syncManager.connect(fallbackAddress)
                    } else {
                        _connectionMode.value = ConnectionMode.BLE_ONLY
                    }
                }
            }

            // Timeout: if no BLE device found after 15 seconds, fall back to TCP
            delay(15_000)
            if (!isConnected && fallbackAddress != null) {
                bleScanner?.stopScan()
                _connectionMode.value = ConnectionMode.TCP_DIRECT
                syncManager.connect(fallbackAddress)
            }
        }
    }

    /**
     * Extract TCP address from a BLE-discovered peer.
     * In production, this reads from GATT characteristics.
     * For now, uses the peer address if it looks like an IP.
     */
    private fun extractTcpAddress(peer: PeerInfo): String? {
        val address = peer.address
        // Check if address looks like IP:port
        return if (address.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+"))) {
            address
        } else if (address.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
            "$address:8443"
        } else {
            null // BLE MAC address, no TCP fallback
        }
    }

    /** Shutdown and release all resources */
    fun shutdown() {
        scope.cancel()
        stopConnection()
        syncManager.shutdown()
    }
}

/**
 * Current connection mode
 */
enum class ConnectionMode {
    /** Not connected */
    IDLE,
    /** BLE scanning for Desktop agent */
    BLE_SCAN,
    /** Connected via BLE only (no TCP available) */
    BLE_ONLY,
    /** BLE discovery → TCP switch in progress */
    BLE_TO_TCP,
    /** Direct TCP/WiFi connection */
    TCP_DIRECT
}
