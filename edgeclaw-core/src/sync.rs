//! Desktop-Mobile synchronization client.
//!
//! Provides `SyncClient` for TCP-based synchronization with an EdgeClaw
//! Desktop agent, supporting config sync, status push, and remote execution.

use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;

use crate::ecnp::{EcnpCodec, EcnpMessage};
use crate::error::EdgeClawError;
use crate::protocol::MessageType;

// ─── Sync message type codes (0x10–0x1F reserved) ───

/// Sub-type codes carried inside ECNP Data frames
pub const SYNC_CONFIG: u8 = 0x10;
pub const SYNC_REMOTE_EXEC: u8 = 0x11;
pub const SYNC_STATUS_PUSH: u8 = 0x12;
pub const SYNC_REMOTE_EXEC_RESULT: u8 = 0x13;

// ─── Sync message payloads ───

/// Synchronization message — wraps the three sync sub-types.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum SyncMessage {
    /// Desktop → Mobile: configuration update
    #[serde(rename = "config_sync")]
    ConfigSync {
        config_hash: String,
        config_data: String,
    },

    /// Mobile → Desktop: request remote command execution
    #[serde(rename = "remote_exec")]
    RemoteExec { command: String, args: Vec<String> },

    /// Desktop → Mobile: system status push
    #[serde(rename = "status_push")]
    StatusPush {
        cpu_usage: f64,
        memory_usage: f64,
        disk_usage: f64,
        uptime_secs: u64,
        active_sessions: u32,
        ai_status: String,
    },

    /// Desktop → Mobile: remote execution result
    #[serde(rename = "remote_exec_result")]
    RemoteExecResult {
        command: String,
        exit_code: i32,
        stdout: String,
        stderr: String,
    },
}

impl SyncMessage {
    /// Serialize to JSON bytes
    pub fn to_bytes(&self) -> Result<Vec<u8>, EdgeClawError> {
        serde_json::to_vec(self).map_err(EdgeClawError::from)
    }

    /// Deserialize from JSON bytes
    pub fn from_bytes(data: &[u8]) -> Result<Self, EdgeClawError> {
        serde_json::from_slice(data).map_err(EdgeClawError::from)
    }

    /// Get the sync sub-type code for ECNP framing
    pub fn sync_type_code(&self) -> u8 {
        match self {
            SyncMessage::ConfigSync { .. } => SYNC_CONFIG,
            SyncMessage::RemoteExec { .. } => SYNC_REMOTE_EXEC,
            SyncMessage::StatusPush { .. } => SYNC_STATUS_PUSH,
            SyncMessage::RemoteExecResult { .. } => SYNC_REMOTE_EXEC_RESULT,
        }
    }

    /// Encode into an ECNP Data frame (with sync sub-type prefix)
    pub fn encode_ecnp(&self) -> Result<Vec<u8>, EdgeClawError> {
        let json_bytes = self.to_bytes()?;
        // Prefix the payload with the sync sub-type byte
        let mut payload = Vec::with_capacity(1 + json_bytes.len());
        payload.push(self.sync_type_code());
        payload.extend_from_slice(&json_bytes);
        EcnpCodec::encode(MessageType::Data, &payload)
    }

    /// Decode from an ECNP Data frame
    pub fn decode_ecnp(frame: &[u8]) -> Result<(u8, Self), EdgeClawError> {
        let msg: EcnpMessage = EcnpCodec::decode(frame)?;
        if msg.msg_type != MessageType::Data as u8 {
            return Err(EdgeClawError::InvalidParameter);
        }
        if msg.payload.is_empty() {
            return Err(EdgeClawError::InvalidParameter);
        }
        let sync_type = msg.payload[0];
        let sync_msg = Self::from_bytes(&msg.payload[1..])?;
        Ok((sync_type, sync_msg))
    }
}

// ─── Connection state ───

