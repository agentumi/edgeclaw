use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use hkdf::Hkdf;
use sha2::Sha256;
use x25519_dalek::{PublicKey, StaticSecret};

use crate::error::EdgeClawError;

/// Session information exposed via UniFFI
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SessionInfo {
    pub session_id: String,
    pub peer_id: String,
    pub state: String,
    pub created_at: String,
    pub expires_at: String,
    pub messages_sent: u64,
    pub messages_received: u64,
}

/// Internal session state
#[derive(Debug, Clone, PartialEq)]
pub enum SessionState {
    Initiating,
    Established,
    Expired,
}

impl std::fmt::Display for SessionState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            SessionState::Initiating => write!(f, "initiating"),
            SessionState::Established => write!(f, "established"),
            SessionState::Expired => write!(f, "expired"),
        }
    }
}

/// Secure session with X25519 ECDH + AES-256-GCM
struct Session {
    session_id: String,
    peer_id: String,
    state: SessionState,
    session_key: [u8; 32],
    nonce_counter: u64,
    created_at: chrono::DateTime<chrono::Utc>,
    expires_at: chrono::DateTime<chrono::Utc>,
    messages_sent: u64,
    messages_received: u64,
}

impl Session {
    fn to_info(&self) -> SessionInfo {
        SessionInfo {
            session_id: self.session_id.clone(),
            peer_id: self.peer_id.clone(),
            state: self.state.to_string(),
            created_at: self.created_at.to_rfc3339(),
            expires_at: self.expires_at.to_rfc3339(),
            messages_sent: self.messages_sent,
            messages_received: self.messages_received,
        }
    }

    fn is_expired(&self) -> bool {
        chrono::Utc::now() >= self.expires_at
    }
}

/// Session manager: handles key exchange, session creation, encrypt/decrypt
pub struct SessionManager {
    sessions: std::collections::HashMap<String, Session>,
    session_duration_secs: i64,
}

impl Default for SessionManager {
    fn default() -> Self {
        Self::new()
    }
}

impl SessionManager {
    pub fn new() -> Self {
        Self {
            sessions: std::collections::HashMap::new(),
            session_duration_secs: 3600, // 1 hour default
        }
    }

    /// Create a new session via X25519 ECDH key exchange
    pub fn create_session(
        &mut self,
        peer_id: &str,
        local_secret: &[u8; 32],
        remote_public: &[u8; 32],
    ) -> Result<SessionInfo, EdgeClawError> {
        // Perform X25519 ECDH
        let secret = StaticSecret::from(*local_secret);
        let remote_pk = PublicKey::from(*remote_public);
        let shared_secret = secret.diffie_hellman(&remote_pk);

        // Derive session key via HKDF-SHA256
        let hk = Hkdf::<Sha256>::new(None, shared_secret.as_bytes());
        let mut session_key = [0u8; 32];
        hk.expand(b"edgeclaw-session-v1", &mut session_key)
            .map_err(|_| EdgeClawError::CryptoError)?;

        let now = chrono::Utc::now();
        let session_id = uuid::Uuid::new_v4().to_string();

        let session = Session {
            session_id: session_id.clone(),
            peer_id: peer_id.to_string(),
            state: SessionState::Established,
            session_key,
            nonce_counter: 0,
            created_at: now,
            expires_at: now + chrono::Duration::seconds(self.session_duration_secs),
            messages_sent: 0,
            messages_received: 0,
        };

        let info = session.to_info();
        self.sessions.insert(session_id, session);

        tracing::info!(peer_id = %peer_id, "Session established");
        Ok(info)
    }

    /// Encrypt data using session's AES-256-GCM key
    pub fn encrypt(
        &mut self,
        session_id: &str,
        plaintext: &[u8],
    ) -> Result<Vec<u8>, EdgeClawError> {
        let session = self
            .sessions
            .get_mut(session_id)
            .ok_or(EdgeClawError::InvalidParameter)?;

        if session.is_expired() {
            session.state = SessionState::Expired;
            return Err(EdgeClawError::SessionExpired);
        }

        let cipher = Aes256Gcm::new_from_slice(&session.session_key)
            .map_err(|_| EdgeClawError::CryptoError)?;

        // Build nonce from counter (12 bytes)
        let mut nonce_bytes = [0u8; 12];
        nonce_bytes[4..12].copy_from_slice(&session.nonce_counter.to_be_bytes());
        session.nonce_counter += 1;

        let nonce = Nonce::from_slice(&nonce_bytes);
        let ciphertext = cipher
            .encrypt(nonce, plaintext)
            .map_err(|_| EdgeClawError::CryptoError)?;

        // Prepend nonce to ciphertext
        let mut result = Vec::with_capacity(12 + ciphertext.len());
        result.extend_from_slice(&nonce_bytes);
        result.extend_from_slice(&ciphertext);

        session.messages_sent += 1;
        Ok(result)
    }

