package com.github.valentincre.intellijpluginjsonfinder.util

import kotlin.math.abs

object FuzzyMatchUtil {

    // Scores at or above this threshold are excluded from suggestion results (segment-count mismatch penalty).
    const val SEGMENT_MISMATCH_PENALTY = Int.MAX_VALUE / 2

    /**
     * Segment-aware Levenshtein: splits both strings on '.', sums per-segment edit distances.
     * Returns a large penalty when segment counts differ.
     */
    fun segmentLevenshtein(query: String, candidate: String): Int {
        val querySegs = query.split('.')
        val candidateSegs = candidate.split('.')
        if (querySegs.size != candidateSegs.size) {
            return SEGMENT_MISMATCH_PENALTY + abs(querySegs.size - candidateSegs.size)
        }
        return querySegs.zip(candidateSegs).sumOf { (q, c) -> levenshtein(q, c) }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }
}