/// Connection state for the sync client
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SyncConnectionState {
    Disconnected,
    Connecting,
    Handshaking,
    Connected,
    Syncing,
    Error,
}

impl std::fmt::Display for SyncConnectionState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Disconnected => write!(f, "disconnected"),
            Self::Connecting => write!(f, "connecting"),
            Self::Handshaking => write!(f, "handshaking"),
            Self::Connected => write!(f, "connected"),
            Self::Syncing => write!(f, "syncing"),
            Self::Error => write!(f, "error"),
        }
    }
}

// ─── Sync client configuration ───

/// Configuration for the sync client
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncClientConfig {
    /// Desktop agent address (e.g. "192.168.1.100:8443")
    pub desktop_address: String,
    /// How often to send heartbeats (seconds)
    pub heartbeat_interval_secs: u64,
    /// Status push receive interval (seconds)
    pub status_interval_secs: u64,
    /// Connection timeout (seconds)
    pub connect_timeout_secs: u64,
    /// Auto-reconnect on disconnect
    pub auto_reconnect: bool,
    /// Maximum reconnect attempts (0 = unlimited)
    pub max_reconnect_attempts: u32,
}

impl Default for SyncClientConfig {
    fn default() -> Self {
        Self {
            desktop_address: "127.0.0.1:8443".to_string(),
            heartbeat_interval_secs: 30,
            status_interval_secs: 30,
            connect_timeout_secs: 10,
            auto_reconnect: true,
            max_reconnect_attempts: 0,
        }
    }
}

// ─── Sync client stats ───

/// Runtime statistics for the sync client
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncStats {
    pub messages_sent: u64,
    pub messages_received: u64,
    pub reconnect_count: u32,
    pub last_config_hash: Option<String>,
    pub last_status_push: Option<String>,
}

// ─── Sync Client ───

/// TCP-based synchronization client for Desktop-Mobile communication.
///
/// Handles the mobile side of the Desktop-Mobile sync protocol:
/// - Config synchronization (receive from Desktop)
/// - Status push (receive from Desktop)
/// - Remote execution (send to Desktop, receive result)
///
/// # Usage
/// ```rust,ignore
/// let config = SyncClientConfig {
///     desktop_address: "192.168.1.100:8443".into(),
///     ..Default::default()
/// };
/// let client = SyncClient::new(config);
/// // In async context: client.connect().await
/// ```
pub struct SyncClient {
    config: SyncClientConfig,
    state: Arc<std::sync::Mutex<SyncConnectionState>>,
    connected: Arc<AtomicBool>,
    messages_sent: Arc<AtomicU64>,
    messages_received: Arc<AtomicU64>,
    reconnect_count: Arc<std::sync::atomic::AtomicU32>,
    last_config_hash: Arc<std::sync::Mutex<Option<String>>>,
    last_status: Arc<std::sync::Mutex<Option<SyncMessage>>>,
    shutdown: Arc<AtomicBool>,
}

impl SyncClient {
    /// Create a new sync client
    pub fn new(config: SyncClientConfig) -> Self {
        Self {
            config,
            state: Arc::new(std::sync::Mutex::new(SyncConnectionState::Disconnected)),
            connected: Arc::new(AtomicBool::new(false)),
            messages_sent: Arc::new(AtomicU64::new(0)),
            messages_received: Arc::new(AtomicU64::new(0)),
            reconnect_count: Arc::new(std::sync::atomic::AtomicU32::new(0)),
            last_config_hash: Arc::new(std::sync::Mutex::new(None)),
            last_status: Arc::new(std::sync::Mutex::new(None)),
            shutdown: Arc::new(AtomicBool::new(false)),
        }
    }

    /// Get current connection state
    pub fn state(&self) -> SyncConnectionState {
        *self.state.lock().unwrap_or_else(|e| e.into_inner())
    }

    /// Check if connected
    pub fn is_connected(&self) -> bool {
        self.connected.load(Ordering::Relaxed)
    }

