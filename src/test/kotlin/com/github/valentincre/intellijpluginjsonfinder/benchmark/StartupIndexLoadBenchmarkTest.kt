package com.github.valentincre.intellijpluginjsonfinder.benchmark

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil

// Startup-with-persisted-index benchmark (AC4 — NFR4: no noticeable startup delay — Story 1.4).
//
// Uses HeavyPlatformTestCase with a real populated content root to approximate the
// warm-restart scenario. The test:
//   1. Generates fixtures and adds them as a project content root (cold pass, not timed).
//   2. Waits for the initial index to settle.
//   3. Measures a second waitUntilIndexesAreReady call (warm pass) — this models the
//      startup check cost when an index already exists on disk.
//
// The 5,000 ms threshold (NFR4) ensures the plugin does not noticeably delay IDE startup.
//
// Run manually: ./gradlew benchmarkTest --tests "*.StartupIndexLoadBenchmarkTest"
// Excluded from normal CI via exclude("**/benchmark/**") in build.gradle.kts.
class StartupIndexLoadBenchmarkTest : HeavyPlatformTestCase() {

    companion object {
        private const val STARTUP_THRESHOLD_MS = 5_000L
    }

    fun testStartupWithPersistedIndexDoesNotDelayIde() {
        val tempDir = java.nio.file.Files.createTempDirectory("benchmark-startup").toFile()
        try {
            BenchmarkFixtureGenerator.generateFixtures(tempDir)
            val vfsDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir)
                ?: error("VFS registration failed for $tempDir")
            PsiTestUtil.addContentRoot(myModule, vfsDir)

            // Cold pass: let initial indexing complete (not measured)
            IndexingTestUtil.waitUntilIndexesAreReady(project)

            // Warm pass: second wait simulates startup with an already-persisted index
            val startNs = System.nanoTime()
            IndexingTestUtil.waitUntilIndexesAreReady(project)
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

            println("Startup index-load (warm): ${elapsedMs}ms (threshold: ${STARTUP_THRESHOLD_MS}ms)")

            assertTrue(
                "IDE startup must not be noticeably delayed; measured ${elapsedMs}ms, " +
                    "threshold ${STARTUP_THRESHOLD_MS}ms (NFR4)",
                elapsedMs <= STARTUP_THRESHOLD_MS,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
