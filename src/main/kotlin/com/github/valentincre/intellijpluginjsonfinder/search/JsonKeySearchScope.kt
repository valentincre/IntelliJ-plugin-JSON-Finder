package com.github.valentincre.intellijpluginjsonfinder.search

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

/**
 * Custom GlobalSearchScope that covers only JSON files eligible for the JsonKeyIndex.
 * Displayed as "JSON Keys" in the scope selector of Cmd+Shift+F.
 *
 * The exclusion logic mirrors JsonKeyIndex.getInputFilter() — kept in sync manually
 * to avoid a circular dependency on the index class from this scope.
 */
class JsonKeySearchScope(project: Project) : GlobalSearchScope(project) {

    override fun getDisplayName(): String = "JSON Keys"

    override fun contains(file: VirtualFile): Boolean =
        file.extension == "json" && !isExcluded(file)

    override fun isSearchInModuleContent(aModule: Module): Boolean = true

    override fun isSearchInLibraries(): Boolean = false

    private fun isExcluded(file: VirtualFile): Boolean {
        val path = file.path
        return path.contains("/node_modules/") ||
            EXCLUDED_NAMES.contains(file.name) ||
            file.name.startsWith("tsconfig")
    }

    companion object {
        private val EXCLUDED_NAMES = setOf(
            "package.json",
            "project.json",
        )
    }
}