    /// Get the desktop address
    pub fn desktop_address(&self) -> &str {
        &self.config.desktop_address
    }

    /// Get runtime statistics
    pub fn stats(&self) -> SyncStats {
        SyncStats {
            messages_sent: self.messages_sent.load(Ordering::Relaxed),
            messages_received: self.messages_received.load(Ordering::Relaxed),
            reconnect_count: self.reconnect_count.load(Ordering::Relaxed),
            last_config_hash: self
                .last_config_hash
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .clone(),
            last_status_push: self
                .last_status
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .as_ref()
                .map(|s| serde_json::to_string(s).unwrap_or_default()),
        }
    }

    /// Initiate TCP connection to the desktop agent.
    ///
    /// Performs:
    /// 1. TCP connect with timeout
    /// 2. ECNP handshake (send Handshake frame, expect Ack)
    /// 3. Transition to Connected state
    pub async fn connect(&self) -> Result<(), EdgeClawError> {
        self.set_state(SyncConnectionState::Connecting);

        let addr = self
            .config
            .desktop_address
            .parse::<std::net::SocketAddr>()
            .map_err(|_| EdgeClawError::InvalidParameter)?;

        let timeout = std::time::Duration::from_secs(self.config.connect_timeout_secs);

        let stream = tokio::time::timeout(timeout, tokio::net::TcpStream::connect(addr))
            .await
            .map_err(|_| EdgeClawError::TimeoutError)?
            .map_err(|_| EdgeClawError::ConnectionError)?;

        // Send ECNP handshake
        self.set_state(SyncConnectionState::Handshaking);

        let handshake_payload = serde_json::json!({
            "protocol": "ecnp",
            "version": "1.1",
            "client_type": "mobile",
            "capabilities": ["config_sync", "remote_exec", "status_push"]
        });
        let handshake_data = serde_json::to_vec(&handshake_payload)
            .map_err(|_| EdgeClawError::SerializationError)?;
        let frame = EcnpCodec::encode(MessageType::Handshake, &handshake_data)?;

        use tokio::io::AsyncWriteExt;
        let mut stream = stream;
        stream
            .write_all(&frame)
            .await
            .map_err(|_| EdgeClawError::ConnectionError)?;
        stream
            .flush()
            .await
            .map_err(|_| EdgeClawError::ConnectionError)?;

        // Read handshake ack
        use tokio::io::AsyncReadExt;
        let mut header_buf = [0u8; 6]; // ECNP header
        tokio::time::timeout(timeout, stream.read_exact(&mut header_buf))
            .await
            .map_err(|_| EdgeClawError::TimeoutError)?
            .map_err(|_| EdgeClawError::ConnectionError)?;

        let payload_len =
            u32::from_be_bytes([header_buf[2], header_buf[3], header_buf[4], header_buf[5]])
                as usize;
        let mut payload_buf = vec![0u8; payload_len];
        if payload_len > 0 {
            tokio::time::timeout(timeout, stream.read_exact(&mut payload_buf))
                .await
                .map_err(|_| EdgeClawError::TimeoutError)?
                .map_err(|_| EdgeClawError::ConnectionError)?;
        }

        // Validate ack
        if header_buf[1] != MessageType::Ack as u8 {
            self.set_state(SyncConnectionState::Error);
            return Err(EdgeClawError::ConnectionError);
        }

        self.connected.store(true, Ordering::Relaxed);
        self.set_state(SyncConnectionState::Connected);
        tracing::info!(addr = %self.config.desktop_address, "Sync client connected");
        Ok(())
    }

    /// Create a RemoteExec sync message
    pub fn create_remote_exec(
        &self,
        command: &str,
        args: Vec<String>,
    ) -> Result<Vec<u8>, EdgeClawError> {
        let msg = SyncMessage::RemoteExec {
            command: command.to_string(),
            args,
        };
        let frame = msg.encode_ecnp()?;
        self.messages_sent.fetch_add(1, Ordering::Relaxed);
        Ok(frame)
    }

