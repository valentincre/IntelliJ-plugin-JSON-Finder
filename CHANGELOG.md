<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# JSON Finder Changelog

## [Unreleased]

## [1.0.0] - 2026-06-18

### Dependency & tooling updates

- Kotlin 2.4.0
- IntelliJ Platform Gradle Plugin 2.16.0
- Qodana 2026.1.3
- Gradle wrapper 9.5.1

## [0.0.1] - 2026-06-17

### Added

- Background indexing of dotted-path JSON keys across the project using IntelliJ's `FileBasedIndex`; index updates incrementally on file change
- Cmd+Click (Ctrl+Click) navigation from any string literal containing a dotted path to the matching JSON key definition; disambiguation popup when multiple matches exist
- Hover preview showing the resolved JSON value for the key under the cursor
- Inline warnings on unresolved dotted-path keys with fuzzy "did you mean?" quick-fix suggestions (Levenshtein + segment-aware matching)
- "JSON Keys" custom search scope for filtering Find in Files results to JSON key definitions
- Search Everywhere integration — search JSON keys by name from the global search dialog
- Per-project settings UI to configure include/exclude glob patterns, restricting the index to the relevant JSON files in non-standard project structures
- Compatible with IntelliJ IDEA, WebStorm, and all JetBrains IDEs 2025.1+

[Unreleased]: https://github.com/valentincre/IntelliJ-plugin-JSON-Finder/compare/1.0.0...HEAD
[1.0.0]: https://github.com/valentincre/IntelliJ-plugin-JSON-Finder/compare/0.0.1...1.0.0
[0.0.1]: https://github.com/valentincre/IntelliJ-plugin-JSON-Finder/commits/0.0.1
