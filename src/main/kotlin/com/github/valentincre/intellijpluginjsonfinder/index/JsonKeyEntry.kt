package com.github.valentincre.intellijpluginjsonfinder.index

/**
 * Represents a single indexed occurrence of a dotted-path JSON key.
 *
 * @param offset       Character offset in the VirtualFile where the key's value starts.
 * @param resolvedValue Leaf JSON value as a string; objects/arrays use their JSON repr; null → "".
 */
data class JsonKeyEntry(
    val offset: Int,
    val resolvedValue: String,
)