    /// Process a received sync message
    pub fn process_incoming(&self, frame: &[u8]) -> Result<SyncMessage, EdgeClawError> {
        let (_sync_type, msg) = SyncMessage::decode_ecnp(frame)?;
        self.messages_received.fetch_add(1, Ordering::Relaxed);

        match &msg {
            SyncMessage::ConfigSync { config_hash, .. } => {
                if let Ok(mut hash) = self.last_config_hash.lock() {
                    *hash = Some(config_hash.clone());
                }
                tracing::info!(config_hash = %config_hash, "Config sync received");
            }
            SyncMessage::StatusPush { .. } => {
                if let Ok(mut status) = self.last_status.lock() {
                    *status = Some(msg.clone());
                }
                tracing::info!("Status push received");
            }
            SyncMessage::RemoteExecResult {
                command, exit_code, ..
            } => {
                tracing::info!(command = %command, exit_code = %exit_code, "Remote exec result received");
            }
            _ => {}
        }

        Ok(msg)
    }

    /// Request shutdown
    pub fn shutdown(&self) {
        self.shutdown.store(true, Ordering::Relaxed);
        self.connected.store(false, Ordering::Relaxed);
        self.set_state(SyncConnectionState::Disconnected);
        tracing::info!("Sync client shutdown requested");
    }

    /// Check if shutdown was requested
    pub fn is_shutdown(&self) -> bool {
        self.shutdown.load(Ordering::Relaxed)
    }

    fn set_state(&self, new_state: SyncConnectionState) {
        if let Ok(mut state) = self.state.lock() {
            *state = new_state;
        }
    }
}

// ─── Transport switch helper ───

/// Transport preference for Desktop connection
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub enum TransportPreference {
    /// BLE for proximity, TCP for data
    BleFirst,
    /// TCP/WiFi LAN direct
    TcpLan,
    /// Auto-detect: BLE discovery → TCP switch
    #[default]
    Auto,
}

/// Connection strategy result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConnectionStrategy {
    pub transport: TransportPreference,
    pub desktop_address: Option<String>,
    pub ble_device_id: Option<String>,
    pub should_use_tcp: bool,
}

