package com.github.valentincre.intellijpluginjsonfinder.search

import com.intellij.find.FindModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase

// Integration tests for JsonKeySearchScope and JsonKeyFindInProjectSearchEngine (Story 4.1).
class JsonKeySearchScopeTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/search"

    // ─── Scope: contains ──────────────────────────────────────────────────

    fun testScopeContainsIndexedJsonFile() {
        myFixture.configureByText("keys.json", "{\"foo\": {\"bar\": \"baz\"}}")
        val scope = JsonKeySearchScope(project)
        assertTrue(
            "Scope must contain a .json file",
            scope.contains(myFixture.file.virtualFile),
        )
    }

    fun testScopeExcludesNonJsonFile() {
        myFixture.configureByText("consumer.ts", "const key = \"foo.bar\";")
        val scope = JsonKeySearchScope(project)
        assertFalse(
            "Scope must not contain a .ts file",
            scope.contains(myFixture.file.virtualFile),
        )
    }

    // ─── Scope: excluded names ─────────────────────────────────────────────

    fun testScopeExcludesPackageJson() {
        myFixture.configureByText("package.json", "{\"name\": \"my-app\"}")
        val scope = JsonKeySearchScope(project)
        assertFalse(
            "Scope must not contain package.json",
            scope.contains(myFixture.file.virtualFile),
        )
    }

    // ─── SearchEngine: createSearcher ─────────────────────────────────────

    fun testSearchEngineReturnsNullWhenNoJsonKeyScope() {
        // Default FindModel has no custom scope — engine must return null.
        val findModel = FindModel().apply { stringToFind = "foo.bar" }
        val engine = JsonKeyFindInProjectSearchEngine()
        val searcher = engine.createSearcher(findModel, project)
        assertNull("Engine must return null when JSON Keys scope is not active", searcher)
    }

    fun testSearchEngineReturnsSearcherForJsonKeyScope() {
        val scope = JsonKeySearchScope(project)
        val findModel = FindModel().apply {
            stringToFind = "foo.bar"
            isCustomScope = true
            customScope = scope
        }
        val engine = JsonKeyFindInProjectSearchEngine()
        val searcher = engine.createSearcher(findModel, project)
        assertNotNull("Engine must return a searcher when JSON Keys scope is active", searcher)
        assertTrue("Searcher must be reliable", searcher!!.isReliable())
    }

    // ─── Search: results match only key definitions ────────────────────────

    fun testSearchReturnsOnlyKeyMatches() {
        // Index both fixture files so payments.checkout is in the index.
        myFixture.configureByFile("basic-project/en.json")
        myFixture.configureByFile("basic-project/fr.json")

        val scope = JsonKeySearchScope(project)
        val findModel = FindModel().apply {
            stringToFind = "payments.checkout"
            isCustomScope = true
            customScope = scope
        }
        val engine = JsonKeyFindInProjectSearchEngine()
        val searcher = engine.createSearcher(findModel, project)

        assertNotNull("Expected searcher for JSON Keys scope", searcher)
        val results = searcher!!.searchForOccurrences()

        // Both en.json and fr.json define payments.checkout — expect two results.
        assertEquals("Expected 2 files (en.json + fr.json)", 2, results.size)

        val fileNames = results.map { it.name }.toSet()
        assertTrue("Expected en.json in results", fileNames.contains("en.json"))
        assertTrue("Expected fr.json in results", fileNames.contains("fr.json"))
    }

    fun testSearchReturnsEmptyForUnknownKey() {
        myFixture.configureByFile("basic-project/en.json")

        val scope = JsonKeySearchScope(project)
        val findModel = FindModel().apply {
            stringToFind = "no.such.key.anywhere"
            isCustomScope = true
            customScope = scope
        }
        val engine = JsonKeyFindInProjectSearchEngine()
        val searcher = engine.createSearcher(findModel, project)

        assertNotNull("Expected searcher even for unknown key", searcher)
        val results = searcher!!.searchForOccurrences()
        assertTrue("Expected empty results for unknown key", results.isEmpty())
    }

    fun testSearchReturnsEmptyForBlankQuery() {
        myFixture.configureByFile("basic-project/en.json")

        val scope = JsonKeySearchScope(project)
        val findModel = FindModel().apply {
            stringToFind = "   "
            isCustomScope = true
            customScope = scope
        }
        val engine = JsonKeyFindInProjectSearchEngine()
        val searcher = engine.createSearcher(findModel, project)

        assertNotNull("Expected searcher for blank query", searcher)
        val results = searcher!!.searchForOccurrences()
        assertTrue("Expected empty results for blank query", results.isEmpty())
    }

    // ─── Search: isCovered ────────────────────────────────────────────────

    fun testIsCoveredReturnsTrueForJsonFile() {
        myFixture.configureByText("keys.json", "{\"foo\": \"bar\"}")

        val scope = JsonKeySearchScope(project)
        val findModel = FindModel().apply {
            stringToFind = "foo"
            isCustomScope = true
            customScope = scope
        }
        val engine = JsonKeyFindInProjectSearchEngine()
        val searcher = engine.createSearcher(findModel, project)!!

        assertTrue(
            "Searcher must cover .json files",
            searcher.isCovered(myFixture.file.virtualFile),
        )
    }

    fun testIsCoveredReturnsFalseForNonJsonFile() {
        myFixture.configureByText("consumer.ts", "const k = \"foo.bar\";")

        val scope = JsonKeySearchScope(project)
        val findModel = FindModel().apply {
            stringToFind = "foo"
            isCustomScope = true
            customScope = scope
        }
        val engine = JsonKeyFindInProjectSearchEngine()
        val searcher = engine.createSearcher(findModel, project)!!

        assertFalse(
            "Searcher must not cover non-json files",
            searcher.isCovered(myFixture.file.virtualFile),
        )
    }
}
