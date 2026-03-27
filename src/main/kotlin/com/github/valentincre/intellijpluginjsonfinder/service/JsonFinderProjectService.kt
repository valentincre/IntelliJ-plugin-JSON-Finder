package com.github.valentincre.intellijpluginjsonfinder.service

interface JsonFinderProjectService {
    fun findDefinitions(keyPath: String): List<ResolvedKeyDefinition>
    fun suggestSimilar(keyPath: String, maxResults: Int = 5): List<String>
    fun isValidKeyPath(text: String): Boolean
}
