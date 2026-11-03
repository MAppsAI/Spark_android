package com.example.readaloud

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Turns a foreground window's node tree into reading-order speech text.
 *
 * Collects visible AccessibilityNodeInfo nodes that carry text, drops
 * password fields / tiny labels / offscreen nodes, groups lines that sit on
 * the same visual row, and renders top-to-bottom (reading order) — node
 * traversal order in RecyclerViews is frequently wrong.
 */
object TextExtractor {

    private const val MAX_CHARS = 12_000

    private data class Piece(val text: String, val top: Int, val left: Int, val bottom: Int)

    fun extract(root: AccessibilityNodeInfo?): String {
        val pieces = ArrayList<Piece>()
        collect(root, pieces, depth = 0)
        if (pieces.isEmpty()) return ""

        val visible = pieces.filter { it.bottom > 0 }
        if (visible.isEmpty()) return ""

        // Reading order: sort by top edge; then fold rows whose vertical
        // centres overlap into single lines ordered left-to-right.
        val sorted = visible.sortedWith(compareBy({ it.top }, { it.left }))
        val lines = ArrayList<Pair<IntArray, ArrayList<String>>>() // rep bounds y-range, segments
        for (p in sorted) {
            val centre = (p.top + p.bottom) / 2
            val row = lines.firstOrNull { (bounds, _) -> centre in bounds[0]..bounds[1] }
            if (row != null) {
                row.second.add(p.text)
                val b = row.first
                b[0] = minOf(b[0], p.top); b[1] = maxOf(b[1], p.bottom)
            } else {
                lines.add(Pair(intArrayOf(p.top, p.bottom), arrayListOf(p.text)))
            }
        }

        val sb = StringBuilder()
        val seenLines = HashSet<String>()
        var total = 0
        for ((_, segs) in lines) {
            val line = segs.joinToString(" ").trim()
            if (line.isEmpty()) continue
            if (!seenLines.add(line)) continue // duplicate row (headers etc.)
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
            total += line.length
            if (total >= MAX_CHARS) break
        }
        return sb.toString()
    }

    private fun collect(node: AccessibilityNodeInfo?, out: ArrayList<Piece>, depth: Int) {
        if (node == null || depth > 40) return
        try {
            if (!node.isVisibleToUser) return
            val isPassword = node.isPassword
            val cls = node.className?.toString() ?: ""
            if (!isPassword && !cls.contains("ProgressBar") && !cls.contains("SeekBar")) {
                val raw = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val t = when {
                    !raw.isNullOrBlank() -> raw
                    !desc.isNullOrBlank() && desc != raw -> desc
                    else -> null
                }
                if (t != null && t.length > 1 && looksLikeWords(t)) {
                    val b = android.graphics.Rect()
                    node.getBoundsInScreen(b)
                    if (b.bottom > 0 && b.height() in 8..8000 && b.width() > 20) {
                        out.add(Piece(normalize(t), b.top, b.left, b.bottom))
                    }
                }
            }
            for (i in 0 until node.childCount) collect(node.getChild(i), out, depth + 1)
        } catch (_: Throwable) {
            // Stale node handles throw on some OEM ROMs — skip the branch.
        }
    }

    private fun normalize(t: String): String =
        t.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

    /** Keep only content that reads like language, not icons/glyph rows. */
    private fun looksLikeWords(t: String): Boolean {
        var letters = 0
        for (c in t) if (c.isLetterOrDigit()) letters++
        if (letters < 2) return false
        return letters.toDouble() / t.length > 0.35
    }
}
