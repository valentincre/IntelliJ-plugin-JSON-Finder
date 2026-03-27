package com.github.valentincre.intellijpluginjsonfinder.navigation

import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

// GotoDeclarationHandler that identifies dotted-path string literals as candidate JSON key
// references. Navigation targets are deferred to Stories 2.2 (single match) and 2.3 (multi-match).
// No service call or index access in this story — detection only.
class JsonKeyGotoHandler : GotoDeclarationHandler {

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
        // Navigation targets implemented in Stories 2.2 (single match) and 2.3 (multi-match)
        return null
    }
}
