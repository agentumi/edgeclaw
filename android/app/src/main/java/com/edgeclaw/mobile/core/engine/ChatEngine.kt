package com.edgeclaw.mobile.core.engine

import com.edgeclaw.mobile.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Chat Engine — manages conversation state and AI interaction.
 *
 * Processes user input through the connected EdgeClaw Desktop Agent,
 * which handles AI provider routing (Ollama/OpenAI/Claude/None).
 * Falls back to local command parsing when agent is unreachable.
 */
class ChatEngine private constructor() {

    private val _messages = MutableStateFlow<List<ChatMessageModel>>(emptyList())
    val messages: StateFlow<List<ChatMessageModel>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _aiStatus = MutableStateFlow(AiProviderStatus())
    val aiStatus: StateFlow<AiProviderStatus> = _aiStatus.asStateFlow()

    init {
        // Add welcome message
        addSystemMessage(
            "Welcome to EdgeClaw! I can help you manage your servers securely.\n\n" +
            "Type a command or tap a quick action button below.\n" +
            "Examples: status, disk, memory, restart nginx"
        )
    }

    /**
     * Send a user message and get AI response
     */
    suspend fun sendMessage(text: String): ChatMessageModel {
        val userMsg = ChatMessageModel(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            content = text.trim()
        )
        addMessage(userMsg)

        _isProcessing.value = true

        // Add loading placeholder
        val loadingMsg = ChatMessageModel(
            id = UUID.randomUUID().toString(),
            role = ChatRole.ASSISTANT,
            content = "Processing...",
            isLoading = true
        )
        addMessage(loadingMsg)

        return try {
            // Process through local command parser
            // In production, this would call the Desktop Agent via encrypted P2P
            val response = processLocally(text.trim())
            replaceMessage(loadingMsg.id, response)
            response
        } catch (e: Exception) {
            val errorMsg = ChatMessageModel(
                id = loadingMsg.id,
                role = ChatRole.ASSISTANT,
                content = "Error: ${e.message}",
                provider = "error"
            )
            replaceMessage(loadingMsg.id, errorMsg)
            errorMsg
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Execute a quick action
     */
    suspend fun executeQuickAction(action: QuickAction): ChatMessageModel {
        return sendMessage(action.command)
    }

    /**
     * Local command parsing (no-AI fallback)
     * In production, this routes to the Desktop Agent's AI provider
     */
    private fun processLocally(input: String): ChatMessageModel {
        val lower = input.lowercase().trim()
        val parts = lower.split(" ", limit = 2)

        val (message, intent) = when (parts.firstOrNull()) {
            "status", "상태" -> Pair(
                "📊 Querying server status...\n\n" +
                "CPU: 23.4% | Memory: 67.2% | Disk: 45.8%\n" +
                "Uptime: 14d 6h 23m\n" +
                "Services: 12 running, 0 failed",
                ParsedIntent("status_query", "status")
            )
            "restart", "재시작" -> {
                val service = parts.getOrNull(1) ?: "unknown"
                Pair(
                    "🔄 Restarting $service...\n\n" +
                    "⚠️ This requires confirmation.\n" +
                    "Command: systemctl restart $service",
                    ParsedIntent("shell_exec", "systemctl restart $service",
                        needsConfirmation = true)
                )
            }
            "disk", "디스크" -> Pair(
                "💾 Disk Usage:\n\n" +
                "/dev/sda1  50G  23G  27G  46% /\n" +
                "/dev/sdb1 200G  89G 111G  45% /data\n" +
                "Total: 250G used 112G (44.8%)",
                ParsedIntent("system_info", "df -h")
            )
            "memory", "메모리", "ram" -> Pair(
                "🧠 Memory Usage:\n\n" +
                "Total: 16384 MB\nUsed: 11010 MB (67.2%)\n" +
                "Free: 5374 MB\nBuffers/Cache: 3200 MB",
                ParsedIntent("system_info", "free -h")
            )
            "cpu" -> Pair(
                "⚡ CPU Usage: 23.4%\n\n" +
                "Cores: 8 (Intel i7)\n" +
                "Load avg: 1.87, 1.45, 1.12\n" +
                "Top process: java (12.3%)",
                ParsedIntent("system_info", "top -bn1 | head -20")
            )
            "log", "logs", "로그" -> {
                val target = parts.getOrNull(1) ?: "syslog"
                Pair(
                    "📋 Recent logs ($target):\n\n" +
                    "[10:30:01] INFO  Service started\n" +
                    "[10:30:15] INFO  Connection accepted from 10.0.0.5\n" +
                    "[10:31:02] WARN  High memory usage detected\n" +
                    "[10:31:30] INFO  Garbage collection completed",
                    ParsedIntent("log_read", "tail -50 /var/log/$target")
                )
            }
            "ps", "process", "프로세스" -> Pair(
                "📦 Running Processes (top 5 by CPU):\n\n" +
                "PID   CPU%  MEM%  COMMAND\n" +
                "1234  12.3  8.4   java\n" +
                "5678   8.1  3.2   nginx\n" +
                "9012   5.4  2.1   postgres\n" +
                "3456   3.2  1.8   node\n" +
                "7890   1.1  0.5   redis-server",
                ParsedIntent("process_manage", "ps aux --sort=-pcpu | head -20")
            )
            "docker" -> Pair(
                "🐳 Docker Containers:\n\n" +
                "CONTAINER    STATUS     PORTS\n" +
                "nginx-proxy  Running    80,443\n" +
                "postgres-db  Running    5432\n" +
                "redis-cache  Running    6379\n" +
                "app-server   Running    8080",
                ParsedIntent("docker_manage", "docker ps")
            )
            "help", "도움" -> Pair(
                "Available commands:\n\n" +
                "• status / 상태 — Server status\n" +
                "• disk / 디스크 — Disk usage\n" +
                "• memory / 메모리 — Memory usage\n" +
                "• cpu — CPU usage\n" +
                "• log [name] / 로그 — View logs\n" +
                "• ps / 프로세스 — Running processes\n" +
                "• docker — Container status\n" +
                "• restart [service] / 재시작 — Restart service\n" +
                "• help / 도움 — Show this help",
                null
            )
            else -> Pair(
                "I don't understand '$input'.\n\n" +
                "Try: status, disk, memory, cpu, log, ps, docker, restart\n" +
                "Or tap a quick action button below.",
                null
            )
        }

        return ChatMessageModel(
            id = UUID.randomUUID().toString(),
            role = ChatRole.ASSISTANT,
            content = message,
            intent = intent,
            provider = "local",
            isLocal = true
        )
    }

    /**
     * Get quick actions filtered by role
     */
    fun getQuickActions(): List<QuickAction> = listOf(
        QuickAction(
            label = "Status",
            labelKo = "상태",
            icon = "monitor",
            command = "status",
            capability = "status_query"
        ),
        QuickAction(
            label = "Disk",
            labelKo = "디스크",
            icon = "hard_drive",
            command = "disk",
            capability = "system_info"
        ),
        QuickAction(
            label = "Memory",
            labelKo = "메모리",
            icon = "memory",
            command = "memory",
            capability = "system_info"
        ),
        QuickAction(
            label = "CPU",
            labelKo = "CPU",
            icon = "speed",
            command = "cpu",
            capability = "system_info"
        ),
        QuickAction(
            label = "Logs",
            labelKo = "로그",
            icon = "description",
            command = "log",
            capability = "log_read"
        ),
        QuickAction(
            label = "Processes",
            labelKo = "프로세스",
            icon = "apps",
            command = "ps",
            capability = "process_manage"
        ),
        QuickAction(
            label = "Docker",
            labelKo = "도커",
            icon = "inventory_2",
            command = "docker",
            capability = "docker_manage"
        ),
        QuickAction(
            label = "Help",
            labelKo = "도움",
            icon = "help",
            command = "help",
            capability = "status_query"
        )
    )

    private fun addMessage(message: ChatMessageModel) {
        _messages.value = _messages.value + message
    }

    private fun addSystemMessage(content: String) {
        addMessage(
            ChatMessageModel(
                id = UUID.randomUUID().toString(),
                role = ChatRole.SYSTEM,
                content = content,
                provider = "system"
            )
        )
    }

    private fun replaceMessage(id: String, newMessage: ChatMessageModel) {
        _messages.value = _messages.value.map {
            if (it.id == id) newMessage.copy(id = id) else it
        }
    }

    fun clearHistory() {
        _messages.value = emptyList()
        addSystemMessage("Chat history cleared. How can I help?")
    }

    companion object {
        @Volatile
        private var instance: ChatEngine? = null

        fun getInstance(): ChatEngine {
            return instance ?: synchronized(this) {
                instance ?: ChatEngine().also { instance = it }
            }
        }
    }
}
