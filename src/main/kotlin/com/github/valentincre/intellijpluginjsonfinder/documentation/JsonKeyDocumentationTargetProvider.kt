package com.github.valentincre.intellijpluginjsonfinder.documentation

import com.github.valentincre.intellijpluginjsonfinder.service.JsonFinderProjectService
import com.github.valentincre.intellijpluginjsonfinder.service.ResolvedKeyDefinition
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

// New-API documentation provider for IntelliJ 252+.
// AbstractDocumentationProvider.getCustomDocumentationElement is no longer reliably called
// in the JSON documentation flow; DocumentationTargetProvider is the correct hook.
class JsonKeyDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val element = file.findElementAt(offset) ?: return emptyList()
        val stripped = extractKeyPath(element) ?: return emptyList()
        val definitions = try {
            element.project.service<JsonFinderProjectService>().findDefinitions(stripped)
        } catch (_: ProcessCanceledException) {
            return emptyList()
        } catch (_: IndexNotReadyException) {
            return emptyList()
        } catch (e: Exception) {
            thisLogger().warn("[JsonKeyDoc] Unexpected error resolving documentation for '$stripped'", e)
            return emptyList()
        }
        if (definitions.isEmpty()) return emptyList()
        return listOf(JsonKeyDocumentationTarget(stripped, definitions))
    }

    private fun extractKeyPath(element: PsiElement): String? {
        val text = element.text ?: return null
        return stripQuotes(text)
            ?: element.parent?.text?.let { stripQuotes(it) }
    }

    private fun stripQuotes(raw: String): String? {
        if (raw.length < 2) return null
        val inner = when {
            raw.startsWith('"') && raw.endsWith('"') -> raw.substring(1, raw.length - 1)
            raw.startsWith('\'') && raw.endsWith('\'') -> raw.substring(1, raw.length - 1)
            else -> return null
        }
        return if (KeyPathUtil.isKeyPathCandidate(inner)) inner else null
    }
}

class JsonKeyDocumentationTarget(
    private val keyPath: String,
    private val definitions: List<ResolvedKeyDefinition>,
) : DocumentationTarget {

    override fun createPointer(): com.intellij.model.Pointer<out DocumentationTarget> =
        com.intellij.model.Pointer.hardPointer(this)

    override fun computeDocumentation(): DocumentationResult =
        DocumentationResult.documentation(buildHtml())

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(keyPath).presentation()

    private fun buildHtml(): String = buildString {
        append("<html><body>")
        if (definitions.size == 1) {
            append(formatValue(definitions.first().resolvedValue))
        } else {
            definitions.forEach { def ->
                val name = StringUtil.escapeXmlEntities(def.virtualFile.name)
                append("<b>$name:</b> ${formatValue(def.resolvedValue)}<br>")
            }
        }
        append("</body></html>")
    }

    private fun formatValue(value: String): String {
        val truncated = if (value.length > 100) value.take(100) + "…" else value
        return "<code>${StringUtil.escapeXmlEntities(truncated)}</code>"
    }
}
