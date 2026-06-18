# JSON Finder

![Build](https://github.com/valentincre/IntelliJ-plugin-JSON-Finder/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.github.valentincre.intellijpluginjsonfinder.svg)](https://plugins.jetbrains.com/plugin/32334-json-finder)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.github.valentincre.intellijpluginjsonfinder.svg)](https://plugins.jetbrains.com/plugin/32334-json-finder)

<!-- Plugin description -->
JSON Finder brings first-class dotted-path JSON key navigation to IntelliJ-based IDEs.

Working with i18n files, feature flags, or JSON config? This plugin indexes all your JSON keys in the background and lets you navigate to their definitions with a single Cmd+Click (Ctrl+Click on Windows/Linux), preview their resolved value on hover, and search them project-wide with a dedicated scope — all with zero configuration.

**Features:**

- **Cmd+Click navigation** — jump from any dotted-path string literal directly to the matching JSON key definition
- **Hover preview** — see the resolved JSON value inline without leaving your current file
- **Broken key warnings** — inline annotations highlight unresolved keys with fuzzy "did you mean?" suggestions
- **Search Everywhere integration** — find JSON keys by name across the entire project from the Search Everywhere dialog
- **"JSON Keys" search scope** — filter Find in Files results to JSON key definitions only
- **Background indexing** — keys are indexed incrementally as files change; no manual refresh needed
- **Configurable patterns** — include/exclude glob patterns let you focus the index on the right files for your project structure

Works in IntelliJ IDEA, WebStorm, and any other JetBrains IDE that supports JSON files (2025.1+).
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "JSON Finder"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32334-json-finder) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/32334-json-finder/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/valentincre/IntelliJ-plugin-JSON-Finder/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
