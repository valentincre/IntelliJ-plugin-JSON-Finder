package com.github.valentincre.intellijpluginjsonfinder.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "JsonFinderSettings", storages = [Storage("jsonFinderSettings.xml")])
@Service(Service.Level.PROJECT)
class JsonFinderSettings : PersistentStateComponent<JsonFinderSettings.State> {

    companion object {
        val DEFAULT_INCLUDE_PATTERNS = listOf("**/*.{js,ts,jsx,tsx,cjs,cts,mjs,mts,html,vue,json}")
        val DEFAULT_EXCLUDE_PATTERNS = listOf(
            "**/package.json",
            "**/project.json",
            "**/tsconfig*.json",
            "**/jest.config.*",
            "**/eslint*",
            "**/prettier*",
            "**/node_modules/**",
        )
    }

    data class State(
        var includePatterns: List<String> = DEFAULT_INCLUDE_PATTERNS,
        var excludePatterns: List<String> = DEFAULT_EXCLUDE_PATTERNS,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
