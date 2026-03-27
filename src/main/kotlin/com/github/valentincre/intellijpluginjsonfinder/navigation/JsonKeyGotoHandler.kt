package com.github.valentincre.intellijpluginjsonfinder.navigation

import com.github.valentincre.intellijpluginjsonfinder.service.JsonFinderProjectService
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

class JsonKeyGotoHandler : GotoDeclarationHandler {

    private val logger = thisLogger()

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor,
    ): Array<PsiElement>? {
        sourceElement ?: return null
        val rawText = sourceElement.text ?: return null
        val stripped = when {
            rawText.length >= 2 && rawText.startsWith('"') && rawText.endsWith('"') ->
                rawText.substring(1, rawText.length - 1)
            rawText.length >= 2 && rawText.startsWith('\'') && rawText.endsWith('\'') ->
                rawText.substring(1, rawText.length - 1)
            else -> return null
        }
        if (!KeyPathUtil.isKeyPathCandidate(stripped)) return null

        val project = sourceElement.project
        val definitions = project.service<JsonFinderProjectService>().findDefinitions(stripped)

        return when (definitions.size) {
            0 -> null
            1 -> {
                val def = definitions.first()
                try {
                    ReadAction.compute<Array<PsiElement>?, Throwable> {
                        if (!def.virtualFile.isValid) return@compute null
                        val psiFile = PsiManager.getInstance(project).findFile(def.virtualFile)
                            ?: return@compute null
                        val element = psiFile.findElementAt(def.offset) ?: return@compute null
                        arrayOf(element)
                    }
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (_: IndexNotReadyException) {
                    null
                } catch (e: Exception) {
                    logger.warn("Failed to resolve navigation target for '$stripped'", e)
                    null
                }
            }
            else -> {
                try {
                    val targets = ReadAction.compute<Array<PsiElement>?, Throwable> {
                        val resolved = definitions.mapNotNull { def ->
                            if (!def.virtualFile.isValid) return@mapNotNull null
                            val psiFile = PsiManager.getInstance(project).findFile(def.virtualFile)
                                ?: return@mapNotNull null
                            psiFile.findElementAt(def.offset)
                        }
                        if (resolved.isEmpty()) null else resolved.toTypedArray()
                    }
                    targets
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (_: IndexNotReadyException) {
                    null
                } catch (e: Exception) {
                    logger.warn("Failed to resolve multi-match navigation targets for '$stripped'", e)
                    null
                }
            }
        }
    }
}
