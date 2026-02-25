package com.edgeclaw.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.edgeclaw.mobile.core.model.PeerInfo
import com.edgeclaw.mobile.core.model.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/**
 * BLE Scanner — discovers nearby EdgeClaw devices via BLE advertisements.
 *
 * Scans for devices advertising the EdgeClaw service UUID.
 */
class BleScanner(private val context: Context) {

    companion object {
        // EdgeClaw BLE Service UUID
        val EDGECLAW_SERVICE_UUID: UUID =
            UUID.fromString("EC1A0001-E4B5-4F67-8C5D-2A1B3C4D5E6F")

        private const val SCAN_PERIOD_MS = 10_000L // 10 seconds
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager?.adapter

    private var bleScanner: BluetoothLeScanner? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerInfo>> = _discoveredPeers.asStateFlow()

    private val peerMap = mutableMapOf<String, PeerInfo>()

    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val peerId = device.address
            val name = device.name ?: "Unknown Device"
            val rssi = result.rssi

            val peer = PeerInfo(
                peerId = peerId,
                deviceName = name,
                deviceType = "unknown",
                address = device.address,
                capabilities = emptyList(),
                lastSeen = Instant.now().toString(),
                isConnected = false,
                rssi = rssi,
                transport = Transport.BLE
            )

            peerMap[peerId] = peer
            _discoveredPeers.value = peerMap.values.toList()
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    /**
     * Start BLE scan
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        if (!isBluetoothEnabled) return

        bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(EDGECLAW_SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        peerMap.clear()
        _discoveredPeers.value = emptyList()

        try {
            bleScanner?.startScan(filters, settings, scanCallback)
            _isScanning.value = true
        } catch (e: SecurityException) {
            _isScanning.value = false
        }
    }

    /**
     * Start scan without UUID filter (discovers all BLE devices)
     */
    @SuppressLint("MissingPermission")
    fun startScanAll() {
        if (_isScanning.value) return
        if (!isBluetoothEnabled) return

        bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        peerMap.clear()
        _discoveredPeers.value = emptyList()

        try {
            bleScanner?.startScan(null, settings, scanCallback)
            _isScanning.value = true
        } catch (e: SecurityException) {
            _isScanning.value = false
        }
    }

    /**
     * Stop BLE scan
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // Ignore
        }
        _isScanning.value = false
    }

    /**
     * Clear discovered peers
     */
    fun clearPeers() {
        peerMap.clear()
        _discoveredPeers.value = emptyList()
    }
}
