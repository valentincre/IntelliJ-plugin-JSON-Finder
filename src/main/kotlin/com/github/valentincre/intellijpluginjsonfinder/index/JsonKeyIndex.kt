package com.github.valentincre.intellijpluginjsonfinder.index

import com.github.valentincre.intellijpluginjsonfinder.settings.JsonFinderSettings
import com.intellij.json.JsonFileType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
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
 *
 * Include/exclude patterns are read from JsonFinderSettings at filter time so that changes
 * in Settings take effect after the next requestRebuild() call without an IDE restart (AC 1, 4, 5).
 */
class JsonKeyIndex : FileBasedIndexExtension<String, List<JsonKeyEntry>>() {

    companion object {
        val KEY: ID<String, List<JsonKeyEntry>> = ID.create("JsonKeyIndex")
        private const val INDEX_VERSION = 2  // bumped from 1: now reads settings glob patterns
    }

    override fun getName(): ID<String, List<JsonKeyEntry>> = KEY

    override fun getVersion(): Int = INDEX_VERSION

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, List<JsonKeyEntry>, FileContent> = JsonKeyIndexer()

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<JsonKeyEntry>> = JsonKeyEntryListExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
        if (file.fileType != JsonFileType.INSTANCE) return@InputFilter false
        val openProjects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        // Prefer the project whose content root contains this file; fall back to the first open project.
        val project = openProjects.firstOrNull { ProjectFileIndex.getInstance(it).isInContent(file) }
            ?: openProjects.firstOrNull()
            ?: return@InputFilter false
        val settings = project.service<JsonFinderSettings>().state
        val basePath = project.basePath
        matchesInclude(file, settings.includePatterns, basePath) && !matchesExclude(file, settings.excludePatterns, basePath)
    }

    private fun matchesInclude(file: VirtualFile, patterns: List<String>, basePath: String?): Boolean {
        if (patterns.isEmpty()) return false
        return matchesAny(file, patterns, basePath)
    }

    private fun matchesExclude(file: VirtualFile, patterns: List<String>, basePath: String?): Boolean {
        if (patterns.isEmpty()) return false
        return matchesAny(file, patterns, basePath)
    }

    // Returns true if the file matches any of the given glob patterns.
    // Patterns are matched against both the relative path from the project root (for patterns like
    // "src/i18n/**/*.json") and the absolute path (for patterns like "**/*.json").
    private fun matchesAny(file: VirtualFile, patterns: List<String>, basePath: String?): Boolean {
        val absolutePath = java.nio.file.Paths.get(file.path)
        val relativePath = basePath?.let {
            runCatching { java.nio.file.Paths.get(it).relativize(absolutePath) }.getOrNull()
        }
        return patterns.any { pattern ->
            runCatching {
                val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
                (relativePath != null && matcher.matches(relativePath)) || matcher.matches(absolutePath)
            }.getOrDefault(false)
        }
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
