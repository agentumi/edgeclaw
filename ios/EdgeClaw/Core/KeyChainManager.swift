//
//  KeyChainManager.swift
//  EdgeClaw
//
//  Keychain wrapper for securely storing Ed25519/X25519 key material.
//  Uses Apple Security framework for hardware-backed key storage.
//

import Foundation
import Security

/// Errors from KeyChain operations
enum KeyChainError: Error, LocalizedError {
    case duplicateItem
    case itemNotFound
    case unexpectedStatus(OSStatus)
    case encodingError
    case decodingError
    case invalidData

    var errorDescription: String? {
        switch self {
        case .duplicateItem:              return "Item already exists in Keychain"
        case .itemNotFound:               return "Item not found in Keychain"
        case .unexpectedStatus(let s):    return "Keychain error: \(s)"
        case .encodingError:              return "Failed to encode data for Keychain"
        case .decodingError:              return "Failed to decode data from Keychain"
        case .invalidData:                return "Invalid data retrieved from Keychain"
        }
    }
}

/// Keychain service identifiers
private enum KeyChainService {
    static let ed25519PrivateKey = "com.edgeclaw.ed25519.private"
    static let ed25519PublicKey  = "com.edgeclaw.ed25519.public"
    static let x25519PrivateKey  = "com.edgeclaw.x25519.private"
    static let x25519PublicKey   = "com.edgeclaw.x25519.public"
    static let deviceId          = "com.edgeclaw.device.id"
    static let fingerprint       = "com.edgeclaw.device.fingerprint"
    static let sessionKeys       = "com.edgeclaw.session.keys"
}

/// Secure Keychain wrapper for EdgeClaw cryptographic key material.
///
/// Stores and retrieves Ed25519/X25519 keys using the iOS Keychain.
/// Keys are stored with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`
/// to prevent backup extraction.
///
/// Usage:
/// ```swift
/// let keychain = KeyChainManager()
/// try keychain.storeEd25519PrivateKey(privateKeyBytes)
/// let key = try keychain.loadEd25519PrivateKey()
/// ```
class KeyChainManager {

    // MARK: - Generic Keychain Operations

