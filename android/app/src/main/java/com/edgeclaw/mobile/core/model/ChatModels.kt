package com.edgeclaw.mobile.core.model

import kotlinx.serialization.Serializable

/**
 * Chat message for conversation display
 */
@Serializable
data class ChatMessageModel(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val intent: ParsedIntent? = null,
    val isLoading: Boolean = false,
    val provider: String = "",
    val isLocal: Boolean = true
)

/**
 * Chat message role
 */
@Serializable
enum class ChatRole {
    USER, ASSISTANT, SYSTEM
}

/**
 * Parsed intent from AI
 */
@Serializable
data class ParsedIntent(
    val capability: String,
    val command: String,
    val args: List<String> = emptyList(),
    val needsConfirmation: Boolean = false
)

/**
 * Quick action button for elderly-friendly UI
 */
@Serializable
data class QuickAction(
    val label: String,
    val icon: String,
    val command: String,
    val capability: String,
    val needsConfirmation: Boolean = false,
    val labelKo: String = ""  // Korean label for market vendors
)

/**
 * AI provider status
 */
@Serializable
data class AiProviderStatus(
    val provider: String = "none",
    val available: Boolean = false,
    val isLocal: Boolean = true,
    val requiresConsent: Boolean = true
)

/**
 * Execution result from agent
 */
@Serializable
data class ExecutionResult(
    val executionId: String,
    val success: Boolean,
    val stdout: String = "",
    val stderr: String = "",
    val durationMs: Long = 0
)
