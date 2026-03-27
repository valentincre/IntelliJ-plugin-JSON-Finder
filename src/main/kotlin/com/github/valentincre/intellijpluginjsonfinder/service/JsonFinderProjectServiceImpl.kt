package com.github.valentincre.intellijpluginjsonfinder.service

import com.github.valentincre.intellijpluginjsonfinder.index.JsonKeyIndex
import com.github.valentincre.intellijpluginjsonfinder.util.FuzzyMatchUtil
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.indexing.FileBasedIndex

@Service(Service.Level.PROJECT)
class JsonFinderProjectServiceImpl(private val project: Project) : JsonFinderProjectService {

    override fun findDefinitions(keyPath: String): List<ResolvedKeyDefinition> {
        val normalizedPath = keyPath.lowercase().trim()
        if (normalizedPath.isEmpty()) return emptyList()
        val results = mutableListOf<ResolvedKeyDefinition>()
        ReadAction.compute<Unit, Throwable> {
            FileBasedIndex.getInstance().processValues(
                JsonKeyIndex.KEY,
                normalizedPath,
                null,
                { file, entries ->
                    entries.forEach { entry ->
                        results.add(ResolvedKeyDefinition(file, entry.offset, entry.resolvedValue))
                    }
                    true
                },
                GlobalSearchScope.allScope(project),
            )
        }
        return results
    }

    override fun suggestSimilar(keyPath: String, maxResults: Int): List<String> {
        require(maxResults > 0) { "maxResults must be positive, was $maxResults" }
        val normalizedQuery = keyPath.lowercase().trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val allKeys = ReadAction.compute<List<String>, Throwable> {
            val keys = mutableListOf<String>()
            FileBasedIndex.getInstance().processAllKeys(JsonKeyIndex.KEY, { key ->
                keys.add(key)
                true
            }, project)
            keys
        }

        // Tier 1: IntelliJ NameUtil fuzzy matching via MinusculeMatcher
        val matcher = NameUtil.buildMatcher(normalizedQuery, NameUtil.MatchingCaseSensitivity.NONE)
        val tier1 = allKeys.filter { key -> matcher.matches(key) }.take(maxResults)

        if (tier1.isNotEmpty()) return tier1

        // Tier 2: Segment-aware Levenshtein fallback
        return allKeys
            .map { key -> key to FuzzyMatchUtil.segmentLevenshtein(normalizedQuery, key) }
            .filter { (_, score) -> score < FuzzyMatchUtil.SEGMENT_MISMATCH_PENALTY }
            .sortedBy { (_, score) -> score }
            .take(maxResults)
            .map { (key, _) -> key }
    }

    override fun isValidKeyPath(text: String): Boolean = KeyPathUtil.isKeyPathCandidate(text)
}
