use ed25519_dalek::{SigningKey, VerifyingKey};
use rand::rngs::OsRng;
use sha2::{Digest, Sha256};
use x25519_dalek::{PublicKey, StaticSecret};

use crate::error::EdgeClawError;

/// Device identity information exposed via UniFFI
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct DeviceIdentity {
    pub device_id: String,
    pub public_key_hex: String,
    pub fingerprint: String,
    pub created_at: String,
}

/// Manages device identity (Ed25519 signing + X25519 key exchange)
pub struct IdentityManager {
    signing_key: Option<SigningKey>,
    x25519_secret: Option<StaticSecret>,
    identity: Option<DeviceIdentity>,
}

impl Default for IdentityManager {
    fn default() -> Self {
        Self::new()
    }
}

impl IdentityManager {
    pub fn new() -> Self {
        Self {
            signing_key: None,
            x25519_secret: None,
            identity: None,
        }
    }

    /// Generate a new device identity
    pub fn generate_identity(&mut self) -> Result<DeviceIdentity, EdgeClawError> {
        // Generate Ed25519 signing key
        let signing_key = SigningKey::generate(&mut OsRng);
        let verifying_key: VerifyingKey = signing_key.verifying_key();
        let public_key_bytes = verifying_key.to_bytes();
        let public_key_hex = hex::encode(public_key_bytes);

        // Generate X25519 key for key exchange
        let x25519_secret = StaticSecret::random_from_rng(OsRng);

        // Device ID = UUID v4
        let device_id = uuid::Uuid::new_v4().to_string();

        // Fingerprint = first 16 chars of SHA256(public_key)
        let mut hasher = Sha256::new();
        hasher.update(public_key_bytes);
        let hash = hasher.finalize();
        let fingerprint = hex::encode(&hash[..8]);

        let identity = DeviceIdentity {
            device_id,
            public_key_hex,
            fingerprint,
            created_at: chrono::Utc::now().to_rfc3339(),
        };

        self.signing_key = Some(signing_key);
        self.x25519_secret = Some(x25519_secret);
        self.identity = Some(identity.clone());

        tracing::info!(
            device_id = %identity.device_id,
            fingerprint = %identity.fingerprint,
            "Device identity generated"
        );

        Ok(identity)
    }

    /// Get current device identity
    pub fn get_identity(&self) -> Result<DeviceIdentity, EdgeClawError> {
        self.identity.clone().ok_or(EdgeClawError::InternalError)
    }

    /// Get the X25519 secret key bytes for session creation
    pub fn get_secret_key(&self) -> Result<[u8; 32], EdgeClawError> {
        let secret = self
            .x25519_secret
            .as_ref()
            .ok_or(EdgeClawError::InternalError)?;
        Ok(secret.to_bytes())
    }

    /// Get the X25519 public key bytes
    pub fn get_public_key(&self) -> Result<[u8; 32], EdgeClawError> {
        let secret = self
            .x25519_secret
            .as_ref()
            .ok_or(EdgeClawError::InternalError)?;
        let public = PublicKey::from(secret);
        Ok(public.to_bytes())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_generate_identity() {
        let mut mgr = IdentityManager::new();
        let id = mgr.generate_identity().unwrap();

        assert!(!id.device_id.is_empty());
        assert_eq!(id.public_key_hex.len(), 64); // 32 bytes hex
        assert_eq!(id.fingerprint.len(), 16); // 8 bytes hex
        assert!(!id.created_at.is_empty());
    }

    #[test]
    fn test_get_identity_before_generate() {
        let mgr = IdentityManager::new();
        assert!(mgr.get_identity().is_err());
    }

    #[test]
    fn test_get_keys() {
        let mut mgr = IdentityManager::new();
        mgr.generate_identity().unwrap();

        let secret = mgr.get_secret_key().unwrap();
        assert_eq!(secret.len(), 32);

        let public = mgr.get_public_key().unwrap();
        assert_eq!(public.len(), 32);
    }

    #[test]
    fn test_identity_uniqueness() {
        let mut mgr1 = IdentityManager::new();
        let id1 = mgr1.generate_identity().unwrap();

        let mut mgr2 = IdentityManager::new();
        let id2 = mgr2.generate_identity().unwrap();

        assert_ne!(id1.device_id, id2.device_id);
        assert_ne!(id1.public_key_hex, id2.public_key_hex);
    }
}
