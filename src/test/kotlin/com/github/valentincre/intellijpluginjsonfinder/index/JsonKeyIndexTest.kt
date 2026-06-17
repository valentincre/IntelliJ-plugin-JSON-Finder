package com.github.valentincre.intellijpluginjsonfinder.index

import com.github.valentincre.intellijpluginjsonfinder.settings.JsonFinderSettings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContentImpl

/**
 * Integration tests for the JSON key indexing engine (Story 1.2).
 *
 * Tests use BasePlatformTestCase so they run inside a lightweight IntelliJ environment
 * that provides PSI, VFS, and FileBasedIndex infrastructure.
 *
 * Each test exercises JsonKeyIndexer directly via FileContentImpl to avoid async index
 * update timing issues, except where the full FileBasedIndex pipeline is tested.
 */
class JsonKeyIndexTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/index"

    // ─── Test 1: Flat JSON → correct key paths ────────────────────────────────

    fun testFlatJsonKeysIndexedCorrectly() {
        // {"button": "Click me", "title": "Hello World", "count": 42}
        val vFile = myFixture.configureByFile("simple.json").virtualFile
        val fileContent = FileContentImpl.createByFile(vFile, project)

        val result = JsonKeyIndexer().map(fileContent)

        assertTrue("'button' key must be indexed", result.containsKey("button"))
        assertEquals("Click me", result["button"]?.firstOrNull()?.resolvedValue)

        assertTrue("'title' key must be indexed", result.containsKey("title"))
        assertEquals("Hello World", result["title"]?.firstOrNull()?.resolvedValue)

        assertTrue("'count' key must be indexed", result.containsKey("count"))
        assertEquals("42", result["count"]?.firstOrNull()?.resolvedValue)
    }

    // ─── Test 2: Nested JSON → dotted paths (AC 1) ───────────────────────────

    fun testNestedJsonProducesDottedPaths() {
        // {"auth": {"login": {"button": "Sign in", "label": "Username"}, "logout": {...}}}
        val vFile = myFixture.configureByFile("nested.json").virtualFile
        val fileContent = FileContentImpl.createByFile(vFile, project)

        val result = JsonKeyIndexer().map(fileContent)

        assertTrue("'auth.login.button' must be indexed", result.containsKey("auth.login.button"))
        assertEquals("Sign in", result["auth.login.button"]?.firstOrNull()?.resolvedValue)

        assertTrue("'auth.login.label' must be indexed", result.containsKey("auth.login.label"))
        assertEquals("Username", result["auth.login.label"]?.firstOrNull()?.resolvedValue)

        assertTrue("'auth.logout.button' must be indexed", result.containsKey("auth.logout.button"))
        assertEquals("Sign out", result["auth.logout.button"]?.firstOrNull()?.resolvedValue)

        assertTrue("'home.title' must be indexed", result.containsKey("home.title"))
        assertEquals("Welcome", result["home.title"]?.firstOrNull()?.resolvedValue)

        // Intermediate object nodes must NOT appear as leaf entries
        assertFalse("'auth' object node must not be a leaf entry", result.containsKey("auth"))
        assertFalse("'auth.login' object node must not be a leaf entry", result.containsKey("auth.login"))
    }

    // ─── Test 3: Key path normalization → always lowercase ────────────────────

    fun testKeyPathsAreLowercaseNormalized() {
        // {"Auth": {"Login": "value"}} → must index as "auth.login"
        val vFile = myFixture.configureByText("uppercase.json", """{"Auth": {"Login": "value"}}""").virtualFile
        val fileContent = FileContentImpl.createByFile(vFile, project)

        val result = JsonKeyIndexer().map(fileContent)

        assertTrue("Lowercase path 'auth.login' must exist", result.containsKey("auth.login"))
        assertFalse("Mixed-case path 'Auth.Login' must NOT exist", result.containsKey("Auth.Login"))
        assertEquals("value", result["auth.login"]?.firstOrNull()?.resolvedValue)
    }

    // ─── Test 4: Malformed JSON → no exception, IDE stable (AC 5) ────────────

    fun testMalformedJsonDoesNotThrow() {
        // File content is "{ invalid json content here\n" — not valid JSON
        val vFile = myFixture.configureByFile("malformed.json").virtualFile
        val fileContent = FileContentImpl.createByFile(vFile, project)

        // The indexer must not propagate any exception to the caller (may return empty or partial)
        try {
            JsonKeyIndexer().map(fileContent)
        } catch (e: Exception) {
            fail("JsonKeyIndexer.map() must not throw for malformed JSON: ${e.message}")
        }
    }

    // ─── Test 5: Excluded files not indexed (AC 7) ────────────────────────────

    fun testPackageJsonExcludedFromInputFilter() {
        val file = myFixture.configureByText("package.json", """{"name": "my-app", "version": "1.0.0"}""").virtualFile
        val filter = JsonKeyIndex().getInputFilter()
        assertFalse("package.json must be excluded by the input filter", filter.acceptInput(file))
    }

    fun testTsconfigJsonExcludedFromInputFilter() {
        val file = myFixture.configureByText("tsconfig.json", """{}""").virtualFile
        val filter = JsonKeyIndex().getInputFilter()
        assertFalse("tsconfig.json must be excluded by the input filter", filter.acceptInput(file))
    }

    fun testRegularLocaleJsonAcceptedByInputFilter() {
        val file = myFixture.configureByText("en.json", """{"key": "value"}""").virtualFile
        val filter = JsonKeyIndex().getInputFilter()
        assertTrue("en.json must be accepted by the input filter", filter.acceptInput(file))
    }

    fun testNonJsonFileExcludedFromInputFilter() {
        val file = myFixture.configureByText("config.yaml", "key: value").virtualFile
        val filter = JsonKeyIndex().getInputFilter()
        assertFalse("Non-.json files must be excluded by the input filter", filter.acceptInput(file))
    }

    // ─── Test 6: Multiple files → separate entries for the same key (AC 4) ───

    fun testMultipleFilesProduceSeparateEntriesForSameKey() {
        // en.json and fr.json both have "auth.title" with different resolved values
        val enFile = myFixture.configureByFile("multilingual/en.json").virtualFile
        val frFile = myFixture.copyFileToProject("multilingual/fr.json", "fr.json")

        val enContent = FileContentImpl.createByFile(enFile, project)
        val frContent = FileContentImpl.createByFile(frFile, project)

        val enResult = JsonKeyIndexer().map(enContent)
        val frResult = JsonKeyIndexer().map(frContent)

        // Both files must produce an entry for "auth.title"
        assertTrue("en.json must index 'auth.title'", enResult.containsKey("auth.title"))
        assertTrue("fr.json must index 'auth.title'", frResult.containsKey("auth.title"))

        // Values must be language-specific
        assertEquals("Sign in", enResult["auth.title"]?.firstOrNull()?.resolvedValue)
        assertEquals("Connexion", frResult["auth.title"]?.firstOrNull()?.resolvedValue)

        // Both files must also have "home.welcome"
        assertTrue("en.json must index 'home.welcome'", enResult.containsKey("home.welcome"))
        assertTrue("fr.json must index 'home.welcome'", frResult.containsKey("home.welcome"))
    }

    // ─── Test 7: Offset stored for each leaf value ────────────────────────────

    fun testOffsetIsStoredForLeafValues() {
        val vFile = myFixture.configureByText("offset_test.json", """{"key": "value"}""").virtualFile
        val fileContent = FileContentImpl.createByFile(vFile, project)

        val result = JsonKeyIndexer().map(fileContent)

        val entry = result["key"]?.firstOrNull()
        assertNotNull("Entry for 'key' must exist", entry)
        // Offset must be positive (the value "value" starts after {"key": )
        assertTrue("Offset must be a non-negative position in the file", entry!!.offset >= 0)
    }

    // ─── Test 8: Settings include pattern accepts matching JSON file (AC 1) ───

    fun testSettingsIncludePatternAcceptsMatchingFile() {
        val settings = project.service<JsonFinderSettings>()
        val originalState = settings.state
        try {
            settings.loadState(
                JsonFinderSettings.State(
                    includePatterns = listOf("**/*.json"),
                    excludePatterns = emptyList()
                )
            )
            val file = myFixture.configureByText("locale.json", """{"greeting": "Hello"}""").virtualFile
            val filter = JsonKeyIndex().getInputFilter()
            assertTrue("File matching include pattern must be accepted by the filter", filter.acceptInput(file))
        } finally {
            settings.loadState(originalState)
        }
    }

    // ─── Test 9: Settings exclude pattern rejects file (AC 5) ─────────────────

    fun testSettingsExcludePatternRejectsMatchingFile() {
        val settings = project.service<JsonFinderSettings>()
        val originalState = settings.state
        try {
            settings.loadState(
                JsonFinderSettings.State(
                    includePatterns = listOf("**/*.json"),
                    excludePatterns = listOf("**/excluded.json")
                )
            )
            val file = myFixture.configureByText("excluded.json", """{"secret": "hidden"}""").virtualFile
            val filter = JsonKeyIndex().getInputFilter()
            assertFalse("File matching exclude pattern must be rejected by the filter", filter.acceptInput(file))
        } finally {
            settings.loadState(originalState)
        }
    }

    // ─── Test 10: Empty include patterns reject all files (AC 1) ──────────────

    fun testEmptyIncludePatternsRejectAllFiles() {
        val settings = project.service<JsonFinderSettings>()
        val originalState = settings.state
        try {
            settings.loadState(
                JsonFinderSettings.State(
                    includePatterns = emptyList(),
                    excludePatterns = emptyList()
                )
            )
            val file = myFixture.configureByText("any.json", """{"key": "value"}""").virtualFile
            val filter = JsonKeyIndex().getInputFilter()
            assertFalse("Empty include patterns must cause all files to be rejected", filter.acceptInput(file))
        } finally {
            settings.loadState(originalState)
        }
    }

    // ─── Test 11: Previously excluded file included after pattern change (AC 4) ─

    fun testPreviouslyExcludedFileAcceptedAfterPatternChange() {
        val settings = project.service<JsonFinderSettings>()
        val originalState = settings.state
        try {
            // Phase 1: exclude.json is excluded
            settings.loadState(
                JsonFinderSettings.State(
                    includePatterns = listOf("**/*.json"),
                    excludePatterns = listOf("**/excluded.json")
                )
            )
            val file = myFixture.configureByText("excluded.json", """{"key": "value"}""").virtualFile
            val filter1 = JsonKeyIndex().getInputFilter()
            assertFalse("File must be rejected when in exclude list", filter1.acceptInput(file))

            // Phase 2: pattern changed — excluded.json is no longer excluded
            settings.loadState(
                JsonFinderSettings.State(
                    includePatterns = listOf("**/*.json"),
                    excludePatterns = emptyList()
                )
            )
            val filter2 = JsonKeyIndex().getInputFilter()
            assertTrue("File must be accepted after exclude pattern is removed", filter2.acceptInput(file))
        } finally {
            settings.loadState(originalState)
        }
    }

    // ─── Test 12: Relative glob pattern (e.g. src/**/*.json) matches via project-root relativization ─

    fun testRelativeGlobPatternMatchesFileInSubdirectory() {
        val settings = project.service<JsonFinderSettings>()
        val originalState = settings.state
        try {
            settings.loadState(
                JsonFinderSettings.State(
                    includePatterns = listOf("src/**/*.json"),
                    excludePatterns = emptyList()
                )
            )
            val file = myFixture.addFileToProject("src/i18n/messages.json", """{"hello": "world"}""").virtualFile
            val filter = JsonKeyIndex().getInputFilter()
            assertTrue(
                "File inside src/ must be accepted by relative pattern 'src/**/*.json'",
                filter.acceptInput(file)
            )
        } finally {
            settings.loadState(originalState)
        }
    }
}
