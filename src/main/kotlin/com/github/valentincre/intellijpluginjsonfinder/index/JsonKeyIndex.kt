package com.github.valentincre.intellijpluginjsonfinder.index

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

/**
 * FileBasedIndexExtension that indexes all JSON keys as dotted paths across the project.
 *
 * Index key:   String — full dotted path, lowercase, no leading/trailing dots.
 * Index value: List<JsonKeyEntry> — one entry per file occurrence of that key path.
 *
 * Persistence is handled automatically by the IntelliJ platform; the index survives IDE restarts
 * without a full re-index (AC 2). VFS events trigger incremental updates automatically (AC 3).
 */
class JsonKeyIndex : FileBasedIndexExtension<String, List<JsonKeyEntry>>() {

    companion object {
        val KEY: ID<String, List<JsonKeyEntry>> = ID.create("JsonKeyIndex")
        private const val INDEX_VERSION = 1

        // Default exclude patterns — hard-coded for this story (Settings integration in Epic 6).
        private val DEFAULT_EXCLUDED_NAMES = setOf(
            "package.json",
            "project.json",
            "tsconfig.json",
            "jest.config.js",
            "jest.config.ts",
            "jest.config.mjs",
            "jest.config.cjs",
        )
    }

    override fun getName(): ID<String, List<JsonKeyEntry>> = KEY

    override fun getVersion(): Int = INDEX_VERSION

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, List<JsonKeyEntry>, FileContent> = JsonKeyIndexer()

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<JsonKeyEntry>> = JsonKeyEntryListExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
        file.extension == "json" && !isExcluded(file)
    }

    private fun isExcluded(file: VirtualFile): Boolean {
        val path = file.path
        return path.contains("/node_modules/") ||
            DEFAULT_EXCLUDED_NAMES.contains(file.name) ||
            file.name.startsWith("tsconfig")
    }
}

/**
 * Serializer/deserializer for List<JsonKeyEntry> values stored in the FileBasedIndex.
 * Uses IOUtil for UTF I/O to handle strings longer than 65535 chars.
 */
private object JsonKeyEntryListExternalizer : DataExternalizer<List<JsonKeyEntry>> {

    override fun save(out: DataOutput, value: List<JsonKeyEntry>) {
        out.writeInt(value.size)
        for (entry in value) {
            out.writeInt(entry.offset)
            IOUtil.writeUTF(out, entry.resolvedValue)
        }
    }

    override fun read(input: DataInput): List<JsonKeyEntry> {
        val size = input.readInt()
        if (size < 0) return emptyList()
        return List(size) {
            JsonKeyEntry(
                offset = input.readInt(),
                resolvedValue = IOUtil.readUTF(input),
            )
        }
    }
}
