package com.github.valentincre.intellijpluginjsonfinder.index

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonNullLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.JsonValue
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileContent

/**
 * DataIndexer that traverses the PSI tree of a JSON file and emits
 * dotted-path key → List<JsonKeyEntry> mappings for the FileBasedIndex.
 */
class JsonKeyIndexer : DataIndexer<String, List<JsonKeyEntry>, FileContent> {

    override fun map(inputData: FileContent): Map<String, List<JsonKeyEntry>> {
        val result = mutableMapOf<String, MutableList<JsonKeyEntry>>()
        try {
            val psiFile = inputData.psiFile
            if (psiFile !is JsonFile) return emptyMap()
            val root = psiFile.topLevelValue as? JsonObject ?: return emptyMap()
            traverseObject(root, "", result)
        } catch (e: ProcessCanceledException) {
            throw e  // MUST re-throw — never swallow cancellation
        } catch (e: Exception) {
            thisLogger().warn("JsonKeyIndexer: failed to index ${inputData.file.path}", e)
        }
        return result
    }

    private fun traverseObject(
        obj: JsonObject,
        prefix: String,
        result: MutableMap<String, MutableList<JsonKeyEntry>>,
    ) {
        for (property in obj.propertyList) {
            val name = property.name.lowercase().trim()
            if (name.isEmpty()) continue
            val path = if (prefix.isEmpty()) name else "$prefix.$name"
            val value = property.value ?: continue
            when (value) {
                is JsonObject -> traverseObject(value, path, result)
                is JsonArray  -> indexArray(value, path, result)
                else          -> {
                    val entry = JsonKeyEntry(
                        offset = value.textOffset,
                        resolvedValue = extractLeafValue(value),
                    )
                    result.getOrPut(path) { mutableListOf() }.add(entry)
                }
            }
        }
    }

    private fun indexArray(
        array: JsonArray,
        path: String,
        result: MutableMap<String, MutableList<JsonKeyEntry>>,
    ) {
        // v1: store the array JSON representation as a leaf value (no recursive indexing into arrays)
        val entry = JsonKeyEntry(offset = array.textOffset, resolvedValue = array.text.take(200))
        result.getOrPut(path) { mutableListOf() }.add(entry)
    }

    private fun extractLeafValue(element: JsonValue): String = when (element) {
        is JsonStringLiteral -> element.value  // unescaped string content
        is JsonNullLiteral   -> ""
        else                 -> element.text   // number, boolean as raw JSON
    }
}
