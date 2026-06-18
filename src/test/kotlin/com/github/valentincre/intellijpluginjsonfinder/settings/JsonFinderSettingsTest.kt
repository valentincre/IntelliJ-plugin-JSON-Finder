package com.github.valentincre.intellijpluginjsonfinder.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

// Unit tests for JsonFinderSettings (Story 7.1 — AC1).
class JsonFinderSettingsTest : BasePlatformTestCase() {

    // AC1: isEnabled defaults to true for a new project.
    fun testIsEnabledDefaultsToTrue() {
        val settings = project.service<JsonFinderSettings>()
        assertTrue("isEnabled must default to true", settings.state.isEnabled)
    }

    fun testIsEnabledCanBeSetToFalse() {
        val settings = project.service<JsonFinderSettings>()
        settings.loadState(settings.state.copy(isEnabled = false))
        assertFalse("isEnabled must be false after setting it", settings.state.isEnabled)
    }

    fun testIsEnabledCanBeResetToTrue() {
        val settings = project.service<JsonFinderSettings>()
        settings.loadState(settings.state.copy(isEnabled = false))
        settings.loadState(settings.state.copy(isEnabled = true))
        assertTrue("isEnabled must be true after reset", settings.state.isEnabled)
    }

    fun testOtherSettingsPreservedWhenTogglingEnabled() {
        val settings = project.service<JsonFinderSettings>()
        val customPatterns = listOf("**/*.json")
        settings.loadState(settings.state.copy(includePatterns = customPatterns))
        settings.loadState(settings.state.copy(isEnabled = false))
        assertEquals("includePatterns must be preserved when toggling isEnabled", customPatterns, settings.state.includePatterns)
    }
}
