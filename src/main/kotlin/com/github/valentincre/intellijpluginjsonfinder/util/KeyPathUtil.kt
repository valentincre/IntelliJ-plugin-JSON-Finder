package com.github.valentincre.intellijpluginjsonfinder.util

// Pure Kotlin detection and normalization utility for dotted-path JSON key references.
// Zero IntelliJ imports — enables unit testing without the platform.
object KeyPathUtil {

    // Bare path validation: each segment must start with a letter or underscore, then word chars.
    // Matches:  "auth.login.button", "a.b", "UPPER.CASE", "_key.name"
    // Rejects:  "plain", "", "has space", ".leading", "trailing.", "not-a.path", "1.2", "123.456"
    private val DOTTED_PATH_REGEX = Regex("""^[a-zA-Z_]\w*(\.[a-zA-Z_]\w*)+$""")

    // Returns true if [text] (bare, without surrounding quotes) is a valid dotted-path key.
    fun isKeyPathCandidate(text: String): Boolean = DOTTED_PATH_REGEX.matches(text.trim())

    // Trims and lowercases a key path for normalized storage/lookup.
    fun normalizeKeyPath(text: String): String = text.trim().lowercase()
}
