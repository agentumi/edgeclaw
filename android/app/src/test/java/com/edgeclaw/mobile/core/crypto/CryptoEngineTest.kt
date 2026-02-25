package com.edgeclaw.mobile.core.crypto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CryptoEngine
 */
class CryptoEngineTest {

    private lateinit var crypto: CryptoEngine

    @Before
    fun setup() {
        crypto = CryptoEngine()
    }

    @Test
    fun `generate identity produces valid identity`() {
        val identity = crypto.generateIdentity()

        assertNotNull(identity)
        assertTrue(identity.deviceId.isNotEmpty())
        assertTrue(identity.publicKeyHex.isNotEmpty())
        assertEquals(16, identity.fingerprint.length)
        assertTrue(identity.createdAt.isNotEmpty())
    }

    @Test
    fun `generate identity produces unique identities`() {
        val id1 = crypto.generateIdentity()

        val crypto2 = CryptoEngine()
        val id2 = crypto2.generateIdentity()

        assertNotEquals(id1.deviceId, id2.deviceId)
        assertNotEquals(id1.publicKeyHex, id2.publicKeyHex)
    }

    @Test
    fun `create session returns valid session`() {
        val session = crypto.createSession("peer-1", "0".repeat(64))

        assertNotNull(session)
        assertEquals("peer-1", session.peerId)
        assertTrue(session.sessionId.isNotEmpty())
    }

    @Test
    fun `encrypt decrypt roundtrip succeeds`() {
        val session = crypto.createSession("peer-1", "0".repeat(64))
        val plaintext = "Hello EdgeClaw!".toByteArray()

        val ciphertext = crypto.encrypt(session.sessionId, plaintext)
        assertFalse(ciphertext.contentEquals(plaintext))

        val decrypted = crypto.decrypt(session.sessionId, ciphertext)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `multiple encrypt produce different ciphertexts`() {
        val session = crypto.createSession("peer-1", "0".repeat(64))
        val plaintext = "test".toByteArray()

        val ct1 = crypto.encrypt(session.sessionId, plaintext)
        val ct2 = crypto.encrypt(session.sessionId, plaintext)

        // Different nonces produce different ciphertexts
        assertFalse(ct1.contentEquals(ct2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encrypt with invalid session throws`() {
        crypto.encrypt("nonexistent", "test".toByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decrypt with invalid session throws`() {
        crypto.decrypt("nonexistent", ByteArray(20))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decrypt too short data throws`() {
        val session = crypto.createSession("peer-1", "0".repeat(64))
        crypto.decrypt(session.sessionId, ByteArray(5))
    }
}
