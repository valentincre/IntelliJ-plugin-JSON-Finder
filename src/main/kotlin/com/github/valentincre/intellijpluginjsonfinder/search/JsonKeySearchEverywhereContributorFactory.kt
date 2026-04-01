package com.github.valentincre.intellijpluginjsonfinder.search

import com.github.valentincre.intellijpluginjsonfinder.service.ResolvedKeyDefinition
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.actionSystem.AnActionEvent

class JsonKeySearchEverywhereContributorFactory :
    SearchEverywhereContributorFactory<ResolvedKeyDefinition> {

    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<ResolvedKeyDefinition> =
        JsonKeySearchEverywhereContributor(initEvent.project!!)
}
