package com.github.valentincre.intellijpluginjsonfinder.annotator

import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Method
import java.lang.reflect.Proxy

// Integration tests for JsonKeyAnnotator (Story 5.1).
// Uses JSON consumer files for PSI leaf nodes — .ts files are plain text without the TypeScript plugin.
// language="any" annotators are not triggered by doHighlighting() in IntelliJ 2025.2 tests, so the
// annotator is called directly. AnnotationHolder/AnnotationBuilder are implemented via dynamic proxy
// to avoid compile-time dependency on types that are not accessible in the test classpath.
class JsonKeyAnnotatorTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/annotator"

    // Records annotations produced by a single annotator.annotate() call.
    private data class CapturedAnnotation(val severity: HighlightSeverity, val description: String)

    // Creates a self-referential AnnotationBuilder proxy: fluent methods return the SAME proxy so
    // that create() is always called on the proxy that captured severity/message.
    private fun builderProxy(
        severity: HighlightSeverity,
        message: String,
        captured: MutableList<CapturedAnnotation>,
    ): AnnotationBuilder {
        // Use an array cell to hold the proxy reference before it is assigned (avoids circular init).
        val ref = arrayOfNulls<Any>(1)
        ref[0] = Proxy.newProxyInstance(
            AnnotationBuilder::class.java.classLoader,
            arrayOf(AnnotationBuilder::class.java),
        ) { _, method: Method, _ ->
            when (method.name) {
                "create" -> { captured.add(CapturedAnnotation(severity, message)); null }
                // All fluent methods return the SAME proxy to preserve the captured state.
                else -> ref[0]
            }
        }
        @Suppress("UNCHECKED_CAST")
        return ref[0] as AnnotationBuilder
    }

    // Creates a dynamic proxy for AnnotationHolder that records newAnnotation() calls.
    private fun recordingHolder(captured: MutableList<CapturedAnnotation>): AnnotationHolder {
        return Proxy.newProxyInstance(
            AnnotationHolder::class.java.classLoader,
            arrayOf(AnnotationHolder::class.java),
        ) { _, method: Method, args ->
            when (method.name) {
                "newAnnotation" -> {
                    val severity = args?.getOrNull(0) as? HighlightSeverity ?: return@newProxyInstance null
                    val message = args?.getOrNull(1) as? String ?: ""
                    builderProxy(severity, message, captured)
                }
                "newSilentAnnotation" -> {
                    val severity = args?.getOrNull(0) as? HighlightSeverity ?: return@newProxyInstance null
                    builderProxy(severity, "", captured)
                }
                "isBatchMode" -> false
                else -> null
            }
        } as AnnotationHolder
    }

    // Calls the annotator directly on the element at the current caret position.
    private fun annotateAtCaret(): List<CapturedAnnotation> {
        val element = checkNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "No element at caret offset"
        }
        val captured = mutableListOf<CapturedAnnotation>()
        JsonKeyAnnotator().annotate(element, recordingHolder(captured))
        return captured
    }

    // ─── Broken key ───────────────────────────────────────────────────────

    fun testBrokenKeyProducesWarning() {
        // Index auth.login.button from en.json; auth.login.missing is not indexed → WARNING expected.
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login.missing"}""")
        val warnings = annotateAtCaret().filter { it.severity == HighlightSeverity.WARNING }
        assertTrue("Expected WARNING annotation for broken key 'auth.login.missing'", warnings.isNotEmpty())
        assertTrue(
            "Warning message must contain the key name",
            warnings.any { it.description.contains("auth.login.missing") },
        )
    }

    // ─── Valid key ────────────────────────────────────────────────────────

    fun testValidKeyProducesNoWarning() {
        // auth.login.button is in en.json → annotator must NOT emit a warning for it.
        // configureByFile populates the FileBasedIndex synchronously in BasePlatformTestCase;
        // configureByText then switches the active editor while retaining the indexed en.json.
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login.button"}""")
        val warnings = annotateAtCaret().filter { it.severity == HighlightSeverity.WARNING }
        assertTrue("Expected no WARNING for valid key 'auth.login.button'", warnings.isEmpty())
    }

    // ─── Non-key-path string ──────────────────────────────────────────────

    fun testNonKeyPathStringProducesNoWarning() {
        // "plainstring" has no dots — isKeyPathCandidate returns false; no annotation produced.
        myFixture.configureByText("consumer.json", """{"ref": "<caret>plainstring"}""")
        val annotations = annotateAtCaret()
        assertTrue("Expected no annotation for non-key-path string 'plainstring'", annotations.isEmpty())
    }

    // ─── Non-string element ───────────────────────────────────────────────

    fun testAnnotatorDoesNotCrashOnNonStringElement() {
        // A JSON numeric value has text that doesn't start/end with quotes — annotator must return early.
        myFixture.configureByText("consumer.json", """{"count": <caret>42}""")
        // annotateAtCaret() returns without throwing; assertion-free — crash = test failure.
        annotateAtCaret()
    }

    // ─── Dynamic string suppression (Story 5.2) ───────────────────────────

    // Named nested class whose simpleName contains "Binary" — used to simulate a concatenation
    // expression parent node without importing TypeScript/JS PSI types.
    private class BinaryExpressionWrapper(delegate: PsiElement) : PsiElement by delegate

    fun testNonQuotedStringProducesNoAnnotation() {
        // A JSON numeric value does not start/end with quotes — annotator returns early.
        myFixture.configureByText("consumer.json", """{"count": <caret>42}""")
        val annotations = annotateAtCaret()
        assertTrue("Expected no annotation for non-quoted element", annotations.isEmpty())
    }

    fun testKeyPathCandidateBoundaryValues() {
        // Pure unit tests for KeyPathUtil — no IntelliJ fixture infrastructure needed.
        assertTrue("""isKeyPathCandidate("auth.login") must be true""",
            KeyPathUtil.isKeyPathCandidate("auth.login"))
        assertFalse("""isKeyPathCandidate("prefix.") must be false (trailing dot)""",
            KeyPathUtil.isKeyPathCandidate("prefix."))
        assertFalse("""isKeyPathCandidate(".suffix") must be false (leading dot)""",
            KeyPathUtil.isKeyPathCandidate(".suffix"))
        assertFalse("""isKeyPathCandidate("") must be false""",
            KeyPathUtil.isKeyPathCandidate(""))
        // A string containing ${ (template literal body) does not satisfy the word-char regex.
        // Use ${'$'} to embed a literal dollar sign in a Kotlin string literal.
        assertFalse("""isKeyPathCandidate("feature.${'$'}{x}.label") must be false""",
            KeyPathUtil.isKeyPathCandidate("feature.${'$'}{x}.label"))
    }

    fun testPartialPathFragmentWithNoIndexMatchProducesWarning() {
        // "auth.login" is a valid key-path candidate but the index only has "auth.login.button".
        // JsonKeyIndexer only indexes leaf values (strings, numbers, booleans) — intermediate
        // JSON objects are never emitted as index entries, so "auth.login" correctly resolves to
        // empty and a broken-key warning is expected for a plain literal (no concatenation parent).
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login"}""")
        val warnings = annotateAtCaret().filter { it.severity == HighlightSeverity.WARNING }
        assertTrue(
            "Expected WARNING for partial path 'auth.login' that is absent from the index",
            warnings.isNotEmpty(),
        )
        assertTrue(
            "Warning message must contain the key name 'auth.login'",
            warnings.any { it.description.contains("auth.login") },
        )
    }

    fun testBacktickTemplateLiteralProducesNoAnnotation() {
        // Backtick template literals have PSI text starting with '`', not '"' or '\''.
        // The annotator's `else -> return` branch in the quote-strip step filters them out
        // before any index lookup — this test documents that guarantee directly (AC1).
        myFixture.configureByText("consumer.json", """{"count": <caret>42}""")
        val realElement = checkNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "No element at caret offset"
        }
        val backtickElement = object : PsiElement by realElement {
            override fun getText(): String = "`feature.${'$'}{featureFlag}.label`"
        }
        val captured = mutableListOf<CapturedAnnotation>()
        JsonKeyAnnotator().annotate(backtickElement, recordingHolder(captured))
        assertTrue(
            "Expected no annotation for backtick template literal (else -> return branch in annotate())",
            captured.isEmpty(),
        )
    }

    fun testConcatenationParentSuppressesWarning() {
        // "auth.login" inside a concatenation expression must produce NO warning (AC2).
        // BinaryExpressionWrapper has simpleName containing "Binary" — triggers the parent-node guard.
        myFixture.configureByFile("en.json")
        myFixture.configureByText("consumer.json", """{"ref": "<caret>auth.login"}""")
        val realElement = checkNotNull(myFixture.file.findElementAt(myFixture.caretOffset)) {
            "No element at caret offset"
        }
        val binaryParent = BinaryExpressionWrapper(realElement)
        val elementInBinaryExpr = object : PsiElement by realElement {
            override fun getParent(): PsiElement = binaryParent
        }
        val captured = mutableListOf<CapturedAnnotation>()
        JsonKeyAnnotator().annotate(elementInBinaryExpr, recordingHolder(captured))
        assertTrue(
            "Expected no warning for 'auth.login' when its parent is a concatenation (Binary) expression",
            captured.isEmpty(),
        )
    }
}
