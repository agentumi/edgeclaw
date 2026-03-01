//! UniFFI bridge — exposes EdgeClaw core to Swift/Kotlin via FFI.
//!
//! This module wraps the thread-safe `EdgeClawEngine` behind a UniFFI-
//! compatible interface that uses only types representable across FFI.

use std::sync::Arc;

use crate::error::EdgeClawError;
use crate::protocol::MessageType;
use crate::{
    DeviceIdentity, EcnpMessage, EngineConfig, PeerInfo, PolicyDecision, SessionInfo,
    SyncClientConfig,
};

/// UniFFI-exported wrapper around `EdgeClawEngine`.
///
/// All methods are `&self` — the inner engine already uses `Mutex` for
/// interior mutability, making it safe to share across Swift/Kotlin threads.
pub struct EdgeClawEngineFFI {
    inner: crate::EdgeClawEngine,
}

impl EdgeClawEngineFFI {
    pub fn new(config: EngineConfig) -> Result<Self, EdgeClawError> {
        Ok(Self {
            inner: crate::EdgeClawEngine::new(config)?,
        })
    }

    // ─── Config ───

    pub fn get_config(&self) -> EngineConfig {
        self.inner.config().clone()
    }

    // ─── Identity ───

    pub fn generate_identity(&self) -> Result<DeviceIdentity, EdgeClawError> {
        self.inner.generate_identity()
    }

    pub fn get_identity(&self) -> Result<DeviceIdentity, EdgeClawError> {
        self.inner.get_identity()
    }

    // ─── Peers ───

    pub fn add_peer(
        &self,
        peer_id: String,
        device_name: String,
        device_type: String,
        address: String,
        capabilities: Vec<String>,
    ) -> Result<PeerInfo, EdgeClawError> {
        self.inner
            .add_peer(&peer_id, &device_name, &device_type, &address, capabilities)
    }

    pub fn get_peers(&self) -> Vec<PeerInfo> {
        self.inner.get_peers()
    }

    pub fn remove_peer(&self, peer_id: String) -> Result<(), EdgeClawError> {
        self.inner.remove_peer(&peer_id)
    }

    // ─── Sessions ───

    pub fn create_session(
        &self,
        peer_id: String,
        peer_public_key: Vec<u8>,
    ) -> Result<SessionInfo, EdgeClawError> {
        if peer_public_key.len() != 32 {
            return Err(EdgeClawError::InvalidParameter);
        }
        let mut key = [0u8; 32];
        key.copy_from_slice(&peer_public_key);
        self.inner.create_session(&peer_id, &key)
    }

    pub fn encrypt_message(
        &self,
        session_id: String,
        plaintext: Vec<u8>,
    ) -> Result<Vec<u8>, EdgeClawError> {
        self.inner.encrypt_message(&session_id, &plaintext)
    }

    pub fn decrypt_message(
        &self,
        session_id: String,
        ciphertext: Vec<u8>,
    ) -> Result<Vec<u8>, EdgeClawError> {
        self.inner.decrypt_message(&session_id, &ciphertext)
    }

    // ─── Protocol ───

    pub fn create_ecm(&self) -> Result<String, EdgeClawError> {
        self.inner.create_ecm()
    }

    pub fn create_heartbeat(
        &self,
        uptime_secs: u64,
        cpu_usage: f64,
        memory_usage: f64,
    ) -> Result<String, EdgeClawError> {
        self.inner.create_heartbeat(uptime_secs, cpu_usage, memory_usage)
    }

    // ─── Policy ───

    pub fn evaluate_capability(
        &self,
        capability_name: String,
        role: String,
    ) -> Result<PolicyDecision, EdgeClawError> {
        self.inner.evaluate_capability(&capability_name, &role)
    }

    // ─── ECNP ───

    pub fn encode_ecnp(
        &self,
        msg_type: u8,
        payload: Vec<u8>,
    ) -> Result<Vec<u8>, EdgeClawError> {
        let mt = MessageType::try_from(msg_type)?;
        self.inner.encode_ecnp(mt, &payload)
    }

