package com.edgeclaw.mobile.core.sync

import com.edgeclaw.mobile.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/**
 * SyncManager — manages TCP-based synchronization with Desktop agent.
 *
 * Handles:
 * - TCP connection establishment with ECNP handshake
 * - Config sync reception
 * - Status push reception
 * - Remote command execution requests
 * - BLE → TCP transport auto-switch
 */
class SyncManager(
    private val config: SyncClientConfig = SyncClientConfig()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _connectionState = MutableStateFlow(SyncConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SyncConnectionState> = _connectionState.asStateFlow()

    private val _lastStatus = MutableStateFlow<SyncMessage.StatusPush?>(null)
    val lastStatus: StateFlow<SyncMessage.StatusPush?> = _lastStatus.asStateFlow()

    private val _lastConfig = MutableStateFlow<SyncMessage.ConfigSync?>(null)
    val lastConfig: StateFlow<SyncMessage.ConfigSync?> = _lastConfig.asStateFlow()

    private val _lastExecResult = MutableStateFlow<SyncMessage.RemoteExecResult?>(null)
    val lastExecResult: StateFlow<SyncMessage.RemoteExecResult?> = _lastExecResult.asStateFlow()

    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private var reconnectCount = 0

    private var socket: Socket? = null
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Check if currently connected */
    val isConnected: Boolean
        get() = _connectionState.value == SyncConnectionState.CONNECTED ||
                _connectionState.value == SyncConnectionState.SYNCING

    /**
     * Connect to the desktop agent via TCP.
     * Performs ECNP handshake after connection.
     */
    suspend fun connect(address: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val targetAddress = address ?: config.desktopAddress

        try {
            _connectionState.value = SyncConnectionState.CONNECTING

            val parts = targetAddress.split(":")
            val host = parts.getOrNull(0) ?: return@withContext Result.failure(
                IOException("Invalid address: $targetAddress")
            )
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 8443

            val newSocket = Socket()
            newSocket.connect(
                InetSocketAddress(host, port),
                (config.connectTimeoutSecs * 1000).toInt()
            )
            newSocket.soTimeout = (config.connectTimeoutSecs * 1000).toInt()

            socket = newSocket
            _connectionState.value = SyncConnectionState.HANDSHAKING

            // Send ECNP handshake frame
            val handshakePayload = """
                {"protocol":"ecnp","version":"1.1","client_type":"mobile",
                 "capabilities":["config_sync","remote_exec","status_push"]}
            """.trimIndent().toByteArray()

            val handshakeFrame = encodeEcnpFrame(0x01, handshakePayload) // Handshake type
            newSocket.getOutputStream().write(handshakeFrame)
            newSocket.getOutputStream().flush()

            // Read handshake ack
            val headerBuf = ByteArray(6)
            val headerRead = newSocket.getInputStream().read(headerBuf)
            if (headerRead < 6 || headerBuf[1] != 0x05.toByte()) { // Ack type
                _connectionState.value = SyncConnectionState.ERROR
                return@withContext Result.failure(IOException("Handshake failed"))
            }

            _connectionState.value = SyncConnectionState.CONNECTED
            startHeartbeat()
            startReceiveLoop()

            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = SyncConnectionState.ERROR
            Result.failure(e)
        }
    }

    /**
     * Disconnect from the desktop agent.
     */
    fun disconnect() {
        heartbeatJob?.cancel()
        connectionJob?.cancel()
        try {
            socket?.close()
        } catch (_: IOException) { }
        socket = null
        _connectionState.value = SyncConnectionState.DISCONNECTED
    }

    /**
     * Send a remote execution request to the desktop.
     */
    suspend fun sendRemoteExec(command: String, args: List<String> = emptyList()): Result<Unit> {
        val msg = SyncMessage.RemoteExec(command = command, args = args)
        return sendSyncMessage(msg)
    }

    /**
     * Send a sync message to the desktop agent.
     */
    suspend fun sendSyncMessage(message: SyncMessage): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentSocket = socket ?: return@withContext Result.failure(
                IOException("Not connected")
            )

            val jsonBytes = json.encodeToString(SyncMessage.serializer(), message).toByteArray()
            // Prefix with sync sub-type byte
            val syncType = when (message) {
                is SyncMessage.ConfigSync -> 0x10.toByte()
                is SyncMessage.RemoteExec -> 0x11.toByte()
                is SyncMessage.StatusPush -> 0x12.toByte()
                is SyncMessage.RemoteExecResult -> 0x13.toByte()
            }

            val payload = ByteArray(1 + jsonBytes.size)
            payload[0] = syncType
            System.arraycopy(jsonBytes, 0, payload, 1, jsonBytes.size)

            val frame = encodeEcnpFrame(0x02, payload) // Data type
            currentSocket.getOutputStream().write(frame)
            currentSocket.getOutputStream().flush()

            messagesSent.incrementAndGet()
            Result.success(Unit)
        } catch (e: Exception) {
            handleDisconnect()
            Result.failure(e)
        }
    }

    /**
     * Process a received ECNP frame containing a sync message.
     */
    fun processIncoming(framePayload: ByteArray): SyncMessage? {
        if (framePayload.isEmpty()) return null

        val syncType = framePayload[0]
        val jsonBytes = framePayload.copyOfRange(1, framePayload.size)
        val jsonStr = String(jsonBytes, Charsets.UTF_8)

        return try {
            val msg = json.decodeFromString(SyncMessage.serializer(), jsonStr)
            messagesReceived.incrementAndGet()

            when (msg) {
                is SyncMessage.ConfigSync -> _lastConfig.value = msg
                is SyncMessage.StatusPush -> _lastStatus.value = msg
                is SyncMessage.RemoteExecResult -> _lastExecResult.value = msg
                else -> { /* RemoteExec not expected from desktop */ }
            }

            msg
        } catch (e: Exception) {
            null
        }
    }

    /** Get current sync stats */
    fun getStats(): SyncStats = SyncStats(
        messagesSent = messagesSent.get(),
        messagesReceived = messagesReceived.get(),
        reconnectCount = reconnectCount,
        lastConfigHash = _lastConfig.value?.configHash,
        lastStatusPush = _lastStatus.value
    )

    // ─── Private helpers ───

    /**
     * Encode an ECNP v1.1 frame: [version:1][type:1][length:4][payload:N]
     */
    private fun encodeEcnpFrame(msgType: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(6 + payload.size)
        frame[0] = 0x01 // ECNP version 1
        frame[1] = msgType.toByte()
        val len = payload.size
        frame[2] = ((len shr 24) and 0xFF).toByte()
        frame[3] = ((len shr 16) and 0xFF).toByte()
        frame[4] = ((len shr 8) and 0xFF).toByte()
        frame[5] = (len and 0xFF).toByte()
        System.arraycopy(payload, 0, frame, 6, payload.size)
        return frame
    }

    /**
     * Read a complete ECNP frame from the socket input stream.
     */
    private fun readEcnpFrame(): Pair<Int, ByteArray>? {
        val input = socket?.getInputStream() ?: return null

        val header = ByteArray(6)
        var read = 0
        while (read < 6) {
            val n = input.read(header, read, 6 - read)
            if (n <= 0) return null
            read += n
        }

        val msgType = header[1].toInt() and 0xFF
        val length = ((header[2].toInt() and 0xFF) shl 24) or
                     ((header[3].toInt() and 0xFF) shl 16) or
                     ((header[4].toInt() and 0xFF) shl 8) or
                     (header[5].toInt() and 0xFF)

        if (length > 1024 * 1024) return null // Max 1MB

        val payload = ByteArray(length)
        read = 0
        while (read < length) {
            val n = input.read(payload, read, length - read)
            if (n <= 0) return null
            read += n
        }

        return Pair(msgType, payload)
    }

    /**
     * Start periodic heartbeat sending.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(config.heartbeatIntervalSecs * 1000)
                try {
                    val heartbeat = """{"device_id":"mobile","uptime_secs":0}"""
                    val frame = encodeEcnpFrame(0x04, heartbeat.toByteArray()) // Heartbeat
                    socket?.getOutputStream()?.write(frame)
                    socket?.getOutputStream()?.flush()
                } catch (_: Exception) {
                    handleDisconnect()
                    break
                }
            }
        }
    }

    /**
     * Start receiving messages from the desktop.
     */
    private fun startReceiveLoop() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            while (isActive && isConnected) {
                try {
                    val result = withContext(Dispatchers.IO) { readEcnpFrame() }
                    if (result == null) {
                        handleDisconnect()
                        break
                    }

                    val (msgType, payload) = result
                    when (msgType) {
                        0x02 -> processIncoming(payload) // Data (sync message)
                        0x04 -> { /* Heartbeat ack */ }
                        0x05 -> { /* Ack */ }
                        0x06 -> { /* Error notification */ }
                    }
                } catch (_: Exception) {
                    handleDisconnect()
                    break
                }
            }
        }
    }

    /**
     * Handle disconnect and attempt auto-reconnect if configured.
     */
    private fun handleDisconnect() {
        _connectionState.value = SyncConnectionState.DISCONNECTED
        try { socket?.close() } catch (_: IOException) { }
        socket = null

        if (config.autoReconnect) {
            val maxAttempts = config.maxReconnectAttempts
            if (maxAttempts == 0 || reconnectCount < maxAttempts) {
                reconnectCount++
                scope.launch {
                    delay(5000) // Wait 5 seconds before reconnect
                    connect()
                }
            }
        }
    }

    /** Shutdown the sync manager and release all resources */
    fun shutdown() {
        scope.cancel()
        disconnect()
    }
}