/// Determines the best connection strategy based on available transports.
///
/// In Auto mode:
/// - If BLE device is nearby (discovered), use BLE for initial handshake
/// - Then switch to TCP/LAN for data transfer
/// - Fall back to TCP if BLE not available
pub fn determine_connection_strategy(
    preference: TransportPreference,
    ble_device_available: bool,
    lan_address: Option<&str>,
) -> ConnectionStrategy {
    match preference {
        TransportPreference::BleFirst => ConnectionStrategy {
            transport: TransportPreference::BleFirst,
            desktop_address: lan_address.map(|s| s.to_string()),
            ble_device_id: None,
            should_use_tcp: !ble_device_available,
        },
        TransportPreference::TcpLan => ConnectionStrategy {
            transport: TransportPreference::TcpLan,
            desktop_address: lan_address.map(|s| s.to_string()),
            ble_device_id: None,
            should_use_tcp: true,
        },
        TransportPreference::Auto => {
            if ble_device_available && lan_address.is_some() {
                // BLE for discovery, TCP for data
                ConnectionStrategy {
                    transport: TransportPreference::Auto,
                    desktop_address: lan_address.map(|s| s.to_string()),
                    ble_device_id: None,
                    should_use_tcp: true, // switch to TCP after BLE discovery
                }
            } else if lan_address.is_some() {
                // TCP only
                ConnectionStrategy {
                    transport: TransportPreference::TcpLan,
                    desktop_address: lan_address.map(|s| s.to_string()),
                    ble_device_id: None,
                    should_use_tcp: true,
                }
            } else {
                // BLE only or nothing
                ConnectionStrategy {
                    transport: TransportPreference::BleFirst,
                    desktop_address: None,
                    ble_device_id: None,
                    should_use_tcp: false,
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // ─── SyncMessage tests ───

    #[test]
    fn test_config_sync_roundtrip() {
        let msg = SyncMessage::ConfigSync {
            config_hash: "abc123".to_string(),
            config_data: r#"{"agent":{"name":"test"}}"#.to_string(),
        };

        let bytes = msg.to_bytes().unwrap();
        let decoded = SyncMessage::from_bytes(&bytes).unwrap();
        match decoded {
            SyncMessage::ConfigSync {
                config_hash,
                config_data,
            } => {
                assert_eq!(config_hash, "abc123");
                assert_eq!(config_data, r#"{"agent":{"name":"test"}}"#);
            }
            _ => panic!("Expected ConfigSync"),
        }
    }

    #[test]
    fn test_remote_exec_roundtrip() {
        let msg = SyncMessage::RemoteExec {
            command: "systemctl".to_string(),
            args: vec!["status".into(), "nginx".into()],
        };

        let bytes = msg.to_bytes().unwrap();
        let decoded = SyncMessage::from_bytes(&bytes).unwrap();
        match decoded {
            SyncMessage::RemoteExec { command, args } => {
                assert_eq!(command, "systemctl");
                assert_eq!(args, vec!["status", "nginx"]);
            }
            _ => panic!("Expected RemoteExec"),
        }
    }

    #[test]
    fn test_status_push_roundtrip() {
        let msg = SyncMessage::StatusPush {
            cpu_usage: 45.5,
            memory_usage: 60.0,
            disk_usage: 72.3,
            uptime_secs: 86400,
            active_sessions: 3,
            ai_status: "ollama:ready".to_string(),
        };

        let bytes = msg.to_bytes().unwrap();
        let decoded = SyncMessage::from_bytes(&bytes).unwrap();
        match decoded {
            SyncMessage::StatusPush {
                cpu_usage,
                uptime_secs,
                ai_status,
                ..
            } => {
                assert!((cpu_usage - 45.5).abs() < f64::EPSILON);
                assert_eq!(uptime_secs, 86400);
                assert_eq!(ai_status, "ollama:ready");
            }
            _ => panic!("Expected StatusPush"),
        }
    }

    #[test]
    fn test_remote_exec_result_roundtrip() {
        let msg = SyncMessage::RemoteExecResult {
            command: "hostname".to_string(),
            exit_code: 0,
            stdout: "edgeclaw-pc\n".to_string(),
            stderr: String::new(),
        };

        let bytes = msg.to_bytes().unwrap();
        let decoded = SyncMessage::from_bytes(&bytes).unwrap();
        match decoded {
            SyncMessage::RemoteExecResult {
                command,
                exit_code,
                stdout,
                stderr,
            } => {
                assert_eq!(command, "hostname");
                assert_eq!(exit_code, 0);
                assert_eq!(stdout, "edgeclaw-pc\n");
                assert!(stderr.is_empty());
            }
            _ => panic!("Expected RemoteExecResult"),
        }
    }

    #[test]
    fn test_sync_type_codes() {
        let config = SyncMessage::ConfigSync {
            config_hash: "h".into(),
            config_data: "d".into(),
        };
        assert_eq!(config.sync_type_code(), SYNC_CONFIG);

        let exec = SyncMessage::RemoteExec {
            command: "ls".into(),
            args: vec![],
        };
        assert_eq!(exec.sync_type_code(), SYNC_REMOTE_EXEC);

        let status = SyncMessage::StatusPush {
            cpu_usage: 0.0,
            memory_usage: 0.0,
            disk_usage: 0.0,
            uptime_secs: 0,
            active_sessions: 0,
            ai_status: String::new(),
        };
        assert_eq!(status.sync_type_code(), SYNC_STATUS_PUSH);

        let result = SyncMessage::RemoteExecResult {
            command: String::new(),
            exit_code: 0,
            stdout: String::new(),
            stderr: String::new(),
        };
        assert_eq!(result.sync_type_code(), SYNC_REMOTE_EXEC_RESULT);
    }

    // ─── ECNP encoding tests ───

    #[test]
    fn test_sync_message_ecnp_encode_decode() {
        let msg = SyncMessage::RemoteExec {
            command: "uptime".to_string(),
            args: vec![],
        };

        let frame = msg.encode_ecnp().unwrap();
        let (sync_type, decoded) = SyncMessage::decode_ecnp(&frame).unwrap();

        assert_eq!(sync_type, SYNC_REMOTE_EXEC);
        match decoded {
            SyncMessage::RemoteExec { command, args } => {
                assert_eq!(command, "uptime");
                assert!(args.is_empty());
            }
            _ => panic!("Expected RemoteExec"),
        }
    }

    #[test]
    fn test_ecnp_decode_wrong_type_fails() {
        // Encode as Heartbeat (not Data) — should fail sync decode
        let payload = b"dummy";
        let frame = EcnpCodec::encode(MessageType::Heartbeat, payload).unwrap();
        assert!(SyncMessage::decode_ecnp(&frame).is_err());
    }

    // ─── SyncClient tests ───

    #[test]
    fn test_sync_client_creation() {
        let config = SyncClientConfig::default();
        let client = SyncClient::new(config);

        assert_eq!(client.state(), SyncConnectionState::Disconnected);
        assert!(!client.is_connected());
        assert_eq!(client.desktop_address(), "127.0.0.1:8443");
    }

    #[test]
    fn test_sync_client_stats() {
        let client = SyncClient::new(SyncClientConfig::default());
        let stats = client.stats();
        assert_eq!(stats.messages_sent, 0);
        assert_eq!(stats.messages_received, 0);
        assert_eq!(stats.reconnect_count, 0);
        assert!(stats.last_config_hash.is_none());
    }

    #[test]
    fn test_sync_client_create_remote_exec() {
        let client = SyncClient::new(SyncClientConfig::default());
        let frame = client.create_remote_exec("hostname", vec![]).unwrap();

        // Should be a valid ECNP frame
        let (sync_type, msg) = SyncMessage::decode_ecnp(&frame).unwrap();
        assert_eq!(sync_type, SYNC_REMOTE_EXEC);
        match msg {
            SyncMessage::RemoteExec { command, .. } => assert_eq!(command, "hostname"),
            _ => panic!("Expected RemoteExec"),
        }

        // Stats should be updated
        assert_eq!(client.stats().messages_sent, 1);
    }

    #[test]
    fn test_sync_client_process_config_sync() {
        let client = SyncClient::new(SyncClientConfig::default());
        let msg = SyncMessage::ConfigSync {
            config_hash: "sha256:abc".to_string(),
            config_data: r#"{"agent":{"name":"pc"}}"#.to_string(),
        };
        let frame = msg.encode_ecnp().unwrap();

        let result = client.process_incoming(&frame).unwrap();
        match result {
            SyncMessage::ConfigSync { config_hash, .. } => {
                assert_eq!(config_hash, "sha256:abc");
            }
            _ => panic!("Expected ConfigSync"),
        }

        assert_eq!(client.stats().messages_received, 1);
        assert_eq!(
            client.stats().last_config_hash,
            Some("sha256:abc".to_string())
        );
    }

    #[test]
    fn test_sync_client_process_status_push() {
        let client = SyncClient::new(SyncClientConfig::default());
        let msg = SyncMessage::StatusPush {
            cpu_usage: 10.0,
            memory_usage: 50.0,
            disk_usage: 30.0,
            uptime_secs: 7200,
            active_sessions: 2,
            ai_status: "ollama:running".to_string(),
        };
        let frame = msg.encode_ecnp().unwrap();

        let result = client.process_incoming(&frame).unwrap();
        match result {
            SyncMessage::StatusPush { uptime_secs, .. } => {
                assert_eq!(uptime_secs, 7200);
            }
            _ => panic!("Expected StatusPush"),
        }

        assert!(client.stats().last_status_push.is_some());
    }

    #[test]
    fn test_sync_client_shutdown() {
        let client = SyncClient::new(SyncClientConfig::default());
        assert!(!client.is_shutdown());

        client.shutdown();
        assert!(client.is_shutdown());
        assert!(!client.is_connected());
        assert_eq!(client.state(), SyncConnectionState::Disconnected);
    }

    // ─── Connection strategy tests ───

    #[test]
    fn test_connection_strategy_auto_both() {
        let strategy = determine_connection_strategy(
            TransportPreference::Auto,
            true,
            Some("192.168.1.10:8443"),
        );
        assert!(strategy.should_use_tcp);
        assert_eq!(strategy.transport, TransportPreference::Auto);
        assert_eq!(
            strategy.desktop_address,
            Some("192.168.1.10:8443".to_string())
        );
    }

    #[test]
    fn test_connection_strategy_auto_tcp_only() {
        let strategy =
            determine_connection_strategy(TransportPreference::Auto, false, Some("10.0.0.5:8443"));
        assert!(strategy.should_use_tcp);
        assert_eq!(strategy.transport, TransportPreference::TcpLan);
    }

    #[test]
    fn test_connection_strategy_auto_ble_only() {
        let strategy = determine_connection_strategy(TransportPreference::Auto, true, None);
        assert!(!strategy.should_use_tcp);
        assert_eq!(strategy.transport, TransportPreference::BleFirst);
    }

    #[test]
    fn test_connection_strategy_explicit_tcp() {
        let strategy = determine_connection_strategy(
            TransportPreference::TcpLan,
            true,
            Some("192.168.1.1:8443"),
        );
        assert!(strategy.should_use_tcp);
        assert_eq!(strategy.transport, TransportPreference::TcpLan);
    }

    #[test]
    fn test_connection_strategy_ble_first_no_ble() {
        let strategy = determine_connection_strategy(
            TransportPreference::BleFirst,
            false,
            Some("192.168.1.1:8443"),
        );
        // Falls back to TCP when BLE not available
        assert!(strategy.should_use_tcp);
    }

    #[tokio::test]
    async fn test_sync_client_connect_invalid_addr() {
        let config = SyncClientConfig {
            desktop_address: "not-a-valid-addr".to_string(),
            connect_timeout_secs: 1,
            ..Default::default()
        };
        let client = SyncClient::new(config);
        let result = client.connect().await;
        assert!(result.is_err());
    }

    #[test]
    fn test_sync_connection_state_display() {
        assert_eq!(
            SyncConnectionState::Disconnected.to_string(),
            "disconnected"
        );
        assert_eq!(SyncConnectionState::Connecting.to_string(), "connecting");
        assert_eq!(SyncConnectionState::Handshaking.to_string(), "handshaking");
        assert_eq!(SyncConnectionState::Connected.to_string(), "connected");
        assert_eq!(SyncConnectionState::Syncing.to_string(), "syncing");
        assert_eq!(SyncConnectionState::Error.to_string(), "error");
    }

    #[test]
    fn test_sync_client_config_default() {
        let config = SyncClientConfig::default();
        assert_eq!(config.desktop_address, "127.0.0.1:8443");
        assert_eq!(config.heartbeat_interval_secs, 30);
        assert_eq!(config.status_interval_secs, 30);
        assert_eq!(config.connect_timeout_secs, 10);
        assert!(config.auto_reconnect);
        assert_eq!(config.max_reconnect_attempts, 0);
    }

    #[test]
    fn test_transport_preference_default() {
        let pref = TransportPreference::default();
        assert_eq!(pref, TransportPreference::Auto);
    }
}