    pub fn decode_ecnp(&self, data: Vec<u8>) -> Result<EcnpMessage, EdgeClawError> {
        self.inner.decode_ecnp(&data)
    }

    // ─── Sync ───

    pub fn init_sync(&self, config: SyncClientConfig) -> Result<(), EdgeClawError> {
        self.inner.init_sync(config)
    }

    pub fn sync_remote_exec(
        &self,
        command: String,
        args: Vec<String>,
    ) -> Result<Vec<u8>, EdgeClawError> {
        self.inner.sync_remote_exec(&command, args)
    }

    pub fn sync_process_incoming(&self, frame: Vec<u8>) -> Result<String, EdgeClawError> {
        let msg = self.inner.sync_process_incoming(&frame)?;
        serde_json::to_string(&msg).map_err(|_| EdgeClawError::SerializationError)
    }

    pub fn sync_is_connected(&self) -> bool {
        self.inner.sync_is_connected()
    }

    pub fn sync_shutdown(&self) -> Result<(), EdgeClawError> {
        self.inner.sync_shutdown()
    }

    // ─── Logging ───

    pub fn log_event(&self, level: String, message: String) {
        self.inner.log_event(&level, &message)
    }
}

/// Top-level factory function exported by UniFFI.
pub fn create_engine(config: EngineConfig) -> Result<Arc<EdgeClawEngineFFI>, EdgeClawError> {
    Ok(Arc::new(EdgeClawEngineFFI::new(config)?))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_config() -> EngineConfig {
        EngineConfig {
            device_name: "ffi-test".to_string(),
            device_type: "smartphone".to_string(),
            listen_port: 8443,
            max_connections: 8,
            quic_enabled: false,
            log_level: "warn".to_string(),
        }
    }

    #[test]
    fn test_ffi_create_engine() {
        let engine = create_engine(test_config()).unwrap();
        let cfg = engine.get_config();
        assert_eq!(cfg.device_name, "ffi-test");
    }

    #[test]
    fn test_ffi_identity_roundtrip() {
        let engine = create_engine(test_config()).unwrap();
        assert!(engine.get_identity().is_err());
        let id = engine.generate_identity().unwrap();
        assert!(!id.device_id.is_empty());
        let id2 = engine.get_identity().unwrap();
        assert_eq!(id.device_id, id2.device_id);
    }

    #[test]
    fn test_ffi_peer_ops() {
        let engine = create_engine(test_config()).unwrap();
        engine
            .add_peer(
                "p1".into(),
                "desk".into(),
                "pc".into(),
                "10.0.0.1".into(),
                vec!["gpu".into()],
            )
            .unwrap();
        assert_eq!(engine.get_peers().len(), 1);
        engine.remove_peer("p1".into()).unwrap();
        assert!(engine.get_peers().is_empty());
    }

    #[test]
    fn test_ffi_session_bad_key_len() {
        let engine = create_engine(test_config()).unwrap();
        engine.generate_identity().unwrap();
        // 16 bytes instead of 32 → InvalidParameter
        let result = engine.create_session("peer".into(), vec![0u8; 16]);
        assert!(result.is_err());
    }

    #[test]
    fn test_ffi_policy() {
        let engine = create_engine(test_config()).unwrap();
        let d = engine
            .evaluate_capability("status_query".into(), "viewer".into())
            .unwrap();
        assert!(d.allowed);
    }

    #[test]
    fn test_ffi_ecnp_roundtrip() {
        let engine = create_engine(test_config()).unwrap();
        let encoded = engine.encode_ecnp(0x04, b"test".to_vec()).unwrap();
        let decoded = engine.decode_ecnp(encoded).unwrap();
        assert_eq!(decoded.payload, b"test");
    }

    #[test]
    fn test_ffi_sync_lifecycle() {
        let engine = create_engine(test_config()).unwrap();
        let cfg = SyncClientConfig::default();
        engine.init_sync(cfg).unwrap();
        assert!(!engine.sync_is_connected());
        engine.sync_shutdown().unwrap();
    }

    #[test]
    fn test_ffi_log_event() {
        let engine = create_engine(test_config()).unwrap();
        engine.log_event("info".into(), "test log".into());
        // No panic = success
    }
}
