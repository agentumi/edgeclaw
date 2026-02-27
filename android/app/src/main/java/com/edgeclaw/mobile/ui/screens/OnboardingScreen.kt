package com.edgeclaw.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 4-Step Onboarding Flow:
 * 1. Welcome — introduce EdgeClaw
 * 2. BLE Scan — discover nearby agents
 * 3. Agent Connect — establish connection
 * 4. Complete — ready to use
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var isScanning by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var foundDevices by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            OnboardingProgress(
                currentStep = currentStep,
                totalSteps = 4
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Step content with animation
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { fullWidth -> fullWidth }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { fullWidth -> -fullWidth }
                    )
                },
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    0 -> WelcomeStep()
                    1 -> BleScanStep(
                        isScanning = isScanning,
                        foundDevices = foundDevices
                    )
                    2 -> ConnectStep(
                        isConnecting = isConnecting,
                        isConnected = isConnected
                    )
                    3 -> CompleteStep()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = {
                            currentStep--
                            isScanning = false
                            isConnecting = false
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .semantics { contentDescription = "이전 단계" }
                    ) {
                        Text("이전", fontSize = 18.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        when (currentStep) {
                            0 -> currentStep = 1
                            1 -> {
                                if (!isScanning) {
                                    isScanning = true
                                    // Simulate scan
                                } else {
                                    currentStep = 2
                                    isScanning = false
                                }
                            }
                            2 -> {
                                if (!isConnecting && !isConnected) {
                                    isConnecting = true
                                } else if (isConnected) {
                                    currentStep = 3
                                }
                            }
                            3 -> onComplete()
                        }
                    },
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 140.dp)
                        .semantics {
                            contentDescription = when (currentStep) {
                                0 -> "시작하기"
                                1 -> if (!isScanning) "스캔 시작" else "다음"
                                2 -> if (!isConnected) "연결" else "다음"
                                3 -> "완료"
                                else -> "다음"
                            }
                        },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = when (currentStep) {
                            0 -> "시작하기"
                            1 -> if (!isScanning) "🔍 스캔 시작" else "다음 →"
                            2 -> if (!isConnected) "🔗 연결" else "다음 →"
                            3 -> "✅ 완료"
                            else -> "다음"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip option (not on final step)
            if (currentStep < 3) {
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.semantics { contentDescription = "건너뛰기" }
                ) {
                    Text("건너뛰기", fontSize = 14.sp)
                }
            }
        }
    }

    // Simulate scanning effect
    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(1500)
            foundDevices = 1
            delay(1500)
            foundDevices = 2
        }
    }

    // Simulate connection effect
    LaunchedEffect(isConnecting) {
        if (isConnecting) {
            delay(2000)
            isConnecting = false
            isConnected = true
        }
    }
}

// ─── Step Composables ───

@Composable
private fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "환영합니다 단계" }
    ) {
        OnboardingIcon(
            icon = Icons.Filled.Shield,
            description = "EdgeClaw 보안 아이콘"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "EdgeClaw에 오신 것을 환영합니다",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "제로 트러스트 엣지 AI 관리 시스템입니다.\n" +
                   "데스크탑 에이전트와 안전하게 연결하여\n" +
                   "시스템을 원격으로 관리할 수 있습니다.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        FeatureRow(icon = Icons.Filled.Lock, text = "Ed25519 + AES-256-GCM 암호화")
        FeatureRow(icon = Icons.Filled.Bluetooth, text = "BLE 근거리 자동 탐색")
        FeatureRow(icon = Icons.Filled.Wifi, text = "TCP/WiFi LAN 자동 전환")
        FeatureRow(icon = Icons.Filled.Terminal, text = "원격 명령 실행 및 모니터링")
    }
}

@Composable
private fun BleScanStep(
    isScanning: Boolean,
    foundDevices: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "BLE 스캔 단계" }
    ) {
        OnboardingIcon(
            icon = Icons.Filled.BluetoothSearching,
            description = "블루투스 검색 아이콘"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "주변 에이전트 검색",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "블루투스를 사용하여 근처의\nEdgeClaw 데스크탑 에이전트를 찾습니다.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "스캔 중" }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "검색 중... ${foundDevices}개 발견",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (foundDevices > 0) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "발견된 에이전트: EdgeClaw-PC" },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "EdgeClaw-PC",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "192.168.1.100:8443",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.SignalWifi4Bar,
                        contentDescription = "신호 강도",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectStep(
    isConnecting: Boolean,
    isConnected: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "에이전트 연결 단계" }
    ) {
        OnboardingIcon(
            icon = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.PhonelinkRing,
            description = if (isConnected) "연결 완료 아이콘" else "에이전트 연결 아이콘"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isConnected) "연결 완료!" else "에이전트 연결",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isConnected)
                "데스크탑 에이전트와 안전하게 연결되었습니다.\nEd25519 인증 + AES-256-GCM 암호화 활성화."
            else
                "발견된 에이전트에 보안 연결을 설정합니다.\nECNP 핸드셰이크 + ECDH 키 교환.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "연결 중" }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "ECNP 핸드셰이크 진행 중...",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (isConnected) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ConnectionDetailRow("프로토콜", "ECNP v1.1")
                    ConnectionDetailRow("암호화", "AES-256-GCM")
                    ConnectionDetailRow("인증", "Ed25519")
                    ConnectionDetailRow("키 교환", "X25519 ECDH")
                    ConnectionDetailRow("상태", "✅ 활성")
                }
            }
        }
    }
}

@Composable
private fun CompleteStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "설정 완료 단계" }
    ) {
        OnboardingIcon(
            icon = Icons.Filled.Celebration,
            description = "축하 아이콘"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "모든 설정이 완료되었습니다!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "이제 EdgeClaw를 사용할 준비가 되었습니다.\n다음 기능들을 활용해 보세요:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        FeatureRow(icon = Icons.Filled.Dashboard, text = "대시보드에서 시스템 상태 확인")
        FeatureRow(icon = Icons.Filled.Chat, text = "AI 채팅으로 시스템 관리")
        FeatureRow(icon = Icons.Filled.Devices, text = "디바이스 그룹 관리")
        FeatureRow(icon = Icons.Filled.Sync, text = "실시간 동기화 및 알림")
    }
}

// ─── Shared Components ───

@Composable
private fun OnboardingProgress(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .semantics {
                contentDescription = "단계 ${currentStep + 1} / $totalSteps"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= currentStep
            Box(
                modifier = Modifier
                    .size(if (index == currentStep) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
            if (index < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(
                            if (index < currentStep) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun OnboardingIcon(icon: ImageVector, description: String) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun ConnectionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
