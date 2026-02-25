package com.edgeclaw.mobile.core.crypto

import com.edgeclaw.mobile.core.model.DeviceIdentity
import com.edgeclaw.mobile.core.model.SessionInfo
import com.edgeclaw.mobile.core.model.SessionState
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto Engine — provides identity, key exchange, and AES-256-GCM encryption.
 *
 * Uses Android JCA for crypto operations (future: delegate to Rust core via JNI).
 */
class CryptoEngine {

    private var identity: DeviceIdentity? = null

    // Session keys: sessionId -> 32-byte AES key
    private val sessionKeys = ConcurrentHashMap<String, ByteArray>()
    private val nonceCounters = ConcurrentHashMap<String, Long>()

    companion object {
        private const val AES_KEY_SIZE = 256
        private const val GCM_NONCE_SIZE = 12
        private const val GCM_TAG_SIZE = 128
        private const val SESSION_DURATION_HOURS = 1L
    }

    /**
     * Generate a new device identity (Ed25519-like, using EC on JCA)
     */
    fun generateIdentity(): DeviceIdentity {
        val deviceId = UUID.randomUUID().toString()

        // Generate a keypair (using EC P-256 as JCA proxy for Ed25519)
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256, SecureRandom())
        val keyPair = keyGen.generateKeyPair()

        val publicKeyBytes = keyPair.public.encoded
        val publicKeyHex = publicKeyBytes.toHexString()

        // Fingerprint = first 16 hex chars of SHA-256(publicKey)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyBytes)
        val fingerprint = hash.take(8).toByteArray().toHexString()

        val id = DeviceIdentity(
            deviceId = deviceId,
            publicKeyHex = publicKeyHex,
            fingerprint = fingerprint,
            createdAt = Instant.now().toString()
        )
        identity = id
        return id
    }

    /**
     * Create an encrypted session with a peer.
     * For v1.0, we use a shared random key (full ECDH will use Rust core).
     */
    fun createSession(peerId: String, peerPublicKeyHex: String): SessionInfo {
        val sessionId = UUID.randomUUID().toString()
        val now = Instant.now()

        // Generate session key (256-bit random for now)
        val sessionKey = ByteArray(32)
        SecureRandom().nextBytes(sessionKey)
        sessionKeys[sessionId] = sessionKey
        nonceCounters[sessionId] = 0L

        return SessionInfo(
            sessionId = sessionId,
            peerId = peerId,
            state = SessionState.ESTABLISHED,
            createdAt = now.toString(),
            expiresAt = now.plus(SESSION_DURATION_HOURS, ChronoUnit.HOURS).toString()
        )
    }

    /**
     * Encrypt data using AES-256-GCM
     */
    fun encrypt(sessionId: String, plaintext: ByteArray): ByteArray {
        val key = sessionKeys[sessionId]
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        val counter = nonceCounters.compute(sessionId) { _, v -> (v ?: 0) + 1 }!!

        // Build nonce from counter
        val nonce = ByteArray(GCM_NONCE_SIZE)
        val counterBytes = counter.toBigEndianBytes()
        System.arraycopy(counterBytes, 0, nonce, GCM_NONCE_SIZE - 8, 8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)

        val ciphertext = cipher.doFinal(plaintext)

        // Prepend nonce to ciphertext
        return nonce + ciphertext
    }

    /**
     * Decrypt data using AES-256-GCM
     */
    fun decrypt(sessionId: String, data: ByteArray): ByteArray {
        val key = sessionKeys[sessionId]
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        require(data.size > GCM_NONCE_SIZE) { "Data too short" }

        val nonce = data.copyOfRange(0, GCM_NONCE_SIZE)
        val ciphertext = data.copyOfRange(GCM_NONCE_SIZE, data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)

        return cipher.doFinal(ciphertext)
    }
}

// Extension functions
private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun Long.toBigEndianBytes(): ByteArray {
    val bytes = ByteArray(8)
    for (i in 7 downTo 0) {
        bytes[7 - i] = ((this shr (i * 8)) and 0xFF).toByte()
    }
    return bytes
}
