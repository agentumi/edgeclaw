//! EdgeClaw Core — Zero-Trust Edge AI Orchestration Engine
//!
//! This crate implements the core engine for EdgeClaw mobile/PC agent,
//! providing identity management, encrypted sessions, ECNP protocol
//! framing, peer discovery, and policy evaluation.

pub mod ecnp;
pub mod error;
pub mod identity;
pub mod peer;
pub mod policy;
pub mod protocol;
pub mod session;

use std::sync::Mutex;

use ecnp::{EcnpCodec, EcnpMessage};
use error::EdgeClawError;
use identity::{DeviceIdentity, IdentityManager};
use peer::{PeerInfo, PeerManager};
use policy::{PolicyDecision, PolicyEngine};
use protocol::MessageType;
use session::{SessionInfo, SessionManager};

// ─── Engine config ───

/// Engine configuration
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct EngineConfig {
    pub device_name: String,
    pub device_type: String,
    pub listen_port: u16,
    pub max_connections: u32,
    pub quic_enabled: bool,
    pub log_level: String,
}

impl Default for EngineConfig {
    fn default() -> Self {
        Self {
            device_name: "edgeclaw-device".to_string(),
            device_type: "smartphone".to_string(),
            listen_port: 8443,
            max_connections: 16,
            quic_enabled: false,
            log_level: "info".to_string(),
        }
    }
}

// ─── Main Engine ───

/// Create a new EdgeClaw engine instance
pub fn create_engine(config: EngineConfig) -> Result<EdgeClawEngine, EdgeClawError> {
    EdgeClawEngine::new(config)
}

/// Main EdgeClaw engine — thread-safe, composable
pub struct EdgeClawEngine {
    config: EngineConfig,
    identity_manager: Mutex<IdentityManager>,
    session_manager: Mutex<SessionManager>,
    peer_manager: Mutex<PeerManager>,
    policy_engine: PolicyEngine,
}

impl EdgeClawEngine {
    fn new(config: EngineConfig) -> Result<Self, EdgeClawError> {
        // Initialize tracing (ignore if already set)
        let _ = tracing_subscriber::fmt()
            .with_env_filter(
                tracing_subscriber::EnvFilter::try_new(&config.log_level)
                    .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
            )
            .json()
            .try_init();

        tracing::info!(device_name = %config.device_name, "EdgeClaw engine initializing");

        Ok(Self {
            config,
            identity_manager: Mutex::new(IdentityManager::new()),
            session_manager: Mutex::new(SessionManager::new()),
            peer_manager: Mutex::new(PeerManager::new()),
            policy_engine: PolicyEngine::new(),
        })
    }

    /// Get engine configuration
    pub fn config(&self) -> &EngineConfig {
        &self.config
    }

    // ─── Identity ───

    /// Generate a new device identity (Ed25519 + X25519 keypair)
    pub fn generate_identity(&self) -> Result<DeviceIdentity, EdgeClawError> {
        let mut mgr = self
            .identity_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        mgr.generate_identity()
    }

    /// Get the current device identity
    pub fn get_identity(&self) -> Result<DeviceIdentity, EdgeClawError> {
        let mgr = self
            .identity_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        mgr.get_identity()
    }

    // ─── Peers ───

    /// Add or update a discovered peer
    pub fn add_peer(
        &self,
        peer_id: &str,
        device_name: &str,
        device_type: &str,
        address: &str,
        capabilities: Vec<String>,
    ) -> Result<PeerInfo, EdgeClawError> {
        let mut mgr = self
            .peer_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        Ok(mgr.add_peer(peer_id, device_name, device_type, address, capabilities))
    }

    /// List all known peers
    pub fn get_peers(&self) -> Vec<PeerInfo> {
        let mgr = self.peer_manager.lock().unwrap_or_else(|e| e.into_inner());
        mgr.list_peers()
    }

