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
                    body = "本文です",
                ),
            ),
            thread.posts,
        )
    }

    @Test
    fun `本文のリンクのタグを外して文字を残す`() {
        val thread =
            DatParser.parse(
                "a<>a@<>d1<><a href=\"../test/read.cgi/x/1/1\" target=\"_blank\">リンク</a>のあと<>",
            )

        assertEquals(
            listOf(Post.Readable(1, "a", "a@", "d1", "リンクのあと")),
            thread.posts,
        )
    }

    @Test
    fun `本文の太字のタグを外して文字を残す`() {
        val thread = DatParser.parse("a<>a@<>d1<><b>太字</b><>")

        assertEquals(
            listOf(Post.Readable(1, "a", "a@", "d1", "太字")),
            thread.posts,
        )
    }

    @Test
    fun `本文のbrタグとほかのタグが混ざっていても改行を残してタグを外す`() {
        val thread = DatParser.parse("a<>a@<>d1<><b>1行目</b><br>2行目<>")

        assertEquals(
            listOf(Post.Readable(1, "a", "a@", "d1", "1行目\n2行目")),
            thread.posts,
        )
    }

    @Test
    fun `名前のタグを外して文字を残す`() {
        val thread = DatParser.parse("名無し</b>◆abc<b><>a@<>d1<>本文<>")

        assertEquals(
            listOf(Post.Readable(1, "名無し◆abc", "a@", "d1", "本文")),
            thread.posts,
        )
    }

    @Test
    fun `本文の名前つきの文字参照を文字に戻す`() {
        val thread = DatParser.parse("a<>a@<>d1<>&gt;&gt;1 &lt;&amp;&quot;&hearts;&copy;<>")

        assertEquals(
            listOf(Post.Readable(1, "a", "a@", "d1", ">>1 <&\"♥©")),
            thread.posts,
        )
    }

    @Test
    fun `本文の数値の文字参照を10進数も16進数も文字に戻す`() {
        val thread = DatParser.parse("a<>a@<>d1<>&hearts;&#9829;&#x2665;<>")

        assertEquals(
            listOf(Post.Readable(1, "a", "a@", "d1", "♥♥♥")),
            thread.posts,
        )
    }

    @Test
    fun `文字参照は2重に戻さない`() {
        val thread = DatParser.parse("a<>a@<>d1<>&amp;lt;<>")

        assertEquals(
            listOf(Post.Readable(1, "a", "a@", "d1", "&lt;")),
            thread.posts,
        )
    }

    @Test
    fun `文字参照で戻した山括弧はタグとして外さない`() {
        val thread = DatParser.parse("a<>a@<>d1<>&lt;b&gt;太字&lt;/b&gt;<>")

        assertEquals(
            listOf(Post.Readable(1, "a", "a@", "d1", "<b>太字</b>")),
            thread.posts,
        )
    }

    @Test
    fun `名前の文字参照を文字に戻す`() {
        val thread = DatParser.parse("名無し&amp;<>a@<>d1<>本文<>")

        assertEquals(
            listOf(Post.Readable(1, "名無し&", "a@", "d1", "本文")),
            thread.posts,
        )
    }

    @Test
    fun `本文のbrタグを改行にする`() {
        val thread = DatParser.parse("a<>a@<>d1<>1行目<br>2行目<br>3行目<>")

        assertEquals(
            listOf(Post.Readable(1, "a", "a@", "d1", "1行目\n2行目\n3行目")),
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

    private fun bodyOf(rawBody: String): String {
        val post = DatParser.parse("n<>m<>d<>$rawBody<>T").posts.single() as Post.Readable
        return post.body
    }

    @Test
    fun `本文のbrの前後と両端に足された空白を1つずつ取り除く`() {
        assertEquals("1行目\n2行目", bodyOf(" 1行目 <br> 2行目 "))
    }

    @Test
    fun `本文に足された空白がないときは何もしない`() {
        assertEquals("1行目\n2行目", bodyOf("1行目<br>2行目"))
    }

    @Test
    fun `brの後ろの空白が複数のときは1つだけ取り除く`() {
        assertEquals("1行目\n  AA", bodyOf(" 1行目 <br>   AA "))
    }

    @Test
    fun `本文の先頭の空白が複数のときは1つだけ取り除く`() {
        assertEquals("  AA\n2行目", bodyOf("   AA <br> 2行目 "))
    }

    @Test
    fun `本文の先頭の全角の空白は取り除かない`() {
        assertEquals("　全角\n2行目", bodyOf("　全角 <br> 2行目 "))
    }

    @Test
    fun `片側だけに空白があるときはその1つだけを取り除く`() {
        assertEquals("1行目\n2行目", bodyOf("1行目 <br>2行目"))
    }

    @Test
    fun `空行の前後に足された空白を取り除いて空行を残す`() {
        assertEquals("a\n\nb", bodyOf(" a <br>  <br> b "))
    }

    @Test
    fun `名前の空白は取り除かない`() {
        val post = DatParser.parse(" 名無し <>m<>d<>b<>T").posts.single() as Post.Readable

        assertEquals(" 名無し ", post.name)
    }

    @Test
    fun `範囲を超えた数値の文字参照があっても、その欄だけ戻さずにスレッド全体を読める`() {
        val text =
            listOf(
                "名無し&#1114112;<>m<>d<> <b>a</b>&gt;&#1114112;<br>x <>T",
                "名無し<>m<>d<> &gt;&#x110000; <>",
                "名無し&lt;<>m<>d<> &gt;&gt;1 <>",
            ).joinToString("\n")

        val posts = DatParser.parse(text).posts.map { it as Post.Readable }

        assertEquals("名無し&#1114112;", posts[0].name)
        assertEquals("a&gt;&#1114112;\nx", posts[0].body)
        assertEquals("&gt;&#x110000;", posts[1].body)
        assertEquals("名無し<", posts[2].name)
        assertEquals(">>1", posts[2].body)
    }
}
