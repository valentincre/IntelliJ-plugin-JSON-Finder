package com.github.valentincre.intellijpluginjsonfinder.annotator

import com.github.valentincre.intellijpluginjsonfinder.service.JsonFinderProjectService
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement

// Highlights dotted-path string literals that have no matching JSON key in the index.
// Runs inside IntelliJ's background highlighting pass — already in a ReadAction; do not wrap again.
// Dynamic strings (backtick templates, concatenation) are filtered out at the quote-strip step.
class JsonKeyAnnotator : Annotator {

    private val logger = thisLogger()

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val rawText = element.text ?: return
        val stripped = when {
            rawText.length >= 2 && rawText.startsWith('"') && rawText.endsWith('"') ->
                rawText.substring(1, rawText.length - 1)
            rawText.length >= 2 && rawText.startsWith('\'') && rawText.endsWith('\'') ->
                rawText.substring(1, rawText.length - 1)
            else -> return
        }
        // Suppress if this literal is a fragment inside a concatenation expression.
        // Uses class-name matching to avoid importing TypeScript/JS/Kotlin PSI types
        // (those plugins may not be present in all supported IDEs).
        val parentClassName = element.parent?.javaClass?.simpleName ?: ""
        if (parentClassName.contains("Binary", ignoreCase = true) ||
            parentClassName.contains("Plus", ignoreCase = true) ||
            parentClassName.contains("Concatenat", ignoreCase = true)) {
            return
        }

        if (!KeyPathUtil.isKeyPathCandidate(stripped)) return

        val project = element.project
        // Skip during dumb mode (initial indexing) — index is not ready yet.
        if (DumbService.isDumb(project)) return
        try {
            val definitions = project.service<JsonFinderProjectService>().findDefinitions(stripped)
            if (definitions.isEmpty()) {
                holder.newAnnotation(HighlightSeverity.WARNING, "Unresolved JSON key: '$stripped'")
                    .range(element.textRange)
                    .create()
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("JsonKeyAnnotator failed for element '${element.text}'", e)
        }
    }
}
