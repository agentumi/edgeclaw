package com.edgeclaw.mobile.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeclaw.mobile.core.engine.ChatEngine
import com.edgeclaw.mobile.core.model.ChatMessageModel
import com.edgeclaw.mobile.core.model.ChatRole
import com.edgeclaw.mobile.core.model.QuickAction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat Screen — Main chat interface with AI integration.
 *
 * Design principles for elderly-friendly UX:
 * - Large text (16sp minimum)
 * - High contrast colors
 * - Big touch targets (48dp minimum)
 * - Quick action buttons (no typing needed)
 * - Voice input option
 * - Simple, clear layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit
) {
    val chatEngine = remember { ChatEngine.getInstance() }
    val messages by chatEngine.messages.collectAsState()
    val isProcessing by chatEngine.isProcessing.collectAsState()
    val aiStatus by chatEngine.aiStatus.collectAsState()
    val quickActions = remember { chatEngine.getQuickActions() }

    var inputText by remember { mutableStateOf("") }
    var showQuickActions by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Voice input launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrEmpty()) {
            inputText = spoken
            // Auto-send voice input
            scope.launch {
                chatEngine.sendMessage(spoken)
                inputText = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "EdgeClaw Chat",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "AI: ${aiStatus.provider} ${if (aiStatus.isLocal) "🔒" else "☁️"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Toggle quick actions
                    IconButton(onClick = { showQuickActions = !showQuickActions }) {
                        Icon(
                            if (showQuickActions) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            "Quick Actions"
                        )
                    }
                    // Clear chat
                    IconButton(onClick = { chatEngine.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear Chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quick action buttons (collapsible)
            AnimatedVisibility(
                visible = showQuickActions,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                QuickActionBar(
                    actions = quickActions,
                    enabled = !isProcessing,
                    onAction = { action ->
                        scope.launch {
                            chatEngine.executeQuickAction(action)
                        }
                    }
                )
            }

            // Message list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
            }

            // Input bar
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                isProcessing = isProcessing,
                onSend = {
                    if (inputText.isNotBlank()) {
                        val text = inputText
                        inputText = ""
                        keyboardController?.hide()
                        scope.launch {
                            chatEngine.sendMessage(text)
                        }
                    }
                },
                onVoice = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR"
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                            arrayListOf("ko-KR", "en-US")
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a command...")
                    }
                    try {
                        voiceLauncher.launch(intent)
                    } catch (_: Exception) {
                        // Speech recognizer not available
                    }
                }
            )
        }
    }
}

/**
 * Quick action button bar — large, colorful buttons for elderly users
 */
@Composable
fun QuickActionBar(
    actions: List<QuickAction>,
    enabled: Boolean,
    onAction: (QuickAction) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(actions) { action ->
                QuickActionButton(
                    action = action,
                    enabled = enabled,
                    onClick = { onAction(action) }
                )
            }
        }
    }
}

/**
 * Individual quick action button — big and accessible
 */
@Composable
fun QuickActionButton(
    action: QuickAction,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val icon = when (action.icon) {
        "monitor" -> Icons.Default.DesktopWindows
        "hard_drive" -> Icons.Default.Storage
        "memory" -> Icons.Default.Memory
        "speed" -> Icons.Default.Speed
        "description" -> Icons.Default.Description
        "apps" -> Icons.Default.Apps
        "inventory_2" -> Icons.Default.Inventory2
        "wifi" -> Icons.Default.Wifi
        "help" -> Icons.AutoMirrored.Filled.Help
        else -> Icons.Default.PlayArrow
    }

    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(64.dp)
            .widthIn(min = 80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = action.label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = action.labelKo.ifEmpty { action.label },
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Chat message bubble
 */
@Composable
fun ChatBubble(message: ChatMessageModel) {
    val isUser = message.role == ChatRole.USER
    val isSystem = message.role == ChatRole.SYSTEM

    val alignment = when {
        isUser -> Alignment.End
        else -> Alignment.Start
    }

    val bubbleColor = when {
        isSystem -> MaterialTheme.colorScheme.tertiaryContainer
        isUser -> MaterialTheme.colorScheme.primary
        message.isLoading -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = when {
        isSystem -> MaterialTheme.colorScheme.onTertiaryContainer
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val bubbleShape = when {
        isUser -> RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
        else -> RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Role label
        if (!isUser && !isSystem) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "EdgeClaw",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.provider.isNotEmpty() && message.provider != "local") {
                    Text(
                        " · ${message.provider}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Bubble
        Surface(
            color = bubbleColor,
            shape = bubbleShape,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.isLoading) {
                    // Loading animation
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                } else {
                    // Message content
                    Text(
                        text = message.content,
                        color = textColor,
                        fontSize = 16.sp, // Large text for readability
                        lineHeight = 22.sp,
                        fontFamily = if (message.intent != null) FontFamily.Monospace else FontFamily.Default
                    )

                    // Intent badge (if command was parsed)
                    if (message.intent != null && !isUser) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = message.intent.command,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Timestamp
        Text(
            text = formatTimestamp(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            fontSize = 10.sp
        )
    }
}

/**
 * Chat input bar with text field, voice button, and send button
 */
@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isProcessing: Boolean,
    onSend: () -> Unit,
    onVoice: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Voice button (large for elderly users)
            IconButton(
                onClick = onVoice,
                enabled = !isProcessing,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Text input
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                placeholder = {
                    Text(
                        "Type a command or speak...",
                        fontSize = 15.sp
                    )
                },
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 3,
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Send button
            FilledIconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isProcessing,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Format timestamp to readable time
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
