package com.github.valentincre.intellijpluginjsonfinder.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsonFinderProjectServiceTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/service"

    fun testFindDefinitionsReturnsResultForIndexedKey() {
        myFixture.configureByFile("keys.json")
        val service = project.service<JsonFinderProjectService>()
        val results = service.findDefinitions("auth.login.button")
        assertNotEmpty(results)
        val first = results.first()
        assertEquals("Sign in", first.resolvedValue)
        assertTrue("offset must be non-negative", first.offset >= 0)
        assertTrue("virtualFile must be valid", first.virtualFile.isValid)
    }

    fun testFindDefinitionsReturnsEmptyForUnknownKey() {
        myFixture.configureByFile("keys.json")
        val service = project.service<JsonFinderProjectService>()
        assertTrue(service.findDefinitions("nonexistent.key").isEmpty())
    }

    fun testIsValidKeyPathReturnsTrueForDottedPath() {
        val service = project.service<JsonFinderProjectService>()
        assertTrue(service.isValidKeyPath("auth.login.button"))
    }

    fun testIsValidKeyPathReturnsFalseForPlainString() {
        val service = project.service<JsonFinderProjectService>()
        assertFalse(service.isValidKeyPath("plain string"))
    }

    fun testIsValidKeyPathReturnsFalseForSingleSegment() {
        val service = project.service<JsonFinderProjectService>()
        assertFalse(service.isValidKeyPath("notapath"))
    }

    fun testIsValidKeyPathReturnsFalseForHyphenated() {
        val service = project.service<JsonFinderProjectService>()
        assertFalse(service.isValidKeyPath("not-a-path"))
    }

    fun testSuggestSimilarReturnsNonEmptyForNearMissKey() {
        myFixture.configureByFile("keys.json")
        val service = project.service<JsonFinderProjectService>()
        val suggestions = service.suggestSimilar("auth.login.btn", maxResults = 5)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 5)
        assertTrue(
            "expected auth.login.button or auth.login.title among suggestions, got $suggestions",
            suggestions.any { it.startsWith("auth.login.") }
        )
    }

    fun testSuggestSimilarUsesLevenshteinFallbackWhenTier1Empty() {
        myFixture.configureByFile("keys.json")
        val service = project.service<JsonFinderProjectService>()
        // "xyz.abc.qqq" won't match any NameUtil pattern but Levenshtein should still rank keys
        val suggestions = service.suggestSimilar("auth.login.buton", maxResults = 5)
        // Tier 2 fallback: "auth.login.button" has edit distance 1 and should be returned
        assertTrue(
            "expected auth.login.button via Levenshtein fallback, got $suggestions",
            suggestions.contains("auth.login.button")
        )
    }
}
