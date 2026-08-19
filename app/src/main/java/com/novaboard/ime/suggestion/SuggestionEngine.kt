package com.novaboard.ime.suggestion

import kotlin.math.min

/**
 * A compact, on-device suggestion engine. It's intentionally simple (frequency dictionary +
 * bigram next-word map + edit-distance autocorrect) so it needs no network calls or model
 * download; swap `wordFrequency`/`bigrams` for a larger corpus, or replace this class with an
 * ML Kit / on-device LLM-backed implementation later without touching the IME service.
 */
class SuggestionEngine {

    // word -> usage frequency (higher = more common). Seed list; extend freely.
    private val wordFrequency: MutableMap<String, Int> = mutableMapOf(
        "the" to 100, "be" to 95, "to" to 94, "of" to 93, "and" to 92, "a" to 91, "in" to 90,
        "that" to 80, "have" to 79, "I" to 78, "it" to 77, "for" to 76, "not" to 75, "on" to 74,
        "with" to 73, "he" to 72, "as" to 71, "you" to 70, "do" to 69, "at" to 68, "this" to 67,
        "but" to 66, "his" to 65, "by" to 64, "from" to 63, "they" to 62, "we" to 61, "say" to 60,
        "her" to 59, "she" to 58, "or" to 57, "an" to 56, "will" to 55, "my" to 54, "one" to 53,
        "all" to 52, "would" to 51, "there" to 50, "their" to 49, "what" to 48, "so" to 47,
        "up" to 46, "out" to 45, "if" to 44, "about" to 43, "who" to 42, "get" to 41, "which" to 40,
        "go" to 39, "me" to 38, "when" to 37, "make" to 36, "can" to 35, "like" to 34, "time" to 33,
        "no" to 32, "just" to 31, "him" to 30, "know" to 29, "take" to 28, "people" to 27,
        "into" to 26, "year" to 25, "your" to 24, "good" to 23, "some" to 22, "could" to 21,
        "them" to 20, "see" to 19, "other" to 18, "than" to 17, "then" to 16, "now" to 15,
        "look" to 14, "only" to 13, "come" to 12, "its" to 11, "over" to 10, "think" to 9, "also" to 8
    )

    // previous word -> likely next words, ordered by likelihood. Small seed set.
    private val bigrams: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "think", "know", "will", "have"),
        "and" to listOf("the", "then", "which", "so", "but"),
        "the" to listOf("best", "same", "first", "world", "way"),
        "how" to listOf("are", "do", "to", "is", "much"),
        "thank" to listOf("you", "you!", "you so"),
        "let" to listOf("me", "us", "know")
    )

    /** Call whenever the user commits a word, to bias future suggestions toward their habits. */
    fun learn(word: String) {
        val w = word.lowercase()
        if (w.length < 2) return
        wordFrequency[w] = (wordFrequency[w] ?: 0) + 3
    }

    /**
     * Returns up to 3 suggestions for the given typing state.
     * If [currentWord] is non-empty: autocorrect/completion candidates.
     * If [currentWord] is empty: next-word prediction based on [previousWord].
     */
    fun suggest(currentWord: String, previousWord: String?): List<String> {
        return if (currentWord.isNotEmpty()) {
            completions(currentWord)
        } else {
            predictions(previousWord)
        }
    }

    private fun completions(prefix: String): List<String> {
        val lower = prefix.lowercase()
        val prefixMatches = wordFrequency.keys
            .filter { it.startsWith(lower) }
            .sortedByDescending { wordFrequency[it] }

        val result = prefixMatches.toMutableList()
        if (result.size < 3) {
            // fall back to fuzzy (typo-tolerant) matches within edit distance 2
            val fuzzy = wordFrequency.keys
                .filter { it !in result && editDistance(it, lower) <= 2 }
                .sortedBy { editDistance(it, lower) }
            result += fuzzy
        }
        val out = result.take(3).toMutableList()
        if (out.none { it.equals(prefix, ignoreCase = true) }) {
            out.add(0, prefix) // always allow "keep what I typed"
        }
        return out.take(3)
    }

    private fun predictions(previousWord: String?): List<String> {
        val prev = previousWord?.lowercase()
        val next = prev?.let { bigrams[it] } ?: emptyList()
        val fill = wordFrequency.keys.sortedByDescending { wordFrequency[it] }
        return (next + fill).distinct().take(3)
    }

    /** Simple word-level autocorrect: replace the typed word if a close, more common match exists. */
    fun autocorrect(word: String): String? {
        val lower = word.lowercase()
        if (wordFrequency.containsKey(lower)) return null // already a known word
        val best = wordFrequency.keys
            .map { it to editDistance(it, lower) }
            .filter { it.second <= 2 }
            .minByOrNull { it.second * 10 - (wordFrequency[it.first] ?: 0) }
        return best?.first
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + min(dp[i - 1][j - 1], min(dp[i][j - 1], dp[i - 1][j]))
                }
            }
        }
        return dp[a.length][b.length]
    }
}
