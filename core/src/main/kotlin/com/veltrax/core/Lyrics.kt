package com.veltrax.core

import java.util.Locale

data class LyricWord(val text: String, val timestampMs: Long)

data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
)

object LrcLyrics {
    private val lineTimestamp = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val wordTimestamp = Regex("""<(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?>""")

    fun parse(source: String): List<LyricLine> = source.lineSequence()
        .flatMap { rawLine ->
            val timestamps = lineTimestamp.findAll(rawLine).map { match ->
                parseTimestamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            }.toList()
            if (timestamps.isEmpty()) return@flatMap emptySequence()

            val body = lineTimestamp.replace(rawLine, "").trim()
            val wordMatches = wordTimestamp.findAll(body).toList()
            val words = wordMatches.mapIndexed { index, match ->
                val end = wordMatches.getOrNull(index + 1)?.range?.first ?: body.length
                LyricWord(
                    body.substring(match.range.last + 1, end).trimStart(),
                    parseTimestamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                )
            }.filter { it.text.isNotBlank() }
            val text = if (words.isEmpty()) body else words.joinToString("") { it.text }

            timestamps.asSequence().map { timestamp -> LyricLine(timestamp, text, words) }
        }
        .sortedBy { it.timestampMs }
        .toList()

    fun export(lines: List<LyricLine>): String = lines
        .sortedBy { it.timestampMs }
        .joinToString(separator = "\n", postfix = if (lines.isEmpty()) "" else "\n") { line ->
            val timestamp = formatTimestamp(line.timestampMs)
            if (line.words.isEmpty()) {
                "[$timestamp]${line.text}"
            } else {
                "[$timestamp]" + line.words.joinToString("") { word ->
                    "<${formatTimestamp(word.timestampMs)}>${word.text}"
                }
            }
        }

    fun formatTimestamp(milliseconds: Long): String {
        val safe = milliseconds.coerceAtLeast(0)
        val minutes = safe / 60_000
        val seconds = safe / 1_000 % 60
        val centiseconds = safe / 10 % 100
        return String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, centiseconds)
    }

    private fun parseTimestamp(minutes: String, seconds: String, fraction: String): Long {
        val fractionMs = when (fraction.length) {
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            3 -> fraction.toLong()
            else -> 0L
        }
        return (minutes.toLong() * 60 + seconds.toLong()) * 1_000 + fractionMs
    }
}
