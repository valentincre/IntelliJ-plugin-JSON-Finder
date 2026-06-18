package com.github.valentincre.intellijpluginjsonfinder.settings

import com.github.valentincre.intellijpluginjsonfinder.index.KEY
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class JsonFinderConfigurable(private val project: Project) : Configurable {

    private var panel: JPanel? = null
    private var enabledCheckBox: JBCheckBox? = null
    private var includeArea: JBTextArea? = null
    private var excludeArea: JBTextArea? = null

    override fun getDisplayName(): String = "JSON Finder"

    override fun createComponent(): JComponent {
        val checkBox = JBCheckBox("Enable JSON Finder for this project")
        val include = JBTextArea(8, 40)
        val exclude = JBTextArea(8, 40)
        enabledCheckBox = checkBox
        includeArea = include
        excludeArea = exclude
        panel = FormBuilder.createFormBuilder()
            .addComponent(checkBox)
            .addLabeledComponent(JBLabel("Include patterns (one per line):"), JBScrollPane(include), true)
            .addLabeledComponent(JBLabel("Exclude patterns (one per line):"), JBScrollPane(exclude), true)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = project.service<JsonFinderSettings>().state
        return enabledCheckBox?.isSelected != settings.isEnabled ||
                parsePatterns(includeArea?.text) != settings.includePatterns ||
            parsePatterns(excludeArea?.text) != settings.excludePatterns
    }

    override fun apply() {
        val settings = project.service<JsonFinderSettings>()
        settings.loadState(
            JsonFinderSettings.State(
                isEnabled = enabledCheckBox?.isSelected ?: true,
                includePatterns = parsePatterns(includeArea?.text),
                excludePatterns = parsePatterns(excludeArea?.text),
            )
        )
        FileBasedIndex.getInstance().requestRebuild(KEY)
    }

    override fun reset() {
        val state = project.service<JsonFinderSettings>().state
        enabledCheckBox?.isSelected = state.isEnabled
        includeArea?.text = state.includePatterns.joinToString("\n")
        excludeArea?.text = state.excludePatterns.joinToString("\n")
    }

    override fun disposeUIResources() {
        panel = null
        includeArea = null
        excludeArea = null
    }

    private fun parsePatterns(text: String?): List<String> =
        text?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
