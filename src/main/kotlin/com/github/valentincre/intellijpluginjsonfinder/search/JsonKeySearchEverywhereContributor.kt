package com.github.valentincre.intellijpluginjsonfinder.search

import com.github.valentincre.intellijpluginjsonfinder.index.JsonKeyIndex
import com.github.valentincre.intellijpluginjsonfinder.service.JsonFinderProjectService
import com.github.valentincre.intellijpluginjsonfinder.service.ResolvedKeyDefinition
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListCellRenderer

class JsonKeySearchEverywhereContributor(
    private val project: Project,
) : SearchEverywhereContributor<ResolvedKeyDefinition> {

    override fun getSearchProviderId(): String = "JsonKeySearchEverywhereContributor"
    override fun getGroupName(): String = "JSON Keys"
    override fun getSortWeight(): Int = 600
    override fun showInFindResults(): Boolean = false
    override fun isDumbAware(): Boolean = false
    override fun isMultiSelectionSupported(): Boolean = false
    override fun getDataForItem(element: ResolvedKeyDefinition, dataId: String): Any? = null

    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in ResolvedKeyDefinition>,
    ) {
        val query = pattern.lowercase().trim()
        if (query.isBlank()) return

        val results = try {
            ReadAction.compute<List<ResolvedKeyDefinition>, Throwable> {
                val matchingKeys = mutableListOf<String>()
                FileBasedIndex.getInstance().processAllKeys(JsonKeyIndex.KEY, { key ->
                    if (key.lowercase().contains(query)) matchingKeys.add(key)
                    true
                }, project)
                matchingKeys
                    .flatMap { key -> project.service<JsonFinderProjectService>().findDefinitions(key) }
                    .sortedBy { it.virtualFile.path }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: IndexNotReadyException) {
            thisLogger().warn("[JsonKeySearch] IndexNotReadyException for '$query'")
            return
        } catch (e: Exception) {
            thisLogger().warn("[JsonKeySearch] Exception for '$query'", e)
            return
        }

        for (def in results) {
            if (!consumer.process(def)) return
        }
    }

    override fun processSelectedItem(
        selected: ResolvedKeyDefinition,
        modifiers: Int,
        searchText: String,
    ): Boolean {
        ApplicationManager.getApplication().invokeLater {
            OpenFileDescriptor(project, selected.virtualFile, selected.offset).navigate(true)
        }
        return true
    }

    override fun getElementsRenderer(): ListCellRenderer<in ResolvedKeyDefinition> =
        object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is ResolvedKeyDefinition) {
                    val displayValue = value.resolvedValue.let {
                        if (it.length > 80) it.take(77) + "…" else it
                    }
                    text = "${value.virtualFile.name}  —  \"$displayValue\""
                }
                return this
            }
        }
}