    /// Remove a peer by ID
    pub fn remove_peer(&self, peer_id: &str) -> Result<(), EdgeClawError> {
        let mut mgr = self
            .peer_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        mgr.remove_peer(peer_id)
    }

    // ─── Sessions ───

    /// Create an encrypted session with a peer via X25519 ECDH
    pub fn create_session(
        &self,
        peer_id: &str,
        peer_public_key: &[u8; 32],
    ) -> Result<SessionInfo, EdgeClawError> {
        let id_mgr = self
            .identity_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        let our_secret = id_mgr.get_secret_key()?;

        let mut sess_mgr = self
            .session_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        sess_mgr.create_session(peer_id, &our_secret, peer_public_key)
    }

    /// Encrypt data using a session key
    pub fn encrypt_message(
        &self,
        session_id: &str,
        plaintext: &[u8],
    ) -> Result<Vec<u8>, EdgeClawError> {
        let mut sess_mgr = self
            .session_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        sess_mgr.encrypt(session_id, plaintext)
    }

    /// Decrypt data using a session key
    pub fn decrypt_message(
        &self,
        session_id: &str,
        ciphertext: &[u8],
    ) -> Result<Vec<u8>, EdgeClawError> {
        let mut sess_mgr = self
            .session_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        sess_mgr.decrypt(session_id, ciphertext)
    }

    // ─── Protocol ───

    /// Create an ECM (Edge Capability Manifest) announcement
    pub fn create_ecm(&self) -> Result<String, EdgeClawError> {
        let id_mgr = self
            .identity_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        let identity = id_mgr.get_identity()?;

        protocol::create_ecm(
            &identity.device_id,
            &self.config.device_type,
            vec!["status".into(), "file_read".into(), "heartbeat".into()],
        )
    }

    /// Create a heartbeat message
    pub fn create_heartbeat(
        &self,
        uptime_secs: u64,
        cpu_usage: f64,
        memory_usage: f64,
    ) -> Result<String, EdgeClawError> {
        let id_mgr = self
            .identity_manager
            .lock()
            .map_err(|_| EdgeClawError::InternalError)?;
        let identity = id_mgr.get_identity()?;

        let active = self
            .session_manager
            .lock()
            .map(|s| s.active_sessions().len() as u32)
            .unwrap_or(0);

        protocol::create_heartbeat(
            &identity.device_id,
            uptime_secs,
            cpu_usage,
            memory_usage,
            active,
        )
    }

    // ─── Policy ───

    /// Evaluate a capability request
    pub fn evaluate_capability(
        &self,
        capability_name: &str,
        role: &str,
    ) -> Result<PolicyDecision, EdgeClawError> {
        self.policy_engine.evaluate(capability_name, role)
    }

    // ─── ECNP ───

    /// Encode a message into ECNP v1.1 wire format
    pub fn encode_ecnp(
        &self,
        msg_type: MessageType,
        payload: &[u8],
    ) -> Result<Vec<u8>, EdgeClawError> {
        EcnpCodec::encode(msg_type, payload)
    }

    /// Decode a message from ECNP v1.1 wire format
    pub fn decode_ecnp(&self, data: &[u8]) -> Result<EcnpMessage, EdgeClawError> {
        EcnpCodec::decode(data)
    }

    // ─── Logging ───

    /// Log an event through the tracing subsystem
    pub fn log_event(&self, level: &str, message: &str) {
        match level {
            "error" => tracing::error!(%message),
            "warn" => tracing::warn!(%message),
            "info" => tracing::info!(%message),
            "debug" => tracing::debug!(%message),
            _ => tracing::trace!(%message),
        }
    }
}

// ─── Tests ───

#[cfg(test)]
mod tests {
    use super::*;

    fn test_config() -> EngineConfig {
        EngineConfig {
            device_name: "test-device".to_string(),
            device_type: "smartphone".to_string(),
            listen_port: 8443,
            max_connections: 10,
            quic_enabled: false,
            log_level: "warn".to_string(),
        }
    }

