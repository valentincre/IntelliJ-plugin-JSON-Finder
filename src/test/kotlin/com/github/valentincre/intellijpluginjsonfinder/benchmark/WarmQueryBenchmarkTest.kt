package com.github.valentincre.intellijpluginjsonfinder.benchmark

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import com.github.valentincre.intellijpluginjsonfinder.index.JsonKeyIndex

// Warm-query benchmark (AC3 — NFR1: < 50ms per lookup — Story 1.4).
//
// Uses BasePlatformTestCase (light platform) because the index is populated
// synchronously when the fixture is configured.
//
// Fixture content is generated programmatically (no committed file) to satisfy
// the Architecture Compliance constraint. Queries go through
// FileBasedIndex.getInstance().processValues() directly, which is the underlying
// mechanism used by JsonFinderProjectService.findDefinitions().
//
// Run manually: ./gradlew benchmarkTest --tests "*.WarmQueryBenchmarkTest"
// Excluded from normal CI via exclude("**/benchmark/**") in build.gradle.kts.
class WarmQueryBenchmarkTest : BasePlatformTestCase() {

    companion object {
        private const val REPEAT_COUNT = 10
        private const val MAX_QUERY_MS = 50L
    }

    fun testWarmQueryMeetsNfr1Threshold() {
        // Generate fixture content programmatically — no committed static file
        val fixtureContent = buildString {
            append("{\n  \"section_0\": {\n")
            repeat(BenchmarkFixtureGenerator.KEYS_PER_FILE) { keyIdx ->
                val comma = if (keyIdx < BenchmarkFixtureGenerator.KEYS_PER_FILE - 1) "," else ""
                append("    \"key_$keyIdx\": \"value_0_$keyIdx\"$comma\n")
            }
            append("  }\n}\n")
        }
        myFixture.configureByText("fixture.json", fixtureContent)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val scope = GlobalSearchScope.allScope(project)

        // Verify the live FileBasedIndex actually contains the expected key before timing.
        // This catches cases where the indexer was not invoked or produced no entries.
        var indexHasEntries = false
        ReadAction.compute<Unit, Throwable> {
            FileBasedIndex.getInstance().processValues(
                JsonKeyIndex.KEY, "section_0.key_0", null,
                { _, _ -> indexHasEntries = true; true },
                scope,
            )
        }
        assertTrue("FileBasedIndex must contain 'section_0.key_0' after indexing fixture.json", indexHasEntries)

        // Warm-up call: first invocation may include lazy initialization overhead
        ReadAction.compute<Unit, Throwable> {
            FileBasedIndex.getInstance().processValues(
                JsonKeyIndex.KEY, "section_0.key_0", null, { _, _ -> true }, scope,
            )
        }

        // Timed runs
        val timings = LongArray(REPEAT_COUNT)
        repeat(REPEAT_COUNT) { i ->
            val start = System.nanoTime()
            ReadAction.compute<Unit, Throwable> {
                FileBasedIndex.getInstance().processValues(
                    JsonKeyIndex.KEY, "section_0.key_0", null, { _, _ -> true }, scope,
                )
            }
            timings[i] = (System.nanoTime() - start) / 1_000_000L
        }

        val maxMs = timings.maxOrNull() ?: 0L
        val minMs = timings.minOrNull() ?: 0L
        val avgMs = timings.average().toLong()

        println("Warm query timings (ms): ${timings.joinToString()} -> min=$minMs max=$maxMs avg=$avgMs")

        assertTrue(
            "All ${REPEAT_COUNT} warm queries must complete in < ${MAX_QUERY_MS}ms (NFR1); max measured: ${maxMs}ms",
            maxMs < MAX_QUERY_MS,
        )
    }
}
