package io.github.tenth151151.aathreadviewer.dat

import org.junit.Assert.assertEquals
import org.junit.Test

class DatLineParserTest {
    @Test
    fun `2行目以降の通常の1行を4項目に分けられる`() {
        val line = "名無しさん<>sage<>2026/10/11(日) 12:34:56.78 ID:abcd1234<> 本文です <>"

        val post = DatLineParser.parse(line)

        assertEquals(
            Post(
                name = "名無しさん",
                mail = "sage",
                dateAndId = "2026/10/11(日) 12:34:56.78 ID:abcd1234",
                body = " 本文です ",
            ),
            post,
        )
    }
}
