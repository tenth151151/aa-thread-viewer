package io.github.tenth151151.aathreadviewer.dat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatParserTest {
    @Test
    fun `通常の1行を4項目に分けて1番のレスにできる`() {
        val text = "名無しさん<>sage<>2026/10/11(日) 12:34:56.78 ID:abcd1234<> 本文です <>"

        val thread = DatParser.parse(text)

        assertEquals(
            listOf(
                Post.Readable(
                    number = 1,
                    name = "名無しさん",
                    mail = "sage",
                    dateAndId = "2026/10/11(日) 12:34:56.78 ID:abcd1234",
                    body = " 本文です ",
                ),
            ),
            thread.posts,
        )
    }

    @Test
    fun `複数行は行の順番を1からのレス番号にする`() {
        val text = "a<>a@<>d1<>b1<>\nb<>b@<>d2<>b2<>\nc<>c@<>d3<>b3<>"

        val thread = DatParser.parse(text)

        assertEquals(listOf(1, 2, 3), thread.posts.map { it.number })
        assertEquals(
            listOf("b1", "b2", "b3"),
            thread.posts.map { (it as Post.Readable).body },
        )
    }

    @Test
    fun `タイトルは1行目の5つ目の欄から取る`() {
        val text = "a<>a@<>d1<>b1<>スレのタイトル\nb<>b@<>d2<>b2<>"

        val thread = DatParser.parse(text)

        assertEquals("スレのタイトル", thread.title)
    }

    @Test
    fun `2行目以降の5つ目の欄はタイトルにしない`() {
        val text = "a<>a@<>d1<>b1<>\nb<>b@<>d2<>b2<>別の欄"

        val thread = DatParser.parse(text)

        assertNull(thread.title)
    }

    @Test
    fun `1行目の5つ目の欄が空ならタイトルはない`() {
        val text = "a<>a@<>d1<>b1<>"

        val thread = DatParser.parse(text)

        assertNull(thread.title)
    }

    @Test
    fun `1行目にタイトルの欄がなければタイトルはない`() {
        val text = "a<>a@<>d1<>b1"

        val thread = DatParser.parse(text)

        assertNull(thread.title)
    }

    @Test
    fun `欄が足りない行は読めなかったレスになり番号を保つ`() {
        val text = "a<>a@<>d1<>b1<>T\nこわれた行<>だけ\nc<>c@<>d3<>b3<>"

        val thread = DatParser.parse(text)

        assertEquals(
            listOf(
                Post.Readable(1, "a", "a@", "d1", "b1"),
                Post.Unreadable(2),
                Post.Readable(3, "c", "c@", "d3", "b3"),
            ),
            thread.posts,
        )
    }

    @Test
    fun `1行目が読めないときはタイトルがなく2行目以降は読める`() {
        val text = "こわれた行\nb<>b@<>d2<>b2<>"

        val thread = DatParser.parse(text)

        assertNull(thread.title)
        assertEquals(
            listOf(Post.Unreadable(1), Post.Readable(2, "b", "b@", "d2", "b2")),
            thread.posts,
        )
    }

    @Test
    fun `空行は読めなかったレスになる`() {
        val text = "a<>a@<>d1<>b1<>\n\nc<>c@<>d3<>b3<>"

        val thread = DatParser.parse(text)

        assertEquals(listOf(1, 2, 3), thread.posts.map { it.number })
        assertEquals(Post.Unreadable(2), thread.posts[1])
    }

    @Test
    fun `末尾の改行で空のレスを作らない`() {
        val text = "a<>a@<>d1<>b1<>T\nb<>b@<>d2<>b2<>\n"

        val thread = DatParser.parse(text)

        assertEquals(listOf(1, 2), thread.posts.map { it.number })
    }

    @Test
    fun `CRLFの行末でも欄に改行の文字が残らない`() {
        val text = "a<>a@<>d1<>b1<>T\r\nb<>b@<>d2<>b2<>\r\n"

        val thread = DatParser.parse(text)

        assertEquals("T", thread.title)
        assertEquals(
            listOf(
                Post.Readable(1, "a", "a@", "d1", "b1"),
                Post.Readable(2, "b", "b@", "d2", "b2"),
            ),
            thread.posts,
        )
    }

    @Test
    fun `空の文字列はタイトルもレスもない`() {
        val thread = DatParser.parse("")

        assertEquals(DatThread(title = null, posts = emptyList()), thread)
    }

    @Test
    fun `1行も読めないdatは読めたレスがなく全部読めなかったレスになる`() {
        val thread = DatParser.parse("こわれた\nこわれた")

        assertEquals(listOf(Post.Unreadable(1), Post.Unreadable(2)), thread.posts)
        assertNull(thread.title)
    }

    @Test
    fun `欄がちょうど3つの行は読めなかったレスになる`() {
        val thread = DatParser.parse("a<>a@<>d1")

        assertEquals(listOf(Post.Unreadable(1)), thread.posts)
    }

    @Test
    fun `欄がちょうど4つの行はタイトルの欄がなくても読めたレスになる`() {
        val thread = DatParser.parse("a<>a@<>d1<>b1")

        assertEquals(listOf(Post.Readable(1, "a", "a@", "d1", "b1")), thread.posts)
    }

    @Test
    fun `6つ目以降の欄は無視して5つ目までで読む`() {
        val thread = DatParser.parse("a<>a@<>d1<>b1<>T<>余分1<>余分2")

        assertEquals("T", thread.title)
        assertEquals(listOf(Post.Readable(1, "a", "a@", "d1", "b1")), thread.posts)
    }

    @Test
    fun `末尾の改行が2つ続いても空行のレスを作らない`() {
        val thread = DatParser.parse("a<>a@<>d1<>b1<>T\n\n")

        assertEquals(listOf(Post.Readable(1, "a", "a@", "d1", "b1")), thread.posts)
    }

    @Test
    fun `末尾のCRLFの空行は空行のレスを作らない`() {
        val thread = DatParser.parse("a<>a@<>d1<>b1<>T\r\n\r\n")

        assertEquals(listOf(Post.Readable(1, "a", "a@", "d1", "b1")), thread.posts)
    }

    @Test
    fun `CRLFの改行だけの文字列はレスもタイトルもない`() {
        val thread = DatParser.parse("\r\n")

        assertEquals(DatThread(title = null, posts = emptyList()), thread)
    }

    @Test
    fun `途中のCRLFの空行は読めなかったレスになり番号を保つ`() {
        val thread = DatParser.parse("a<>a@<>d1<>b1<>\r\n\r\nc<>c@<>d3<>b3<>\r\n")

        assertEquals(
            listOf(
                Post.Readable(1, "a", "a@", "d1", "b1"),
                Post.Unreadable(2),
                Post.Readable(3, "c", "c@", "d3", "b3"),
            ),
            thread.posts,
        )
    }

    @Test
    fun `改行だけの文字列はレスもタイトルもない`() {
        val thread = DatParser.parse("\n")

        assertEquals(DatThread(title = null, posts = emptyList()), thread)
    }

    @Test
    fun `欄の中の単独のCRでレスを分けない`() {
        val text = "a<>a@<>d1<>b\r1<>T\nb<>b@<>d2<>b2<>"

        val thread = DatParser.parse(text)

        assertEquals(
            listOf(
                Post.Readable(1, "a", "a@", "d1", "b\r1"),
                Post.Readable(2, "b", "b@", "d2", "b2"),
            ),
            thread.posts,
        )
    }

    @Test
    fun `1行目の5つ目の欄が空白だけならタイトルはない`() {
        val thread = DatParser.parse("a<>a@<>d1<>b1<> ")

        assertNull(thread.title)
    }

    @Test
    fun `空白以外を含むタイトルは前後の空白をそのまま持つ`() {
        val thread = DatParser.parse("a<>a@<>d1<>b1<> T ")

        assertEquals(" T ", thread.title)
    }

    @Test
    fun `末尾が空白だけの行で終わるときはその行を捨てる`() {
        val row = "a<>a@<>d1<>b1<>T"
        val expected = listOf(Post.Readable(1, "a", "a@", "d1", "b1"))

        assertEquals(expected, DatParser.parse("$row\n \n").posts)
        assertEquals(expected, DatParser.parse("$row\n\t").posts)
    }

    @Test
    fun `途中の空白だけの行は読めなかったレスになり番号を保つ`() {
        val row = "a<>a@<>d1<>b1<>T"

        val thread = DatParser.parse("$row\n \n$row\n")

        assertEquals(
            listOf(
                Post.Readable(1, "a", "a@", "d1", "b1"),
                Post.Unreadable(2),
                Post.Readable(3, "a", "a@", "d1", "b1"),
            ),
            thread.posts,
        )
    }
}
