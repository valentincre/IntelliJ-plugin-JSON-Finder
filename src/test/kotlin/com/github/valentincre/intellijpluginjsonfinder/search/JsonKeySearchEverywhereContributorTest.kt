package com.github.valentincre.intellijpluginjsonfinder.search

import com.github.valentincre.intellijpluginjsonfinder.service.ResolvedKeyDefinition
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase

// Integration tests for JsonKeySearchEverywhereContributor (Story 4.2).
class JsonKeySearchEverywhereContributorTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/search"

    private fun collectResults(pattern: String): List<ResolvedKeyDefinition> {
        val contributor = JsonKeySearchEverywhereContributor(project)
        val results = mutableListOf<ResolvedKeyDefinition>()
        contributor.fetchElements(pattern, EmptyProgressIndicator()) { def ->
            results.add(def)
            true
        }
        return results
    }

    // ─── Matching ─────────────────────────────────────────────────────────

    fun testContributorReturnsMatchingKeys() {
        myFixture.configureByFile("basic-project/en.json")
        myFixture.configureByFile("basic-project/fr.json")

        val results = collectResults("payments.checkout")

        assertEquals("Expected 2 results (en.json + fr.json)", 2, results.size)
        val fileNames = results.map { it.virtualFile.name }.toSet()
        assertTrue("Expected en.json in results", fileNames.contains("en.json"))
        assertTrue("Expected fr.json in results", fileNames.contains("fr.json"))
        val values = results.map { it.resolvedValue }.toSet()
        assertTrue("Expected 'Pay now' value", values.contains("Pay now"))
        assertTrue("Expected 'Payer' value", values.contains("Payer"))
    }

    // ─── Blank pattern ────────────────────────────────────────────────────

    fun testContributorReturnsEmptyForBlankPattern() {
        myFixture.configureByFile("basic-project/en.json")

        val results = collectResults("   ")

        assertTrue("Expected no results for blank pattern", results.isEmpty())
    }

    // ─── Unknown key ─────────────────────────────────────────────────────

    fun testContributorReturnsEmptyForUnknownKey() {
        myFixture.configureByFile("basic-project/en.json")

        val results = collectResults("no.such.key")

        assertTrue("Expected no results for unknown key", results.isEmpty())
    }

    // ─── Navigation ───────────────────────────────────────────────────────

    fun testContributorNavigatesToCorrectOffset() {
        myFixture.configureByFile("basic-project/en.json")

        val results = collectResults("payments.checkout")

        assertEquals("Expected 1 result from en.json", 1, results.size)
        val def = results.first()
        assertEquals("Expected en.json as target file", "en.json", def.virtualFile.name)
        assertTrue("Expected non-negative offset", def.offset >= 0)
        assertEquals("Expected 'Pay now' as resolved value", "Pay now", def.resolvedValue)

        val contributor = JsonKeySearchEverywhereContributor(project)
        val navigated = contributor.processSelectedItem(def, 0, "payments.checkout")
        assertTrue("processSelectedItem must return true", navigated)
    }

    // ─── Sorting ──────────────────────────────────────────────────────────

    fun testContributorResultsSortedByFile() {
        myFixture.configureByFile("basic-project/en.json")
        myFixture.configureByFile("basic-project/fr.json")

        val results = collectResults("auth.login.button")

        assertEquals("Expected 2 results for auth.login.button", 2, results.size)
        val paths = results.map { it.virtualFile.path }
        assertEquals("Results must be sorted by virtualFile.path", paths.sorted(), paths)
    }
}
