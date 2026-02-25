use crate::error::EdgeClawError;

/// Peer information exposed via UniFFI
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PeerInfo {
    pub peer_id: String,
    pub device_name: String,
    pub device_type: String,
    pub address: String,
    pub capabilities: Vec<String>,
    pub last_seen: String,
    pub is_connected: bool,
}

/// Internal peer entry
struct PeerEntry {
    info: PeerInfo,
    discovered_at: chrono::DateTime<chrono::Utc>,
}

/// Manages discovered and connected peers
pub struct PeerManager {
    peers: std::collections::HashMap<String, PeerEntry>,
}

impl Default for PeerManager {
    fn default() -> Self {
        Self::new()
    }
}

impl PeerManager {
    pub fn new() -> Self {
        Self {
            peers: std::collections::HashMap::new(),
        }
    }

    /// Add or update a discovered peer
    pub fn add_peer(
        &mut self,
        peer_id: &str,
        device_name: &str,
        device_type: &str,
        address: &str,
        capabilities: Vec<String>,
    ) -> PeerInfo {
        let now = chrono::Utc::now();
        let info = PeerInfo {
            peer_id: peer_id.to_string(),
            device_name: device_name.to_string(),
            device_type: device_type.to_string(),
            address: address.to_string(),
            capabilities,
            last_seen: now.to_rfc3339(),
            is_connected: false,
        };

        self.peers.insert(
            peer_id.to_string(),
            PeerEntry {
                info: info.clone(),
                discovered_at: now,
            },
        );

        tracing::debug!(peer_id = %peer_id, "Peer added/updated");
        info
    }

    /// Mark a peer as connected
    pub fn set_connected(&mut self, peer_id: &str, connected: bool) -> Result<(), EdgeClawError> {
        let entry = self
            .peers
            .get_mut(peer_id)
            .ok_or(EdgeClawError::InvalidParameter)?;
        entry.info.is_connected = connected;
        entry.info.last_seen = chrono::Utc::now().to_rfc3339();
        Ok(())
    }

    /// Remove a peer
    pub fn remove_peer(&mut self, peer_id: &str) -> Result<(), EdgeClawError> {
        self.peers
            .remove(peer_id)
            .map(|_| ())
            .ok_or(EdgeClawError::InvalidParameter)
    }

    /// Get a specific peer
    pub fn get_peer(&self, peer_id: &str) -> Result<PeerInfo, EdgeClawError> {
        self.peers
            .get(peer_id)
            .map(|e| e.info.clone())
            .ok_or(EdgeClawError::InvalidParameter)
    }

    /// List all known peers
    pub fn list_peers(&self) -> Vec<PeerInfo> {
        self.peers.values().map(|e| e.info.clone()).collect()
    }

    /// List only connected peers
    pub fn connected_peers(&self) -> Vec<PeerInfo> {
        self.peers
            .values()
            .filter(|e| e.info.is_connected)
            .map(|e| e.info.clone())
            .collect()
    }

    /// Remove peers not seen within the given timeout (seconds)
    pub fn cleanup_stale(&mut self, timeout_secs: i64) -> u32 {
        let cutoff = chrono::Utc::now() - chrono::Duration::seconds(timeout_secs);
        let initial = self.peers.len();
        self.peers.retain(|_, e| e.discovered_at >= cutoff);
        (initial - self.peers.len()) as u32
    }

    /// Total peer count
    pub fn count(&self) -> usize {
        self.peers.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_add_and_get_peer() {
        let mut mgr = PeerManager::new();
        let info = mgr.add_peer(
            "peer-1",
            "TestDevice",
            "smartphone",
            "192.168.1.10",
            vec!["camera".into()],
        );
        assert_eq!(info.peer_id, "peer-1");
        assert!(!info.is_connected);

        let fetched = mgr.get_peer("peer-1").unwrap();
        assert_eq!(fetched.device_name, "TestDevice");
    }

    #[test]
    fn test_set_connected() {
        let mut mgr = PeerManager::new();
        mgr.add_peer("peer-1", "Dev", "pc", "10.0.0.1", vec![]);

        mgr.set_connected("peer-1", true).unwrap();
        let p = mgr.get_peer("peer-1").unwrap();
        assert!(p.is_connected);

        assert_eq!(mgr.connected_peers().len(), 1);
    }

    #[test]
    fn test_remove_peer() {
        let mut mgr = PeerManager::new();
        mgr.add_peer("peer-1", "Dev", "pc", "10.0.0.1", vec![]);
        assert_eq!(mgr.count(), 1);

        mgr.remove_peer("peer-1").unwrap();
        assert_eq!(mgr.count(), 0);
        assert!(mgr.get_peer("peer-1").is_err());
    }

    #[test]
    fn test_list_peers() {
        let mut mgr = PeerManager::new();
        mgr.add_peer("p1", "D1", "phone", "1.1.1.1", vec![]);
        mgr.add_peer("p2", "D2", "tablet", "2.2.2.2", vec![]);
        mgr.add_peer("p3", "D3", "pc", "3.3.3.3", vec![]);

        assert_eq!(mgr.list_peers().len(), 3);
    }

    #[test]
    fn test_remove_nonexistent_peer() {
        let mut mgr = PeerManager::new();
        assert!(mgr.remove_peer("nobody").is_err());
    }

    #[test]
    fn test_update_existing_peer() {
        let mut mgr = PeerManager::new();
        mgr.add_peer("peer-1", "OldName", "pc", "1.1.1.1", vec![]);
        mgr.add_peer("peer-1", "NewName", "pc", "2.2.2.2", vec!["gpu".into()]);

        assert_eq!(mgr.count(), 1);
        let p = mgr.get_peer("peer-1").unwrap();
        assert_eq!(p.device_name, "NewName");
        assert_eq!(p.address, "2.2.2.2");
    }
}
