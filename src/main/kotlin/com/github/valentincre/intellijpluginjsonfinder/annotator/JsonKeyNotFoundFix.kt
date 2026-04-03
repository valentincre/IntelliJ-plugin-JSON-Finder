package com.github.valentincre.intellijpluginjsonfinder.annotator

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

// Quick-fix that replaces a broken JSON key string literal with a suggested key from the index.
// Extends LocalQuickFixAndIntentionActionOnPsiElement so that:
//   - It implements IntentionAction (required by AnnotationBuilder.withFix(IntentionAction))
//   - It implements LocalQuickFix (usable from inspection infrastructure)
//   - The element reference is kept as a SmartPsiElementPointer (safe across reparse)
//   - invoke() is called inside a write action automatically
class JsonKeyNotFoundFix(
    element: PsiElement,
    private val suggestion: String,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getText(): String = "Replace with '$suggestion'"

    override fun getFamilyName(): String = "Replace with suggested JSON key"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement,
    ) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        document.replaceString(
            startElement.textRange.startOffset,
            startElement.textRange.endOffset,
            "\"$suggestion\"",
        )
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
