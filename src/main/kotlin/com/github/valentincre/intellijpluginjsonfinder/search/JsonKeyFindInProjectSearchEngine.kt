package com.github.valentincre.intellijpluginjsonfinder.search

import com.intellij.find.FindInProjectSearchEngine
import com.intellij.find.FindModel
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import com.github.valentincre.intellijpluginjsonfinder.index.JsonKeyIndex
import com.github.valentincre.intellijpluginjsonfinder.service.JsonFinderProjectService

/**
 * Index-backed FindInProjectSearchEngine that intercepts searches made with the "JSON Keys" scope.
 *
 * When the active scope is a JsonKeySearchScope, this engine queries the FileBasedIndex directly
 * for dotted key paths and returns the files containing matching keys. Without this engine,
 * the default text search would never find nested keys like "payments.checkout" because
 * the dotted string never appears literally in the JSON file.
 *
 * For all other scopes, returns null to fall through to the default text engine.
 */
class JsonKeyFindInProjectSearchEngine : FindInProjectSearchEngine {

    override fun createSearcher(
        findModel: FindModel,
        project: Project,
    ): FindInProjectSearchEngine.FindInProjectSearcher? {
        if (!findModel.isCustomScope) return null
        val scope = findModel.customScope as? JsonKeySearchScope ?: return null
        return JsonKeySearcher(findModel, project, scope)
    }
}

private class JsonKeySearcher(
    private val findModel: FindModel,
    private val project: Project,
    private val scope: JsonKeySearchScope,
) : FindInProjectSearchEngine.FindInProjectSearcher {

    override fun searchForOccurrences(): Collection<VirtualFile> {
        val query = findModel.stringToFind.lowercase().trim()
        if (query.isBlank()) return emptyList()

        return ReadAction.compute<Collection<VirtualFile>, Throwable> {
            try {
                val allKeys = FileBasedIndex.getInstance()
                    .getAllKeys(JsonKeyIndex.KEY, project)
                val matchingKeys = allKeys.filter { it.contains(query) }

                matchingKeys.flatMap { key ->
                    project.service<JsonFinderProjectService>()
                        .findDefinitions(key)
                        .mapNotNull { def -> def.virtualFile }
                }.distinctBy { it.path }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (_: IndexNotReadyException) {
                emptyList()
            } catch (e: Exception) {
                thisLogger().error("JsonKey search failed for query '$query'", e)
                throw e
            }
        }
    }

    override fun isCovered(file: VirtualFile): Boolean = scope.contains(file)

    override fun isReliable(): Boolean = true
}
