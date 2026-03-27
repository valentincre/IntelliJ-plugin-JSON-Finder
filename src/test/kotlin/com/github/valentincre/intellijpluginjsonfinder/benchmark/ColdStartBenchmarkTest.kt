package com.github.valentincre.intellijpluginjsonfinder.benchmark

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil

// Cold-start indexing benchmark (AC1, AC2 — Story 1.4).
//
// Uses HeavyPlatformTestCase to get a real project with a live VFS and the background
// indexing scheduler. Fixtures are generated programmatically into a temp directory,
// registered in the IntelliJ VFS, and added as a content root to trigger real indexing.
// The temp directory is cleaned up automatically after the test.
//
// Run manually: ./gradlew benchmarkTest --tests "*.ColdStartBenchmarkTest"
// Excluded from normal CI via exclude("**/benchmark/**") in build.gradle.kts.
class ColdStartBenchmarkTest : HeavyPlatformTestCase() {

    companion object {
        // Initial generous threshold; tighten after first measured run.
        private const val COLD_START_THRESHOLD_MS = 30_000L
    }

    fun testColdStartIndexingCompletesWithinThreshold() {
        // java.nio.file.Files.createTempDirectory is atomic — no collision between parallel runs
        val tempDir = java.nio.file.Files.createTempDirectory("benchmark-cold-start").toFile()
        try {
            BenchmarkFixtureGenerator.generateFixtures(tempDir)
            println(
                "Benchmark fixture: ${BenchmarkFixtureGenerator.FILE_COUNT} files x " +
                    "${BenchmarkFixtureGenerator.KEYS_PER_FILE} keys = " +
                    "${BenchmarkFixtureGenerator.FILE_COUNT * BenchmarkFixtureGenerator.KEYS_PER_FILE} keys total"
            )

            // Register the generated files in the IntelliJ VFS and add as a project content
            // root so the indexing subsystem actually processes them.
            val vfsDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir)
                ?: error("VFS registration failed for $tempDir")
            PsiTestUtil.addContentRoot(myModule, vfsDir)

            // Measure from content-root registration (which triggers background indexing)
            // to when all indexes are ready.
            val startNs = System.nanoTime()
            IndexingTestUtil.waitUntilIndexesAreReady(project)
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

            println("Cold-start indexing of ${BenchmarkFixtureGenerator.FILE_COUNT} files: ${elapsedMs}ms")
            println("Threshold: ${COLD_START_THRESHOLD_MS}ms")

            assertTrue(
                "Cold-start must complete within ${COLD_START_THRESHOLD_MS}ms, measured: ${elapsedMs}ms",
                elapsedMs <= COLD_START_THRESHOLD_MS,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
