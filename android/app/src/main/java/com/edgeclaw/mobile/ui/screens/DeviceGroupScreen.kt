package com.edgeclaw.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeclaw.mobile.core.model.DeviceGroup
import java.util.UUID

/**
 * Device Group management screen.
 *
 * Features:
 * - Group CRUD (create, read, update, delete)
 * - Group member management
 * - Group command broadcast
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceGroupScreen(
    onNavigateBack: () -> Unit
) {
    var groups by remember { mutableStateOf(getSampleGroups()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf<DeviceGroup?>(null) }
    var expandedGroupId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("디바이스 그룹", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "뒤로 가기" }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.semantics { contentDescription = "새 그룹 만들기" },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("새 그룹", fontSize = 16.sp) }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyGroupState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    GroupCard(
                        group = group,
                        isExpanded = expandedGroupId == group.groupId,
                        onToggleExpand = {
                            expandedGroupId = if (expandedGroupId == group.groupId)
                                null else group.groupId
                        },
                        onBroadcast = { showBroadcastDialog = group },
                        onDelete = {
                            groups = groups.filter { it.groupId != group.groupId }
                        }
                    )
                }
            }
        }

        // Create group dialog
        if (showCreateDialog) {
            CreateGroupDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, description ->
                    val newGroup = DeviceGroup(
                        groupId = UUID.randomUUID().toString().take(8),
                        name = name,
                        description = description
                    )
                    groups = groups + newGroup
                    showCreateDialog = false
                }
            )
        }

        // Broadcast dialog
        showBroadcastDialog?.let { group ->
            BroadcastDialog(
                group = group,
                onDismiss = { showBroadcastDialog = null },
                onBroadcast = { command ->
                    // In production: send command to all group members via SyncManager
                    showBroadcastDialog = null
                }
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: DeviceGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onBroadcast: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300))
            .semantics { contentDescription = "그룹: ${group.name}" },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Devices,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (group.description.isNotEmpty()) {
                        Text(
                            group.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.semantics {
                        contentDescription = "${group.memberPeerIds.size}개 디바이스"
                    }
                ) {
                    Text(
                        "${group.memberPeerIds.size}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = if (isExpanded) "접기" else "펼치기" }
                ) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Members list
                    if (group.memberPeerIds.isEmpty()) {
                        Text(
                            "멤버 없음 — 디바이스를 추가하세요",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        group.memberPeerIds.forEach { peerId ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Computer,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(peerId, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBroadcast,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .semantics { contentDescription = "명령 브로드캐스트" }
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("브로드캐스트")
                        }

                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .semantics { contentDescription = "그룹 삭제" },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("삭제")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGroupState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.GroupWork,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "그룹이 없습니다",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "디바이스 그룹을 만들어\n일괄 관리해 보세요",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 그룹 만들기", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("그룹 이름") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "그룹 이름 입력" },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("설명 (선택)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "설명 입력" },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, description) },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .height(48.dp)
                    .semantics { contentDescription = "만들기" }
            ) {
                Text("만들기")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .semantics { contentDescription = "취소" }
            ) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun BroadcastDialog(
    group: DeviceGroup,
    onDismiss: () -> Unit,
    onBroadcast: (command: String) -> Unit
) {
    var command by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("명령 브로드캐스트", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "'${group.name}' 그룹의 모든 디바이스에\n명령을 전송합니다.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("명령어") },
                    placeholder = { Text("systemctl status nginx") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "명령어 입력" },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (command.isNotBlank()) onBroadcast(command) },
                enabled = command.isNotBlank(),
                modifier = Modifier
                    .height(48.dp)
                    .semantics { contentDescription = "전송" }
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("전송")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .semantics { contentDescription = "취소" }
            ) {
                Text("취소")
            }
        }
    )
}

// ─── Sample Data ───

private fun getSampleGroups(): List<DeviceGroup> = listOf(
    DeviceGroup(
        groupId = "grp-001",
        name = "서버 클러스터",
        description = "프로덕션 서버 그룹",
        memberPeerIds = listOf("peer-001", "peer-002", "peer-003")
    ),
    DeviceGroup(
        groupId = "grp-002",
        name = "개발 장비",
        description = "개발팀 워크스테이션",
        memberPeerIds = listOf("peer-004", "peer-005")
    )
)
