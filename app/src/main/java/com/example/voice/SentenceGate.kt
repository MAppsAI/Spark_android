package com.example.voice

/**
 * Turns a live LLM token stream into speakable sentences.
 *
 * feed() is called per SSE token; a sentence is emitted (via onSentence)
 * as soon as a sentence-final boundary is seen and the pending text has at
 * least [minWords] words — short fragments wait to join the next sentence.
 * ``` code blocks are suppressed from speech (toggle state tracked across
 * tokens). flush() emits whatever remains at end-of-stream.
 *
 * Not thread-safe: call from the token-reading loop only.
 */
class SentenceGate(
    private val minWords: Int = 3,
    private val maxBuffer: Int = 420,
    private val cleaner: ((String) -> String)? = null,
    private val onSentence: (String) -> Unit
) {
    private val pending = StringBuilder()
    private var inCode = false

    fun feed(token: String) {
        var i = 0
        while (i < token.length) {
            val c = token[i]
            if (c == '`' && token.startsWith("```", i)) {
                if (!inCode) flushPending(force = false)
                inCode = !inCode
                i += 3
                continue
            }
            if (!inCode) pending.append(c)
            i++
        }
        cutSentences()
    }

    fun flush() {
        flushPending(force = true)
    }

    private fun flushPending(force: Boolean) {
        val s = pending.toString().trim()
        if (s.isNotEmpty() && (force || wordCount(s) >= minWords)) {
            pending.setLength(0)
            emit(s)
        }
    }

    private fun cutSentences() {
        while (true) {
            val s = pending.toString()
            val idx = lastBoundary(s)
            if (idx < 0) {
                if (s.length > maxBuffer) {
                    // runaway paragraph without punctuation — hard-cut at a space
                    val cut = s.lastIndexOf(' ', maxBuffer).takeIf { it > 0 } ?: maxBuffer
                    emit(s.substring(0, cut))
                    pending.delete(0, cut)
                }
                return
            }
            val candidate = s.substring(0, idx + 1)
            if (wordCount(candidate) >= minWords) {
                emit(candidate)
                pending.delete(0, idx + 1)
            } else {
                return // too short to stand alone; keep accumulating
            }
        }
    }

    private fun emit(text: String) {
        val cleaned = (cleaner?.invoke(text) ?: text).trim()
        if (cleaned.isNotEmpty()) onSentence(cleaned)
    }

    private fun wordCount(s: String): Int =
        s.split(Regex("\\s+")).count { it.isNotBlank() }

    /** Index of the last sentence boundary, or -1. */
    private fun lastBoundary(s: String): Int {
        var last = -1
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val isBoundary = when {
                c == '\n' -> true
                c == '.' -> {
                    val next = if (i + 1 < s.length) s[i + 1] else ' '
                    val prev = if (i > 0) s[i - 1] else ' '
                    !next.isDigit() && (next == ' ' || next == '\n') && !isAbbrev(prev)
                }
                c == '!' || c == '?' || c == ';' || c == ':' -> {
                    val next = if (i + 1 < s.length) s[i + 1] else ' '
                    next == ' ' || next == '\n' || i == s.length - 1
                }
                else -> false
            }
            if (isBoundary) last = i
            i++
        }
        return last
    }

    private fun isAbbrev(prev: Char): Boolean {
        // "Mr. Smith" / "e.g. so" — a single capital or vowel right before '.'
        // after a non-space is likely an initialism; keep it simple.
        return prev == 'y' && pending.length >= 3 && pending[pending.length - 3] == 'g' // "g." of "e.g."
    }
}
