package com.github.valentincre.intellijpluginjsonfinder.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

// Integration tests for JsonKeyDocumentationProvider (Story 3.1).
// Uses JSON consumer files for PSI leaf nodes — .ts files are plain text without the TypeScript plugin.
class JsonKeyDocumentationProviderTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/documentation"

    // ─── Single match ──────────────────────────────────────────────────────

    fun testSingleMatchShowsValue() {
        // Index auth.login.button = "Sign in" from en.json, then query from a JSON consumer.
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>auth.login.button\"}")
        val provider = JsonKeyDocumentationProvider()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val doc = provider.generateDoc(element!!, null)
        assertNotNull("Expected non-null doc for indexed key", doc)
        assertTrue("Expected backtick-quoted value in doc", doc!!.contains("`Sign in`"))
    }

    // ─── Multi-match ───────────────────────────────────────────────────────

    fun testMultiMatchShowsAllValues() {
        // Index dashboard.title from both en.json and fr.json, then query from a JSON consumer.
        myFixture.configureByFile("en.json")
        myFixture.configureByFile("fr.json")
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>dashboard.title\"}")
        val provider = JsonKeyDocumentationProvider()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val doc = provider.generateDoc(element!!, null)
        assertNotNull("Expected non-null doc for multi-match key", doc)
        assertTrue("Expected en.json attribution", doc!!.contains("en.json"))
        assertTrue("Expected fr.json attribution", doc.contains("fr.json"))
    }

    // ─── No match ──────────────────────────────────────────────────────────

    fun testNoMatchReturnsNull() {
        // No JSON indexed — findDefinitions returns empty → generateDoc must return null.
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>no.match.key\"}")
        val provider = JsonKeyDocumentationProvider()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val doc = provider.generateDoc(element!!, null)
        assertNull("Expected null doc for unindexed key", doc)
    }

    // ─── Truncation ────────────────────────────────────────────────────────

    fun testLongValueIsTruncatedAt100Chars() {
        // long.value in en.json is > 100 chars; tooltip must truncate and append ellipsis.
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>long.value\"}")
        val provider = JsonKeyDocumentationProvider()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val doc = provider.generateDoc(element!!, null)
        assertNotNull("Expected non-null doc for indexed long key", doc)
        assertTrue("Expected truncation ellipsis in doc", doc!!.contains("…"))
        // The backtick-quoted truncated value must not exceed 100 visible chars (plus the ellipsis).
        val backtickContent = doc.substringAfter("`").substringBefore("…")
        assertTrue("Truncated content must be exactly 100 chars", backtickContent.length == 100)
    }

    // ─── Non-key-path string ───────────────────────────────────────────────

    fun testNonKeyPathReturnsNull() {
        // A plain single-word string is not a dotted-path candidate.
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>plainstring\"}")
        val provider = JsonKeyDocumentationProvider()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val customElement = provider.getCustomDocumentationElement(
            myFixture.editor,
            myFixture.file,
            element,
            myFixture.caretOffset,
        )
        assertNull("Expected null for non-key-path string", customElement)
    }
}