    /// Store data in the Keychain
    func store(data: Data, service: String, account: String) throws {
        let query: [String: Any] = [
            kSecClass as String:            kSecClassGenericPassword,
            kSecAttrService as String:      service,
            kSecAttrAccount as String:      account,
            kSecValueData as String:        data,
            kSecAttrAccessible as String:   kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]

        // Delete existing item if present
        SecItemDelete(query as CFDictionary)

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            if status == errSecDuplicateItem {
                throw KeyChainError.duplicateItem
            }
            throw KeyChainError.unexpectedStatus(status)
        }
    }

    /// Load data from the Keychain
    func load(service: String, account: String) throws -> Data {
        let query: [String: Any] = [
            kSecClass as String:            kSecClassGenericPassword,
            kSecAttrService as String:      service,
            kSecAttrAccount as String:      account,
            kSecReturnData as String:       true,
            kSecMatchLimit as String:       kSecMatchLimitOne,
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess else {
            if status == errSecItemNotFound {
                throw KeyChainError.itemNotFound
            }
            throw KeyChainError.unexpectedStatus(status)
        }

        guard let data = result as? Data else {
            throw KeyChainError.invalidData
        }

        return data
    }

    /// Delete data from the Keychain
    func delete(service: String, account: String) throws {
        let query: [String: Any] = [
            kSecClass as String:        kSecClassGenericPassword,
            kSecAttrService as String:  service,
            kSecAttrAccount as String:  account,
        ]

        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeyChainError.unexpectedStatus(status)
        }
    }

    /// Check if an item exists in the Keychain
    func exists(service: String, account: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String:        kSecClassGenericPassword,
            kSecAttrService as String:  service,
            kSecAttrAccount as String:  account,
            kSecMatchLimit as String:   kSecMatchLimitOne,
        ]

        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    // MARK: - Ed25519 Key Operations

    /// Store Ed25519 private key bytes
    func storeEd25519PrivateKey(_ key: Data) throws {
        try store(data: key, service: KeyChainService.ed25519PrivateKey, account: "default")
    }

    /// Load Ed25519 private key bytes
    func loadEd25519PrivateKey() throws -> Data {
        try load(service: KeyChainService.ed25519PrivateKey, account: "default")
    }

    /// Store Ed25519 public key bytes
    func storeEd25519PublicKey(_ key: Data) throws {
        try store(data: key, service: KeyChainService.ed25519PublicKey, account: "default")
    }

    /// Load Ed25519 public key bytes
    func loadEd25519PublicKey() throws -> Data {
        try load(service: KeyChainService.ed25519PublicKey, account: "default")
    }

    /// Delete Ed25519 keys
    func deleteEd25519Keys() throws {
        try delete(service: KeyChainService.ed25519PrivateKey, account: "default")
        try delete(service: KeyChainService.ed25519PublicKey, account: "default")
    }

    // MARK: - X25519 Key Operations

    /// Store X25519 private key bytes
    func storeX25519PrivateKey(_ key: Data) throws {
        try store(data: key, service: KeyChainService.x25519PrivateKey, account: "default")
    }

    /// Load X25519 private key bytes
    func loadX25519PrivateKey() throws -> Data {
        try load(service: KeyChainService.x25519PrivateKey, account: "default")
    }

    /// Store X25519 public key bytes
    func storeX25519PublicKey(_ key: Data) throws {
        try store(data: key, service: KeyChainService.x25519PublicKey, account: "default")
    }

    /// Load X25519 public key bytes
    func loadX25519PublicKey() throws -> Data {
        try load(service: KeyChainService.x25519PublicKey, account: "default")
    }

    /// Delete X25519 keys
    func deleteX25519Keys() throws {
        try delete(service: KeyChainService.x25519PrivateKey, account: "default")
        try delete(service: KeyChainService.x25519PublicKey, account: "default")
    }

    // MARK: - Device Identity

    /// Store device ID
    func storeDeviceId(_ deviceId: String) throws {
        guard let data = deviceId.data(using: .utf8) else {
            throw KeyChainError.encodingError
        }
        try store(data: data, service: KeyChainService.deviceId, account: "default")
    }

    /// Load device ID
    func loadDeviceId() throws -> String {
        let data = try load(service: KeyChainService.deviceId, account: "default")
        guard let id = String(data: data, encoding: .utf8) else {
            throw KeyChainError.decodingError
        }
        return id
    }

    /// Store fingerprint
    func storeFingerprint(_ fingerprint: String) throws {
        guard let data = fingerprint.data(using: .utf8) else {
            throw KeyChainError.encodingError
        }
        try store(data: data, service: KeyChainService.fingerprint, account: "default")
    }

    /// Load fingerprint
    func loadFingerprint() throws -> String {
        let data = try load(service: KeyChainService.fingerprint, account: "default")
        guard let fp = String(data: data, encoding: .utf8) else {
            throw KeyChainError.decodingError
        }
        return fp
    }

    // MARK: - Session Keys

    /// Store session key for a peer
    func storeSessionKey(_ key: Data, forPeer peerId: String) throws {
        try store(data: key, service: KeyChainService.sessionKeys, account: peerId)
    }

    /// Load session key for a peer
    func loadSessionKey(forPeer peerId: String) throws -> Data {
        try load(service: KeyChainService.sessionKeys, account: peerId)
    }

    /// Delete session key for a peer
    func deleteSessionKey(forPeer peerId: String) throws {
        try delete(service: KeyChainService.sessionKeys, account: peerId)
    }

    // MARK: - Utility

    /// Check if Ed25519 identity exists
    var hasIdentity: Bool {
        exists(service: KeyChainService.ed25519PrivateKey, account: "default")
    }

    /// Delete all EdgeClaw keychain items
    func deleteAll() throws {
        try deleteEd25519Keys()
        try deleteX25519Keys()
        try delete(service: KeyChainService.deviceId, account: "default")
        try delete(service: KeyChainService.fingerprint, account: "default")
    }

    /// Store full identity (convenience)
    func storeIdentity(
        deviceId: String,
        fingerprint: String,
        ed25519Private: Data,
        ed25519Public: Data,
        x25519Private: Data,
        x25519Public: Data
    ) throws {
        try storeDeviceId(deviceId)
        try storeFingerprint(fingerprint)
        try storeEd25519PrivateKey(ed25519Private)
        try storeEd25519PublicKey(ed25519Public)
        try storeX25519PrivateKey(x25519Private)
        try storeX25519PublicKey(x25519Public)
    }
}
