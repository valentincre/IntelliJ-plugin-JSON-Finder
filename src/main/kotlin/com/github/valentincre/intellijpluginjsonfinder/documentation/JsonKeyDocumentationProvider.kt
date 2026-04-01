package com.github.valentincre.intellijpluginjsonfinder.documentation

import com.github.valentincre.intellijpluginjsonfinder.service.JsonFinderProjectService
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class JsonKeyDocumentationProvider : AbstractDocumentationProvider() {

    private val logger = thisLogger()

    // Called first — return the element if it's a key path candidate with at least one definition.
    // Returning null skips generateDoc entirely.
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        contextElement ?: return null
        val rawText = contextElement.text ?: return null
        val stripped = stripQuotes(rawText) ?: return null
        if (!KeyPathUtil.isKeyPathCandidate(stripped)) return null
        return try {
            val definitions = contextElement.project
                .service<JsonFinderProjectService>()
                .findDefinitions(stripped)
            if (definitions.isEmpty()) null else contextElement
        } catch (_: ProcessCanceledException) {
            null  // Platform will retry; do not re-throw here (called from EDT in some paths)
        } catch (_: IndexNotReadyException) {
            null
        } catch (e: Exception) {
            logger.warn("Failed to resolve documentation element for key", e)
            null
        }
    }

    // Called when getCustomDocumentationElement returns non-null.
    // element = contextElement (the quoted string PSI leaf).
    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val stripped = stripQuotes(element.text ?: return null) ?: return null
        if (!KeyPathUtil.isKeyPathCandidate(stripped)) return null

        val definitions = try {
            element.project.service<JsonFinderProjectService>().findDefinitions(stripped)
        } catch (e: ProcessCanceledException) {
            throw e  // Must re-throw in generateDoc — called from background read action
        } catch (_: IndexNotReadyException) {
            return null
        } catch (e: Exception) {
            logger.warn("Failed to generate documentation for '$stripped'", e)
            return null
        }

        if (definitions.isEmpty()) return null

        return buildString {
            append("<html><body>")
            if (definitions.size == 1) {
                append(formatValue(definitions.first().resolvedValue))
            } else {
                definitions.forEach { def ->
                    val escapedName = StringUtil.escapeXmlEntities(def.virtualFile.name)
                    append("<b>$escapedName:</b> ${formatValue(def.resolvedValue)}<br>")
                }
            }
            append("</body></html>")
        }
    }

    private fun stripQuotes(rawText: String): String? {
        if (rawText.length < 2) return null
        return when {
            rawText.startsWith('"') && rawText.endsWith('"') -> rawText.substring(1, rawText.length - 1)
            rawText.startsWith('\'') && rawText.endsWith('\'') -> rawText.substring(1, rawText.length - 1)
            else -> null
        }
    }

    private fun formatValue(value: String): String {
        val truncated = if (value.length > 100) value.take(100) + "…" else value
        return "`${StringUtil.escapeXmlEntities(truncated)}`"
    }
}
