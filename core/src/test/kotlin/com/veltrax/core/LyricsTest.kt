package com.veltrax.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsTest {
    @Test
    fun parsesLrcAndEnhancedWordTimestamps() {
        val lines = LrcLyrics.parse(
            "[00:01.2]Plain line\n[00:02.00]<00:02.10>hello <00:02.50>world\n[00:03.00][00:04.00]Repeated"
        )

        assertEquals(4, lines.size)
        assertEquals(1_200L, lines[0].timestampMs)
        assertEquals("hello world", lines[1].text)
        assertEquals(listOf(2_100L, 2_500L), lines[1].words.map { it.timestampMs })
        assertEquals(listOf(3_000L, 4_000L), lines.takeLast(2).map { it.timestampMs })
    }

    @Test
    fun exportsEditedLinesAsValidLrc() {
        val source = listOf(
            LyricLine(1_250, "hello"),
            LyricLine(2_000, "world", listOf(LyricWord("world", 2_130)))
        )

        assertEquals("[00:01.25]hello\n[00:02.00]<00:02.13>world\n", LrcLyrics.export(source))
    }
}
