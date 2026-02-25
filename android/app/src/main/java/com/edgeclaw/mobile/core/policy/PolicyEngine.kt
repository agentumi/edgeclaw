package com.edgeclaw.mobile.core.policy

import com.edgeclaw.mobile.core.model.PolicyDecision
import com.edgeclaw.mobile.core.model.RiskLevel
import com.edgeclaw.mobile.core.model.Role

/**
 * Policy Engine — evaluates capability requests against RBAC roles.
 * Default deny for unknown capabilities.
 */
class PolicyEngine {

    data class CapabilityDef(
        val name: String,
        val riskLevel: RiskLevel,
        val description: String
    )

    private val capabilities = mutableListOf<CapabilityDef>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        capabilities.addAll(
            listOf(
                CapabilityDef("status_query", RiskLevel.NONE, "Query device status"),
                CapabilityDef("heartbeat", RiskLevel.NONE, "Send/receive heartbeat"),
                CapabilityDef("file_read", RiskLevel.LOW, "Read files from device"),
                CapabilityDef("sensor_read", RiskLevel.LOW, "Read sensor data"),
                CapabilityDef("clipboard_read", RiskLevel.LOW, "Read clipboard content"),
                CapabilityDef("file_write", RiskLevel.MEDIUM, "Write files to device"),
                CapabilityDef("config_change", RiskLevel.MEDIUM, "Modify device configuration"),
                CapabilityDef("clipboard_write", RiskLevel.MEDIUM, "Write to clipboard"),
                CapabilityDef("shell_exec", RiskLevel.HIGH, "Execute shell commands"),
                CapabilityDef("firmware_update", RiskLevel.HIGH, "Update device firmware"),
                CapabilityDef("system_reboot", RiskLevel.HIGH, "Reboot device"),
            )
        )
    }

    /**
     * Evaluate capability request against a role
     */
    fun evaluate(capabilityName: String, role: Role): PolicyDecision {
        val cap = capabilities.find { it.name == capabilityName }

        return if (cap != null) {
            val allowed = cap.riskLevel.level <= role.maxRisk.level
            PolicyDecision(
                allowed = allowed,
                reason = if (allowed) {
                    "Role '${role.name}' allowed for '${cap.name}' (risk ${cap.riskLevel.level})"
                } else {
                    "Role '${role.name}' denied for '${cap.name}' (risk ${cap.riskLevel.level} > max ${role.maxRisk.level})"
                },
                riskLevel = cap.riskLevel.level
            )
        } else {
            // Default deny for unknown capabilities
            PolicyDecision(
                allowed = false,
                reason = "Unknown capability '$capabilityName' — default deny",
                riskLevel = RiskLevel.HIGH.level
            )
        }
    }

    fun listCapabilities(): List<CapabilityDef> = capabilities.toList()
}
