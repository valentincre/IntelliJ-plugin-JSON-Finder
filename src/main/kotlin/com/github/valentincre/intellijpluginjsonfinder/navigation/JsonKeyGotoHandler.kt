package com.github.valentincre.intellijpluginjsonfinder.navigation

import com.github.valentincre.intellijpluginjsonfinder.service.JsonFinderProjectService
import com.github.valentincre.intellijpluginjsonfinder.settings.JsonFinderSettings
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

// Wraps a PsiElement to provide a custom ItemPresentation for the "Choose Declaration" popup,
// showing the containing filename instead of the fallback module/project name.
private class JsonKeyNavigationTarget(
    private val delegate: PsiElement,
) : PsiElement by delegate, NavigatablePsiElement {

    override fun getName(): String? = delegate.containingFile?.name

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = delegate.text
        override fun getLocationString(): String? = delegate.containingFile?.name
        override fun getIcon(unused: Boolean) = delegate.containingFile?.getIcon(0)
    }

    // findElementAt() returns a LeafPsiElement which does not implement NavigatablePsiElement,
    // so casting would silently fail. Navigate via OpenFileDescriptor instead.
    override fun navigate(requestFocus: Boolean) {
        val vFile = delegate.containingFile?.virtualFile ?: return
        OpenFileDescriptor(delegate.project, vFile, delegate.textOffset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = delegate.containingFile?.virtualFile != null
    override fun canNavigateToSource(): Boolean = delegate.containingFile?.virtualFile != null

    // Explicit overrides required because Kotlin's `by delegate` generates forwarding stubs for ALL
    // PsiElement methods, including deprecated ones. Without these, the compiler emits OVERRIDE_DEPRECATION.
    // When checkDelete/checkAdd are eventually removed from PsiElement, these will become compile errors — intentional.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun checkDelete() {
        delegate.checkDelete()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun checkAdd(element: PsiElement) {
        delegate.checkAdd(element)
    }
}

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
        if (!project.service<JsonFinderSettings>().state.isEnabled) return null
        // The IDE already holds a read lock when invoking GotoDeclarationHandler —
        // no explicit ReadAction wrapper is needed here.
        val definitions = project.service<JsonFinderProjectService>().findDefinitions(stripped)

        return when (definitions.size) {
            0 -> null
            1 -> {
                val def = definitions.first()
                try {
                    if (!def.virtualFile.isValid) return null
                    val psiFile = PsiManager.getInstance(project).findFile(def.virtualFile)
                        ?: return null
                    val element = psiFile.findElementAt(def.offset) ?: return null
                    arrayOf(element)
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
                    val resolved = definitions.mapNotNull { def ->
                        if (!def.virtualFile.isValid) return@mapNotNull null
                        val psiFile = PsiManager.getInstance(project).findFile(def.virtualFile)
                            ?: return@mapNotNull null
                        val element = psiFile.findElementAt(def.offset) ?: return@mapNotNull null
                        JsonKeyNavigationTarget(element)
                    }
                    if (resolved.isEmpty()) null else resolved.toTypedArray()
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