    #[test]
    fn test_create_engine() {
        let engine = create_engine(test_config()).unwrap();
        assert!(engine.get_peers().is_empty());
    }

    #[test]
    fn test_identity_lifecycle() {
        let engine = create_engine(test_config()).unwrap();

        // Before generation, get_identity should fail
        assert!(engine.get_identity().is_err());

        let identity = engine.generate_identity().unwrap();
        assert!(!identity.device_id.is_empty());
        assert_eq!(identity.public_key_hex.len(), 64);
        assert_eq!(identity.fingerprint.len(), 16);

        let retrieved = engine.get_identity().unwrap();
        assert_eq!(identity.device_id, retrieved.device_id);
    }

    #[test]
    fn test_peer_management() {
        let engine = create_engine(test_config()).unwrap();

        engine
            .add_peer(
                "peer-001",
                "test-pc",
                "pc",
                "192.168.1.10",
                vec!["gpu".into()],
            )
            .unwrap();
        assert_eq!(engine.get_peers().len(), 1);

        engine.remove_peer("peer-001").unwrap();
        assert!(engine.get_peers().is_empty());
    }

    #[test]
    fn test_session_and_encryption() {
        let engine = create_engine(test_config()).unwrap();
        engine.generate_identity().unwrap();

        // Simulate a valid X25519 peer public key
        let peer_key: [u8; 32] = [
            9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 64,
        ];

        let session = engine.create_session("peer-001", &peer_key).unwrap();
        assert_eq!(session.peer_id, "peer-001");
        assert_eq!(session.state, "established");

        let plaintext = b"EdgeClaw test message";
        let ciphertext = engine
            .encrypt_message(&session.session_id, plaintext)
            .unwrap();
        assert_ne!(ciphertext, plaintext.to_vec());

        let decrypted = engine
            .decrypt_message(&session.session_id, &ciphertext)
            .unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_ecnp_encode_decode() {
        let engine = create_engine(test_config()).unwrap();
        let payload = b"heartbeat data";
        let encoded = engine.encode_ecnp(MessageType::Heartbeat, payload).unwrap();
        let decoded = engine.decode_ecnp(&encoded).unwrap();
        assert_eq!(decoded.version, 0x01);
        assert_eq!(decoded.msg_type, MessageType::Heartbeat as u8);
        assert_eq!(decoded.payload, payload);
    }

    #[test]
    fn test_policy_evaluation() {
        let engine = create_engine(test_config()).unwrap();

        // Viewer can query status
        let decision = engine
            .evaluate_capability("status_query", "viewer")
            .unwrap();
        assert!(decision.allowed);

        // Viewer cannot exec shell
        let decision = engine.evaluate_capability("shell_exec", "viewer").unwrap();
        assert!(!decision.allowed);

        // Owner can do everything registered
        let decision = engine.evaluate_capability("shell_exec", "owner").unwrap();
        assert!(decision.allowed);
    }

    #[test]
    fn test_ecm_creation() {
        let engine = create_engine(test_config()).unwrap();
        engine.generate_identity().unwrap();

        let ecm = engine.create_ecm().unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&ecm).unwrap();
        assert!(parsed["device_id"].is_string());
        assert_eq!(parsed["device_type"].as_str().unwrap(), "smartphone");
    }

    #[test]
    fn test_heartbeat_creation() {
        let engine = create_engine(test_config()).unwrap();
        engine.generate_identity().unwrap();

        let hb = engine.create_heartbeat(3600, 25.0, 40.0).unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&hb).unwrap();
        assert_eq!(parsed["uptime_secs"].as_u64().unwrap(), 3600);
    }

    #[test]
    fn test_engine_default_config() {
        let config = EngineConfig::default();
        assert_eq!(config.listen_port, 8443);
        assert!(!config.quic_enabled);
    }
}
