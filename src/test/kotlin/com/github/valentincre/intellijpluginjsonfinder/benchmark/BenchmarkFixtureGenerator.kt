package com.github.valentincre.intellijpluginjsonfinder.benchmark

/**
 * Programmatic generator for large-scale benchmark fixtures.
 *
 * Generates 500 JSON files × 20 keys each = 10,000 total dotted-path keys,
 * satisfying the 10k+ key scale required for cold-start benchmark (AC1, AC2).
 *
 * Keys follow the pattern `section_N.key_M` which is valid for `isValidKeyPath`.
 * Files are written to a caller-supplied temp directory and auto-cleaned after the test.
 */
object BenchmarkFixtureGenerator {

    const val FILE_COUNT = 500
    const val KEYS_PER_FILE = 20

    /**
     * Writes [fileCount] JSON files to [targetDir].
     * Each file contains one top-level section object with [keysPerFile] string values.
     *
     * Resulting key paths: `section_0.key_0` … `section_499.key_19` (10,000 keys total).
     */
    fun generateFixtures(
        targetDir: java.io.File,
        fileCount: Int = FILE_COUNT,
        keysPerFile: Int = KEYS_PER_FILE,
    ) {
        targetDir.mkdirs()
        repeat(fileCount) { fileIdx ->
            val entries = (0 until keysPerFile).joinToString(",\n") { keyIdx ->
                "    \"key_$keyIdx\": \"value_${fileIdx}_$keyIdx\""
            }
            val content = "{\n  \"section_$fileIdx\": {\n$entries\n  }\n}\n"
            java.io.File(targetDir, "fixture_$fileIdx.json").writeText(content)
        }
    }
}