    /// Decrypt data using session's AES-256-GCM key
    pub fn decrypt(
        &mut self,
        session_id: &str,
        ciphertext: &[u8],
    ) -> Result<Vec<u8>, EdgeClawError> {
        if ciphertext.len() < 12 {
            return Err(EdgeClawError::InvalidParameter);
        }

        let session = self
            .sessions
            .get_mut(session_id)
            .ok_or(EdgeClawError::InvalidParameter)?;

        if session.is_expired() {
            session.state = SessionState::Expired;
            return Err(EdgeClawError::SessionExpired);
        }

        let cipher = Aes256Gcm::new_from_slice(&session.session_key)
            .map_err(|_| EdgeClawError::CryptoError)?;

        let nonce = Nonce::from_slice(&ciphertext[..12]);
        let plaintext = cipher
            .decrypt(nonce, &ciphertext[12..])
            .map_err(|_| EdgeClawError::CryptoError)?;

        session.messages_received += 1;
        Ok(plaintext)
    }

    /// Get session info
    pub fn get_session(&self, session_id: &str) -> Result<SessionInfo, EdgeClawError> {
        self.sessions
            .get(session_id)
            .map(|s| s.to_info())
            .ok_or(EdgeClawError::InvalidParameter)
    }

    /// Get all active sessions
    pub fn active_sessions(&self) -> Vec<SessionInfo> {
        self.sessions
            .values()
            .filter(|s| s.state == SessionState::Established && !s.is_expired())
            .map(|s| s.to_info())
            .collect()
    }

    /// Close a session
    pub fn close_session(&mut self, session_id: &str) -> Result<(), EdgeClawError> {
        self.sessions
            .remove(session_id)
            .map(|_| ())
            .ok_or(EdgeClawError::InvalidParameter)
    }

    /// Clean up expired sessions
    pub fn cleanup_expired(&mut self) -> u32 {
        let initial = self.sessions.len();
        self.sessions.retain(|_, s| !s.is_expired());
        (initial - self.sessions.len()) as u32
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rand::rngs::OsRng;
    use x25519_dalek::StaticSecret;

    fn create_keypair() -> ([u8; 32], [u8; 32]) {
        let secret = StaticSecret::random_from_rng(OsRng);
        let public = PublicKey::from(&secret);
        (secret.to_bytes(), public.to_bytes())
    }

    #[test]
    fn test_session_creation() {
        let mut mgr = SessionManager::new();
        let (secret_a, _pub_a) = create_keypair();
        let (_secret_b, pub_b) = create_keypair();

        let info = mgr.create_session("peer-1", &secret_a, &pub_b).unwrap();
        assert_eq!(info.peer_id, "peer-1");
        assert_eq!(info.state, "established");
    }

    #[test]
    fn test_encrypt_decrypt_roundtrip() {
        let mut mgr = SessionManager::new();
        let (secret_a, pub_a) = create_keypair();
        let (secret_b, pub_b) = create_keypair();

        // Both sides derive the same shared secret
        let info_a = mgr.create_session("peer-b", &secret_a, &pub_b).unwrap();

        let mut mgr_b = SessionManager::new();
        let _info_b = mgr_b.create_session("peer-a", &secret_b, &pub_a).unwrap();

        // Encrypt on side A
        let plaintext = b"Hello EdgeClaw!";
        let encrypted = mgr.encrypt(&info_a.session_id, plaintext).unwrap();

        // Decrypt on side A (same key)
        let decrypted = mgr.decrypt(&info_a.session_id, &encrypted).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_session_message_counters() {
        let mut mgr = SessionManager::new();
        let (secret_a, _) = create_keypair();
        let (_, pub_b) = create_keypair();

        let info = mgr.create_session("peer-1", &secret_a, &pub_b).unwrap();

        mgr.encrypt(&info.session_id, b"msg1").unwrap();
        mgr.encrypt(&info.session_id, b"msg2").unwrap();

        let updated = mgr.get_session(&info.session_id).unwrap();
        assert_eq!(updated.messages_sent, 2);
    }

    #[test]
    fn test_close_session() {
        let mut mgr = SessionManager::new();
        let (secret_a, _) = create_keypair();
        let (_, pub_b) = create_keypair();

        let info = mgr.create_session("peer-1", &secret_a, &pub_b).unwrap();
        assert!(mgr.close_session(&info.session_id).is_ok());
        assert!(mgr.get_session(&info.session_id).is_err());
    }

    #[test]
    fn test_decrypt_invalid_data() {
        let mut mgr = SessionManager::new();
        let (secret_a, _) = create_keypair();
        let (_, pub_b) = create_keypair();

        let info = mgr.create_session("peer-1", &secret_a, &pub_b).unwrap();

        // Too short — no nonce
        assert!(mgr.decrypt(&info.session_id, &[0u8; 5]).is_err());
    }
}
