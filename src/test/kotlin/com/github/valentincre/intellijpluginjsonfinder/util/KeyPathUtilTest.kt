package com.github.valentincre.intellijpluginjsonfinder.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Plain JUnit unit tests for KeyPathUtil — no IntelliJ platform dependencies.
class KeyPathUtilTest {

    // ─── isKeyPathCandidate — true cases ─────────────────────────────────────

    @Test
    fun `isKeyPathCandidate returns true for two-segment path`() {
        assertTrue(KeyPathUtil.isKeyPathCandidate("a.b"))
    }

    @Test
    fun `isKeyPathCandidate returns true for deep path`() {
        assertTrue(KeyPathUtil.isKeyPathCandidate("auth.login.button"))
    }

    @Test
    fun `isKeyPathCandidate returns true for uppercase segments`() {
        assertTrue(KeyPathUtil.isKeyPathCandidate("UPPER.CASE"))
    }

    @Test
    fun `isKeyPathCandidate returns true for path with surrounding whitespace`() {
        assertTrue(KeyPathUtil.isKeyPathCandidate("  auth.login.button  "))
    }

    // ─── isKeyPathCandidate — false cases ────────────────────────────────────

    @Test
    fun `isKeyPathCandidate returns false for plain string`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate("plain string"))
    }

    @Test
    fun `isKeyPathCandidate returns false for single segment`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate("single"))
    }

    @Test
    fun `isKeyPathCandidate returns false for empty string`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate(""))
    }

    @Test
    fun `isKeyPathCandidate returns false for string with spaces`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate("has space"))
    }

    @Test
    fun `isKeyPathCandidate returns false for numeric string`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate("123"))
    }

    @Test
    fun `isKeyPathCandidate returns false for leading dot`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate(".leading.dot"))
    }

    @Test
    fun `isKeyPathCandidate returns false for trailing dot`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate("trailing.dot."))
    }

    @Test
    fun `isKeyPathCandidate returns false for hyphenated path`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate("not-a.path"))
    }

    @Test
    fun `isKeyPathCandidate returns false for numeric multi-segment path`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate("1.2"))
    }

    @Test
    fun `isKeyPathCandidate returns false for numeric-leading segment`() {
        assertFalse(KeyPathUtil.isKeyPathCandidate("1auth.login"))
    }

    // ─── normalizeKeyPath ────────────────────────────────────────────────────

    @Test
    fun `normalizeKeyPath trims and lowercases`() {
        assertEquals("auth.login.button", KeyPathUtil.normalizeKeyPath("  AUTH.LOGIN.BUTTON  "))
    }

    @Test
    fun `normalizeKeyPath is idempotent on already-normalized path`() {
        assertEquals("auth.login.button", KeyPathUtil.normalizeKeyPath("auth.login.button"))
    }
}
