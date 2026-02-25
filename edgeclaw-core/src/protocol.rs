use serde::{Deserialize, Serialize};

use crate::error::EdgeClawError;

// ─── ECNP v1.1 Message Types ───

/// ECNP message type codes
#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum MessageType {
    Handshake = 0x01,
    Data = 0x02,
    Control = 0x03,
    Heartbeat = 0x04,
    Ack = 0x05,
    Error = 0x06,
}

impl TryFrom<u8> for MessageType {
    type Error = EdgeClawError;

    fn try_from(v: u8) -> Result<Self, EdgeClawError> {
        match v {
            0x01 => Ok(MessageType::Handshake),
            0x02 => Ok(MessageType::Data),
            0x03 => Ok(MessageType::Control),
            0x04 => Ok(MessageType::Heartbeat),
            0x05 => Ok(MessageType::Ack),
            0x06 => Ok(MessageType::Error),
            _ => Err(EdgeClawError::InvalidParameter),
        }
    }
}

// ─── ECM (Edge Capability Manifest) ───

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EcmPayload {
    pub device_id: String,
    pub device_type: String,
    pub capabilities: Vec<String>,
    pub os: String,
    pub version: String,
}

// ─── EAP (Edge Automation Profile) ───

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EapPayload {
    pub profile_id: String,
    pub name: String,
    pub actions: Vec<EapAction>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EapAction {
    pub action_type: String,
    pub target: String,
    pub parameters: serde_json::Value,
}

// ─── Heartbeat ───

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HeartbeatPayload {
    pub device_id: String,
    pub uptime_secs: u64,
    pub cpu_usage: f64,
    pub memory_usage: f64,
    pub active_sessions: u32,
}

// ─── Protocol message constructors ───

/// Create an ECM announcement JSON string
pub fn create_ecm(
    device_id: &str,
    device_type: &str,
    capabilities: Vec<String>,
) -> Result<String, EdgeClawError> {
    let ecm = EcmPayload {
        device_id: device_id.to_string(),
        device_type: device_type.to_string(),
        capabilities,
        os: std::env::consts::OS.to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
    };
    serde_json::to_string(&ecm).map_err(EdgeClawError::from)
}

/// Create an EAP (automation profile) JSON string
pub fn create_eap(
    profile_id: &str,
    name: &str,
    actions: Vec<(String, String, serde_json::Value)>,
) -> Result<String, EdgeClawError> {
    let eap = EapPayload {
        profile_id: profile_id.to_string(),
        name: name.to_string(),
        actions: actions
            .into_iter()
            .map(|(action_type, target, parameters)| EapAction {
                action_type,
                target,
                parameters,
            })
            .collect(),
    };
    serde_json::to_string(&eap).map_err(EdgeClawError::from)
}

/// Create a heartbeat JSON string
pub fn create_heartbeat(
    device_id: &str,
    uptime_secs: u64,
    cpu_usage: f64,
    memory_usage: f64,
    active_sessions: u32,
) -> Result<String, EdgeClawError> {
    let hb = HeartbeatPayload {
        device_id: device_id.to_string(),
        uptime_secs,
        cpu_usage,
        memory_usage,
        active_sessions,
    };
    serde_json::to_string(&hb).map_err(EdgeClawError::from)
}

/// Parse an ECM announcement from JSON
pub fn parse_ecm(json: &str) -> Result<EcmPayload, EdgeClawError> {
    serde_json::from_str(json).map_err(EdgeClawError::from)
}

/// Parse an EAP from JSON
pub fn parse_eap(json: &str) -> Result<EapPayload, EdgeClawError> {
    serde_json::from_str(json).map_err(EdgeClawError::from)
}

/// Parse a heartbeat from JSON
pub fn parse_heartbeat(json: &str) -> Result<HeartbeatPayload, EdgeClawError> {
    serde_json::from_str(json).map_err(EdgeClawError::from)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ecm_roundtrip() {
        let json = create_ecm(
            "device-001",
            "smartphone",
            vec!["camera".into(), "gps".into()],
        )
        .unwrap();

        let parsed = parse_ecm(&json).unwrap();
        assert_eq!(parsed.device_id, "device-001");
        assert_eq!(parsed.capabilities.len(), 2);
    }

    #[test]
    fn test_eap_roundtrip() {
        let actions = vec![(
            "file_transfer".into(),
            "peer-1".into(),
            serde_json::json!({"path": "/tmp/test.txt"}),
        )];
        let json = create_eap("profile-1", "test-profile", actions).unwrap();

        let parsed = parse_eap(&json).unwrap();
        assert_eq!(parsed.profile_id, "profile-1");
        assert_eq!(parsed.actions.len(), 1);
        assert_eq!(parsed.actions[0].action_type, "file_transfer");
    }

    #[test]
    fn test_heartbeat_roundtrip() {
        let json = create_heartbeat("device-001", 3600, 45.5, 60.0, 3).unwrap();

        let parsed = parse_heartbeat(&json).unwrap();
        assert_eq!(parsed.device_id, "device-001");
        assert_eq!(parsed.uptime_secs, 3600);
        assert!((parsed.cpu_usage - 45.5).abs() < f64::EPSILON);
        assert_eq!(parsed.active_sessions, 3);
    }

    #[test]
    fn test_message_type_conversion() {
        assert_eq!(MessageType::try_from(0x01).unwrap(), MessageType::Handshake);
        assert_eq!(MessageType::try_from(0x04).unwrap(), MessageType::Heartbeat);
        assert!(MessageType::try_from(0xFF).is_err());
    }
}
