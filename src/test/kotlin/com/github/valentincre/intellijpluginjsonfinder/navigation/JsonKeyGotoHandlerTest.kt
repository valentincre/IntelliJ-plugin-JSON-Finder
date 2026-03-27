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
}
