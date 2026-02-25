package com.edgeclaw.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edgeclaw.mobile.core.engine.EdgeClawEngine

/**
 * Settings screen — app and engine configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val engine = remember { EdgeClawEngine.getInstance() }
    val config = engine.config

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingRow(
                        icon = Icons.Default.Badge,
                        title = "Device Name",
                        subtitle = config.deviceName
                    )
                    SettingRow(
                        icon = Icons.Default.DeviceHub,
                        title = "Device Type",
                        subtitle = config.deviceType
                    )
                }
            }

            // Network Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Network",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingRow(
                        icon = Icons.Default.NetworkCheck,
                        title = "Listen Port",
                        subtitle = "${config.listenPort}"
                    )
                    SettingRow(
                        icon = Icons.Default.People,
                        title = "Max Connections",
                        subtitle = "${config.maxConnections}"
                    )

                    var quicEnabled by remember { mutableStateOf(config.quicEnabled) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("QUIC Transport", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "UDP-based low-latency transport",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Switch(
                            checked = quicEnabled,
                            onCheckedChange = { quicEnabled = it }
                        )
                    }
                }
            }

            // Security Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingRow(
                        icon = Icons.Default.Lock,
                        title = "Encryption",
                        subtitle = "AES-256-GCM"
                    )
                    SettingRow(
                        icon = Icons.Default.VpnKey,
                        title = "Key Exchange",
                        subtitle = "X25519 ECDH"
                    )
                    SettingRow(
                        icon = Icons.Default.Timer,
                        title = "Session Duration",
                        subtitle = "1 hour"
                    )
                    SettingRow(
                        icon = Icons.Default.Security,
                        title = "Default Policy",
                        subtitle = "Deny All"
                    )
                }
            }

            // About
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingRow(
                        icon = Icons.Default.Info,
                        title = "Version",
                        subtitle = "1.0.0"
                    )
                    SettingRow(
                        icon = Icons.Default.Code,
                        title = "Core Engine",
                        subtitle = "Rust + Kotlin"
                    )
                    SettingRow(
                        icon = Icons.Default.GitHub,
                        title = "Repository",
                        subtitle = "github.com/agentumi/edgeclaw"
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
