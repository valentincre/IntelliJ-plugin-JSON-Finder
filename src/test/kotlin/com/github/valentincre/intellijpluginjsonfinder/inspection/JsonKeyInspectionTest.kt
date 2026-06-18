package com.github.valentincre.intellijpluginjsonfinder.inspection

import com.github.valentincre.intellijpluginjsonfinder.settings.JsonFinderSettings
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

// Integration tests for JsonKeyInspection (Story 5.1 / 7.2).
// Tests call buildVisitor() directly via ProblemsHolder — avoids relying on doHighlighting()
// dispatch which is unreliable for language="" inspections in IntelliJ 2025.2 tests.
class JsonKeyInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/annotator"

    // Builds a ProblemsHolder for the current file, runs the inspection visitor on the element
    // at the caret, and returns the collected problem descriptors.
    private fun inspectAtCaret(): List<ProblemDescriptor> {
        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = JsonKeyInspection().buildVisitor(holder, false)
        val leaf = checkNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "No element at caret offset"
        }
        val parent = leaf.parent
        val element = if (parent != null && parent.text == leaf.text) parent else leaf
        element.accept(visitor)
        return holder.results
    }

    // ─── Broken key ───────────────────────────────────────────────────────

    fun testBrokenKeyProducesProblem() {
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login.missing"}""")
        val problems = inspectAtCaret()
        assertTrue("Expected problem for broken key 'auth.login.missing'", problems.isNotEmpty())
        assertTrue(
            "Problem description must contain the key name",
            problems.any { it.descriptionTemplate.contains("auth.login.missing") },
        )
    }

    // ─── Valid key ────────────────────────────────────────────────────────

    fun testValidKeyProducesNoProblem() {
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login.button"}""")
        val problems = inspectAtCaret()
        assertTrue("Expected no problem for valid key 'auth.login.button'", problems.isEmpty())
    }

    // ─── Non-key-path string ──────────────────────────────────────────────

    fun testNonKeyPathStringProducesNoProblem() {
        myFixture.configureByText("consumer.json", """{"ref": "<caret>plainstring"}""")
        val problems = inspectAtCaret()
        assertTrue("Expected no problem for non-key-path string 'plainstring'", problems.isEmpty())
    }

    // ─── Non-string element ───────────────────────────────────────────────

    fun testInspectionDoesNotCrashOnNonStringElement() {
        myFixture.configureByText("consumer.json", """{"count": <caret>42}""")
        inspectAtCaret()
    }

    // ─── Dynamic string suppression (Story 5.2) ───────────────────────────

    private class BinaryExpressionWrapper(delegate: PsiElement) : PsiElement by delegate

    fun testNonQuotedStringProducesNoProblem() {
        myFixture.configureByText("consumer.json", """{"count": <caret>42}""")
        val problems = inspectAtCaret()
        assertTrue("Expected no problem for non-quoted element", problems.isEmpty())
    }

    fun testKeyPathCandidateBoundaryValues() {
        assertTrue(
            """isKeyPathCandidate("auth.login") must be true""",
            KeyPathUtil.isKeyPathCandidate("auth.login")
        )
        assertFalse(
            """isKeyPathCandidate("prefix.") must be false (trailing dot)""",
            KeyPathUtil.isKeyPathCandidate("prefix.")
        )
        assertFalse(
            """isKeyPathCandidate(".suffix") must be false (leading dot)""",
            KeyPathUtil.isKeyPathCandidate(".suffix")
        )
        assertFalse(
            """isKeyPathCandidate("") must be false""",
            KeyPathUtil.isKeyPathCandidate("")
        )
        assertFalse(
            $$"""isKeyPathCandidate("feature.${x}.label") must be false""",
            KeyPathUtil.isKeyPathCandidate($$"feature.${x}.label")
        )
    }

    fun testPartialPathFragmentWithNoIndexMatchProducesProblem() {
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login"}""")
        val problems = inspectAtCaret()
        assertTrue(
            "Expected problem for partial path 'auth.login' absent from the index",
            problems.isNotEmpty(),
        )
        assertTrue(
            "Problem description must contain the key name 'auth.login'",
            problems.any { it.descriptionTemplate.contains("auth.login") },
        )
    }

    fun testBacktickTemplateLiteralProducesNoProblem() {
        myFixture.configureByText("consumer.json", """{"count": <caret>42}""")
        val realElement = checkNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "No element at caret offset"
        }
        val backtickElement = object : PsiElement by realElement {
            override fun getText(): String = $$"`feature.${featureFlag}.label`"
        }
        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = JsonKeyInspection().buildVisitor(holder, false)
        backtickElement.accept(visitor)
        assertTrue(
            "Expected no problem for backtick template literal",
            holder.results.isEmpty(),
        )
    }

    fun testConcatenationParentSuppressesProblem() {
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login"}""")
        val realElement = checkNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "No element at caret offset"
        }
        val binaryParent = BinaryExpressionWrapper(realElement)
        val elementInBinaryExpr = object : PsiElement by realElement {
            override fun getParent(): PsiElement = binaryParent
        }
        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = JsonKeyInspection().buildVisitor(holder, false)
        elementInBinaryExpr.accept(visitor)
        assertTrue(
            "Expected no problem for 'auth.login' inside a concatenation (Binary) expression",
            holder.results.isEmpty(),
        )
    }

    // ─── Quick-fix suggestions (Story 5.3) ───────────────────────────────────

    fun testBrokenKeyWithSuggestionProducesQuickFix() {
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login.btn"}""")
        val problems = inspectAtCaret()
        assertTrue("Expected problem for broken key 'auth.login.btn'", problems.isNotEmpty())
        val fixes = problems.first().fixes?.map { it.name } ?: emptyList()
        assertTrue(
            "Expected quick-fix suggestion containing 'auth.login.button', got: $fixes",
            fixes.any { it.contains("auth.login.button") },
        )
    }

    fun testBrokenKeyWithNoSuggestionHasNoFixes() {
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>zzz.yyy.xxx.www"}""")
        val problems = inspectAtCaret()
        assertTrue("Expected problem for broken key 'zzz.yyy.xxx.www'", problems.isNotEmpty())
        val fixes = problems.first().fixes ?: emptyArray()
        assertTrue(
            "Expected no quick-fixes for key with no close match",
            fixes.isEmpty(),
        )
    }

    fun testQuickFixReplacesTextInFile() {
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login.btn"}""")
        val element = checkNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "No element at caret offset"
        }
        val fix = JsonKeyNotFoundFix(element, "auth.login.button")
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(myFixture.project) {
            fix.invoke(myFixture.project, myFixture.editor, myFixture.file)
        }
        assertEquals("""{"ref": "auth.login.button"}""", myFixture.file.text)
    }

    // ─── Plugin disabled (Story 7.1 — AC3) ───────────────────────────────────

    fun testInspectionProducesNoProblemWhenPluginDisabled() {
        project.service<JsonFinderSettings>().loadState(
            project.service<JsonFinderSettings>().state.copy(isEnabled = false)
        )
        try {
            myFixture.configureByFile("en.json")
            myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login.missing"}""")
            val problems = inspectAtCaret()
            assertTrue("Expected no problem when plugin is disabled", problems.isEmpty())
        } finally {
            project.service<JsonFinderSettings>().loadState(
                project.service<JsonFinderSettings>().state.copy(isEnabled = true)
            )
        }
    }
}
