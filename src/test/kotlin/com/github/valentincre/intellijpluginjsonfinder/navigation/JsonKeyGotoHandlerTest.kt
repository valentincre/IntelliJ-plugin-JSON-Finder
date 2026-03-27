package com.github.valentincre.intellijpluginjsonfinder.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase

// Integration tests for JsonKeyGotoHandler detection logic (Story 2.1).
// Navigation targets are null in this story — deferred to Stories 2.2 and 2.3.
class JsonKeyGotoHandlerTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/navigation"

    // ─── Handler does not throw on valid candidate ─────────────────────────

    fun testHandlerReturnsNullOnCandidateWithoutThrowing() {
        myFixture.configureByFile("single-match/consumer.ts")
        val handler = JsonKeyGotoHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        // Returns null — navigation targets deferred to Story 2.2
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        assertNull(targets)
    }

    fun testHandlerReturnsNullOnPlainStringElement() {
        // Configures a file with a non-candidate literal
        myFixture.configureByText("test.ts", "const x = \"just a string\";")
        val handler = JsonKeyGotoHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        assertNull(targets)
    }

    fun testHandlerReturnsNullOnNullElement() {
        myFixture.configureByFile("single-match/consumer.ts")
        val handler = JsonKeyGotoHandler()
        // Passing null directly — must not throw
        val targets = handler.getGotoDeclarationTargets(null, 0, myFixture.editor)
        assertNull(targets)
    }

    // ─── Multi-match fixture is loadable ──────────────────────────────────

    fun testMultiMatchFixtureLoads() {
        myFixture.configureByFile("multi-match/consumer.ts")
        assertNotNull(myFixture.file)
        val handler = JsonKeyGotoHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = handler.getGotoDeclarationTargets(element, 0, myFixture.editor)
        assertNull(targets)
    }

    // ─── Story 2.2: Single-match navigation ───────────────────────────────

    fun testSingleMatchNavigatesToJsonDefinition() {
        // Load en.json to index auth.login.button, then open a JSON consumer as the active editor.
        // JSON string values are proper leaf PSI nodes with quotes in their text — no TypeScript plugin needed.
        myFixture.configureByFile("single-match/en.json")
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>auth.login.button\"}")
        val handler = JsonKeyGotoHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
        assertNotNull("Expected non-null targets for single match", targets)
        assertEquals("Expected 1 navigation target", 1, targets!!.size)
        assertEquals("Expected target in en.json", "en.json", targets[0].containingFile.name)
        val targetText = targets[0].text ?: ""
        val parentText = targets[0].parent?.text ?: ""
        assertTrue(
            "Expected navigation target to be the 'Sign in' value node for auth.login.button",
            targetText.contains("Sign in") || parentText.contains("Sign in"),
        )
    }

    fun testNoMatchKeyReturnsNull() {
        // JSON consumer value gives a proper quoted-string PSI leaf; no.match.key is not indexed
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>no.match.key\"}")
        val handler = JsonKeyGotoHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
        assertNull("Expected null when key has no definition", targets)
    }

    // ─── Story 2.3: Multi-match disambiguation ────────────────────────────

    fun testMultiMatchReturnsAllTargets() {
        // Index dashboard.title from two separate JSON files, then query via a JSON consumer.
        myFixture.configureByFile("multi-match/en.json")
        myFixture.configureByFile("multi-match/fr.json")
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>dashboard.title\"}")
        val handler = JsonKeyGotoHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
        assertNotNull("Expected non-null targets for multi-match", targets)
        assertEquals("Expected 2 navigation targets", 2, targets!!.size)
        val fileNames = targets.map { it.containingFile.name }.toSet()
        assertTrue("Expected en.json in targets", fileNames.contains("en.json"))
        assertTrue("Expected fr.json in targets", fileNames.contains("fr.json"))
    }

    fun testNoDefinitionsIndexedReturnsNull() {
        // No JSON files indexed — findDefinitions returns empty list → when(0) branch → null.
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>dashboard.title\"}")
        val handler = JsonKeyGotoHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
        assertNull("Expected null when no definitions are indexed", targets)
    }

    fun testMultiMatchPartiallyInvalidDefinitionsReturnsRemainingTargets() {
        // Index dashboard.title in en.json and fr.json, then delete fr.json from VFS so its
        // VirtualFile becomes invalid. The isValid guard in the else branch must filter it out,
        // leaving only the en.json target (or 0 targets if the index was updated synchronously
        // before navigation — either way, no invalid-file element must appear in the result).
        myFixture.configureByFile("multi-match/en.json")
        myFixture.configureByFile("multi-match/fr.json")
        val frVirtualFile = myFixture.file.virtualFile
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            frVirtualFile.delete(this)
        }
        myFixture.configureByText("consumer.json", "{\"ref\": \"<caret>dashboard.title\"}")
        val handler = JsonKeyGotoHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
        // Either the index was updated synchronously (1 result → single-match branch → 1 target)
        // or the stale entry was filtered by isValid (else branch → 1 target). In both cases:
        // - result must not be null (en.json definition is still valid)
        // - result must not contain an element from the deleted fr.json
        assertNotNull("Expected non-null targets: en.json definition is still valid", targets)
        for (target in targets!!) {
            assertTrue("All targets must come from a valid VirtualFile", target.containingFile.virtualFile.isValid)
            assertEquals("Only en.json should remain after fr.json deletion", "en.json", target.containingFile.name)
        }
    }
}
