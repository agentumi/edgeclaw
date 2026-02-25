package com.edgeclaw.mobile.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edgeclaw.mobile.core.engine.EdgeClawEngine
import com.edgeclaw.mobile.core.model.PeerInfo
import com.edgeclaw.mobile.core.model.Transport
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Discovery Screen — scan for nearby EdgeClaw devices via BLE
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onNavigateBack: () -> Unit,
    onDeviceSelected: (String) -> Unit
) {
    val engine = remember { EdgeClawEngine.getInstance() }
    var isScanning by remember { mutableStateOf(false) }
    var discoveredDevices by remember { mutableStateOf<List<PeerInfo>>(emptyList()) }

    // Simulated scanning (real BLE scanner requires platform context)
    LaunchedEffect(isScanning) {
        if (isScanning) {
            // Simulate device discovery
            delay(1500)
            val simulated = listOf(
                PeerInfo(
                    peerId = "EC:1A:00:01:AA:BB",
                    deviceName = "EdgeClaw-PC-Alpha",
                    deviceType = "pc",
                    address = "EC:1A:00:01:AA:BB",
                    capabilities = listOf("file_read", "file_write", "shell_exec"),
                    lastSeen = Instant.now().toString(),
                    rssi = -45,
                    transport = Transport.BLE
                ),
                PeerInfo(
                    peerId = "EC:1A:00:02:CC:DD",
                    deviceName = "EdgeClaw-Tablet",
                    deviceType = "tablet",
                    address = "EC:1A:00:02:CC:DD",
                    capabilities = listOf("file_read", "sensor_read"),
                    lastSeen = Instant.now().toString(),
                    rssi = -62,
                    transport = Transport.BLE
                ),
            )
            discoveredDevices = simulated
            delay(3000)
            isScanning = false
        }
    }

    // Scanning animation
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Discovery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Scan control card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isScanning) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .then(
                                    if (isScanning) Modifier.rotate(rotation)
                                    else Modifier
                                ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isScanning) "Scanning..." else "BLE Scanner",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${discoveredDevices.size} device(s) found",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isScanning) {
                                isScanning = false
                            } else {
                                discoveredDevices = emptyList()
                                isScanning = true
                            }
                        }
                    ) {
                        Text(if (isScanning) "Stop" else "Scan")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scanning indicator
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Results
            Text(
                text = "Discovered Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (discoveredDevices.isEmpty() && !isScanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No devices found", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Tap 'Scan' to search for nearby EdgeClaw devices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(discoveredDevices) { peer ->
                        DiscoveredDeviceCard(
                            peer = peer,
                            onConnect = {
                                engine.addPeer(peer)
                                onDeviceSelected(peer.peerId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(
    peer: PeerInfo,
    onConnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (peer.deviceType) {
                    "pc" -> Icons.Default.Computer
                    "tablet" -> Icons.Default.Tablet
                    else -> Icons.Default.PhoneAndroid
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = peer.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row {
                    Text(
                        text = "RSSI: ${peer.rssi} dBm",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = peer.transport.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            FilledTonalButton(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}
