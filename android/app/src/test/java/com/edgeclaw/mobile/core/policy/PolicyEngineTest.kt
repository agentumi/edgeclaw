package com.edgeclaw.mobile.core.policy

import com.edgeclaw.mobile.core.model.Role
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PolicyEngine
 */
class PolicyEngineTest {

    private lateinit var engine: PolicyEngine

    @Before
    fun setup() {
        engine = PolicyEngine()
    }

    @Test
    fun `viewer can query status`() {
        val decision = engine.evaluate("status_query", Role.VIEWER)
        assertTrue(decision.allowed)
        assertEquals(0, decision.riskLevel)
    }

    @Test
    fun `viewer cannot execute shell`() {
        val decision = engine.evaluate("shell_exec", Role.VIEWER)
        assertFalse(decision.allowed)
        assertEquals(3, decision.riskLevel)
    }

    @Test
    fun `operator can read files`() {
        val decision = engine.evaluate("file_read", Role.OPERATOR)
        assertTrue(decision.allowed)
        assertEquals(1, decision.riskLevel)
    }

    @Test
    fun `operator cannot write files`() {
        val decision = engine.evaluate("file_write", Role.OPERATOR)
        assertFalse(decision.allowed)
        assertEquals(2, decision.riskLevel)
    }

    @Test
    fun `admin can write files`() {
        val decision = engine.evaluate("file_write", Role.ADMIN)
        assertTrue(decision.allowed)
    }

    @Test
    fun `admin cannot execute shell`() {
        val decision = engine.evaluate("shell_exec", Role.ADMIN)
        assertFalse(decision.allowed)
    }

    @Test
    fun `owner can do everything`() {
        val capabilities = listOf(
            "status_query", "file_read", "file_write",
            "shell_exec", "firmware_update", "system_reboot"
        )
        capabilities.forEach { cap ->
            val decision = engine.evaluate(cap, Role.OWNER)
            assertTrue("Owner should be allowed for $cap", decision.allowed)
        }
    }

    @Test
    fun `unknown capability is denied`() {
        val decision = engine.evaluate("launch_missiles", Role.OWNER)
        assertFalse(decision.allowed)
    }

    @Test
    fun `list capabilities returns defaults`() {
        val caps = engine.listCapabilities()
        assertTrue(caps.size >= 11)
    }

    @Test
    fun `heartbeat is safe for all roles`() {
        Role.entries.forEach { role ->
            val decision = engine.evaluate("heartbeat", role)
            assertTrue("Heartbeat should be allowed for ${role.name}", decision.allowed)
        }
    }
}
