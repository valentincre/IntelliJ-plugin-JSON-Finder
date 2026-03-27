package com.github.valentincre.intellijpluginjsonfinder.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider

/**
 * Registers the "JSON Keys" scope so it appears in the Cmd+Shift+F scope selector.
 * Declared via the searchScopesProvider extension point in plugin.xml.
 */
class JsonKeySearchScopeProvider : SearchScopeProvider {

    override fun getGeneralSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> =
        listOf(JsonKeySearchScope(project))
}
