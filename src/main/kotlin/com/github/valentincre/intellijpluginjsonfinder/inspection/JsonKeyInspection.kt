package com.github.valentincre.intellijpluginjsonfinder.inspection

import com.github.valentincre.intellijpluginjsonfinder.service.JsonFinderProjectService
import com.github.valentincre.intellijpluginjsonfinder.settings.JsonFinderSettings
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

// Reports dotted-path string literals that have no matching JSON key in the index.
// Default severity is WEAK_WARNING (configured in plugin.xml) — visible as a subtle underline
// in the editor but absent from the right-side scroll bar. Users can raise it to WARNING or ERROR
// via Settings → Editor → Inspections → JSON Finder.
class JsonKeyInspection : LocalInspectionTool() {

    private val logger = thisLogger()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val rawText = element.text ?: return
                val stripped = when {
                    rawText.length >= 2 && rawText.startsWith('"') && rawText.endsWith('"') ->
                        rawText.substring(1, rawText.length - 1)

                    rawText.length >= 2 && rawText.startsWith('\'') && rawText.endsWith('\'') ->
                        rawText.substring(1, rawText.length - 1)

                    else -> return
                }

                // Suppress literals inside concatenation / binary-plus / template expressions.
                val parentClassName = element.parent?.javaClass?.simpleName ?: ""
                if (parentClassName.contains("Binary", ignoreCase = true) ||
                    parentClassName.contains("Plus", ignoreCase = true) ||
                    parentClassName.contains("Concatenat", ignoreCase = true)
                ) return

                if (!KeyPathUtil.isKeyPathCandidate(stripped)) return

                // Skip leaf tokens whose parent composite node wraps the exact same text.
                val parentRaw = element.parent?.text ?: ""
                val parentStripped = when {
                    parentRaw.length >= 2 && parentRaw.startsWith('"') && parentRaw.endsWith('"') ->
                        parentRaw.substring(1, parentRaw.length - 1)

                    parentRaw.length >= 2 && parentRaw.startsWith('\'') && parentRaw.endsWith('\'') ->
                        parentRaw.substring(1, parentRaw.length - 1)

                    else -> null
                }
                if (parentStripped == stripped) return

                val project = element.project
                if (!project.service<JsonFinderSettings>().state.isEnabled) return
                if (DumbService.isDumb(project)) return

                try {
                    val service = project.service<JsonFinderProjectService>()
                    val definitions = service.findDefinitions(stripped)
                    if (definitions.isEmpty()) {
                        val fixes = service.suggestSimilar(stripped, maxResults = 5)
                            .map { JsonKeyNotFoundFix(element, it) }
                            .toTypedArray()
                        holder.registerProblem(
                            element,
                            "Unresolved JSON key: '$stripped'",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            *fixes,
                        )
                    }
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("JsonKeyInspection failed for element '${element.text}'", e)
                }
            }
        }
}
