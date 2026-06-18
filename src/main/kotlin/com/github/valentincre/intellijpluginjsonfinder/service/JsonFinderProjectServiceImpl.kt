package com.github.valentincre.intellijpluginjsonfinder.service

import com.github.valentincre.intellijpluginjsonfinder.index.KEY
import com.github.valentincre.intellijpluginjsonfinder.util.FuzzyMatchUtil
import com.github.valentincre.intellijpluginjsonfinder.util.KeyPathUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.indexing.FileBasedIndex

@Service(Service.Level.PROJECT)
class JsonFinderProjectServiceImpl(private val project: Project) : JsonFinderProjectService {

    // Acquires read access safely from any calling context:
    // - If already in a read action (e.g. called from GotoDeclarationHandler), runs block directly.
    // - Otherwise uses NonBlockingReadAction, which yields to write actions and is cancellation-aware.
    private fun <T> withReadAccess(block: () -> T): T =
        if (ApplicationManager.getApplication().isReadAccessAllowed) block()
        else ReadAction.nonBlocking<T> { block() }.executeSynchronously()

    override fun findDefinitions(keyPath: String): List<ResolvedKeyDefinition> {
        val normalizedPath = keyPath.lowercase().trim()
        if (normalizedPath.isEmpty()) return emptyList()
        val results = mutableListOf<ResolvedKeyDefinition>()
        withReadAccess {
            FileBasedIndex.getInstance().processValues(
                KEY,
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
        val allKeys = withReadAccess {
            val scope = GlobalSearchScope.allScope(project)
            val fbi = FileBasedIndex.getInstance()
            val keys = mutableListOf<String>()
            fbi.processAllKeys(KEY, { key ->
                // Only include keys that have a backing file within the project scope.
                // processAllKeys(project) can return keys from IntelliJ's global shared index or
                // other open projects — verifying via getContainingFiles guarantees we only
                // suggest keys that actually exist in this project.
                if (fbi.getContainingFiles(KEY, key, scope).isNotEmpty()) {
                    keys.add(key)
                }
                true
            }, project)
            keys
        }

        // Tier 1: IntelliJ NameUtil fuzzy matching via MinusculeMatcher (builder pattern).
        val matcher = NameUtil.buildMatcher(normalizedQuery).build()
        val tier1 = allKeys.filter { key -> matcher.matches(key) }.take(maxResults)

        if (tier1.isNotEmpty()) return tier1

        // Tier 2: Segment-aware Levenshtein fallback
        return allKeys.asSequence()
            .map { key -> key to FuzzyMatchUtil.segmentLevenshtein(normalizedQuery, key) }
            .filter { (_, score) -> score < FuzzyMatchUtil.SEGMENT_MISMATCH_PENALTY }
            .sortedBy { (_, score) -> score }
            .take(maxResults)
            .map { (key, _) -> key }
            .toList()
    }

    override fun isValidKeyPath(text: String): Boolean = KeyPathUtil.isKeyPathCandidate(text)
}
