use crate::error::EdgeClawError;

/// Capability risk levels (0-3)
#[derive(Debug, Clone, Copy, PartialEq, PartialOrd)]
pub enum RiskLevel {
    /// Level 0: Passive read-only (status query, heartbeat)
    None = 0,
    /// Level 1: Local data access (file read, sensor read)
    Low = 1,
    /// Level 2: State modification (config change, file write)
    Medium = 2,
    /// Level 3: System-level operations (shell exec, firmware update)
    High = 3,
}

/// Role-Based Access Control roles
#[derive(Debug, Clone, Copy, PartialEq, PartialOrd)]
pub enum Role {
    Viewer = 0,
    Operator = 1,
    Admin = 2,
    Owner = 3,
}

impl Role {
    pub fn max_allowed_risk(&self) -> RiskLevel {
        match self {
            Role::Viewer => RiskLevel::None,
            Role::Operator => RiskLevel::Low,
            Role::Admin => RiskLevel::Medium,
            Role::Owner => RiskLevel::High,
        }
    }

    pub fn parse_role(s: &str) -> Result<Self, EdgeClawError> {
        match s.to_lowercase().as_str() {
            "viewer" => Ok(Role::Viewer),
            "operator" => Ok(Role::Operator),
            "admin" => Ok(Role::Admin),
            "owner" => Ok(Role::Owner),
            _ => Err(EdgeClawError::InvalidParameter),
        }
    }
}

/// Policy decision result
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PolicyDecision {
    pub allowed: bool,
    pub reason: String,
    pub risk_level: u8,
}

/// Capability entry
#[derive(Debug, Clone)]
pub struct Capability {
    pub name: String,
    pub risk_level: RiskLevel,
    pub description: String,
}

/// Policy Engine — evaluates capability requests against role-based policies
pub struct PolicyEngine {
    capabilities: Vec<Capability>,
    default_deny: bool,
}

impl Default for PolicyEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl PolicyEngine {
    pub fn new() -> Self {
        let mut engine = Self {
            capabilities: Vec::new(),
            default_deny: true,
        };
        engine.register_default_capabilities();
        engine
    }

    /// Register the built-in capability set
    fn register_default_capabilities(&mut self) {
        let defaults = vec![
            ("status_query", RiskLevel::None, "Query device status"),
            ("heartbeat", RiskLevel::None, "Send/receive heartbeat"),
            ("file_read", RiskLevel::Low, "Read files from device"),
            ("sensor_read", RiskLevel::Low, "Read sensor data"),
            ("clipboard_read", RiskLevel::Low, "Read clipboard content"),
            ("file_write", RiskLevel::Medium, "Write files to device"),
            (
                "config_change",
                RiskLevel::Medium,
                "Modify device configuration",
            ),
            ("clipboard_write", RiskLevel::Medium, "Write to clipboard"),
            ("shell_exec", RiskLevel::High, "Execute shell commands"),
            ("firmware_update", RiskLevel::High, "Update device firmware"),
            ("system_reboot", RiskLevel::High, "Reboot device"),
        ];

        for (name, risk, desc) in defaults {
            self.capabilities.push(Capability {
                name: name.to_string(),
                risk_level: risk,
                description: desc.to_string(),
            });
        }
    }

    /// Evaluate a capability request against a role
    pub fn evaluate(
        &self,
        capability_name: &str,
        role_str: &str,
    ) -> Result<PolicyDecision, EdgeClawError> {
        let role = Role::parse_role(role_str)?;

        // Find the capability
        let cap = self.capabilities.iter().find(|c| c.name == capability_name);

        match cap {
            Some(capability) => {
                let max_risk = role.max_allowed_risk();
                let allowed = (capability.risk_level as u8) <= (max_risk as u8);
                let risk_u8 = capability.risk_level as u8;

                let reason = if allowed {
                    format!(
                        "Role '{}' allowed for capability '{}' (risk level {})",
                        role_str, capability_name, risk_u8
                    )
                } else {
                    format!(
                        "Role '{}' denied for capability '{}' (risk level {} exceeds max {})",
                        role_str, capability_name, risk_u8, max_risk as u8
                    )
                };

                Ok(PolicyDecision {
                    allowed,
                    reason,
                    risk_level: risk_u8,
                })
            }
            None => {
                if self.default_deny {
                    Ok(PolicyDecision {
                        allowed: false,
                        reason: format!("Unknown capability '{}' — default deny", capability_name),
                        risk_level: 3,
                    })
                } else {
                    Ok(PolicyDecision {
                        allowed: true,
                        reason: format!(
                            "Unknown capability '{}' — default allow (not recommended)",
                            capability_name
                        ),
                        risk_level: 0,
                    })
                }
            }
        }
    }

    /// Get all registered capabilities as strings
    pub fn list_capabilities(&self) -> Vec<String> {
        self.capabilities
            .iter()
            .map(|c| {
                format!(
                    "{} (risk:{}): {}",
                    c.name, c.risk_level as u8, c.description
                )
            })
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_viewer_can_query_status() {
        let engine = PolicyEngine::new();
        let decision = engine.evaluate("status_query", "viewer").unwrap();
        assert!(decision.allowed);
        assert_eq!(decision.risk_level, 0);
    }

    #[test]
    fn test_viewer_cannot_exec_shell() {
        let engine = PolicyEngine::new();
        let decision = engine.evaluate("shell_exec", "viewer").unwrap();
        assert!(!decision.allowed);
        assert_eq!(decision.risk_level, 3);
    }

    #[test]
    fn test_operator_can_read_files() {
        let engine = PolicyEngine::new();
        let decision = engine.evaluate("file_read", "operator").unwrap();
        assert!(decision.allowed);
        assert_eq!(decision.risk_level, 1);
    }

    #[test]
    fn test_operator_cannot_write_files() {
        let engine = PolicyEngine::new();
        let decision = engine.evaluate("file_write", "operator").unwrap();
        assert!(!decision.allowed);
        assert_eq!(decision.risk_level, 2);
    }

    #[test]
    fn test_admin_can_write_files() {
        let engine = PolicyEngine::new();
        let decision = engine.evaluate("file_write", "admin").unwrap();
        assert!(decision.allowed);
    }

    #[test]
    fn test_admin_cannot_exec_shell() {
        let engine = PolicyEngine::new();
        let decision = engine.evaluate("shell_exec", "admin").unwrap();
        assert!(!decision.allowed);
    }

    #[test]
    fn test_owner_can_do_everything() {
        let engine = PolicyEngine::new();
        for cap in &[
            "status_query",
            "file_read",
            "file_write",
            "shell_exec",
            "firmware_update",
        ] {
            let decision = engine.evaluate(cap, "owner").unwrap();
            assert!(decision.allowed, "Owner should be allowed for {}", cap);
        }
    }

    #[test]
    fn test_unknown_capability_denied() {
        let engine = PolicyEngine::new();
        let decision = engine.evaluate("launch_missiles", "owner").unwrap();
        assert!(!decision.allowed);
    }

    #[test]
    fn test_invalid_role() {
        let engine = PolicyEngine::new();
        assert!(engine.evaluate("status_query", "hacker").is_err());
    }

    #[test]
    fn test_list_capabilities() {
        let engine = PolicyEngine::new();
        let caps = engine.list_capabilities();
        assert!(caps.len() >= 11);
    }
}
