package io.github.tenth151151.aathreadviewer.dat

import org.apache.commons.text.StringEscapeUtils
import java.io.Reader

object DatParser {
    private const val SEPARATOR = "<>"
    private const val MIN_FIELD_COUNT = 4
    private const val TITLE_INDEX = 4
    private val TAG = Regex("<[^>]*>")

    private const val READ_BUFFER_SIZE = 8 * 1024

    // 1 行の長さの上限 (文字数)。改行がほとんどない巨大なファイルで、巨大な 1 行をメモリに載せないために設ける。
    // 上限を超える行のあるファイルは、dat として読めないものとして扱う。行末の \r も数える。
    // 超えたら、その時点で読むのをやめる。
    private const val MAX_LINE_LENGTH = 1024 * 1024

    /**
     * [reader] を 1 行ずつ読みながら解析する。全文の文字列も、全部の行の欄も作らない。[reader] は閉じない。
     * 内部のバッファで [READ_BUFFER_SIZE] 文字ずつ読み、`\n` で行に分ける。
     * 1 行が [maxLineLength] 文字を超えたら、それ以上読まずに [LineTooLongException] を投げる。
     */
    fun parse(
        reader: Reader,
        maxLineLength: Int = MAX_LINE_LENGTH,
    ): DatThread {
        val posts = mutableListOf<Post>()
        var title: String? = null
        var lineCount = 0
        // 空白だけの行は、欄が足りないので、必ず読めなかったレスになる。末尾の空白だけの行を捨てるため、数だけ保留する。
        var pendingBlankLines = 0
        forEachLine(reader, maxLineLength) { line ->
            lineCount++
            if (line.isBlank()) {
                pendingBlankLines++
                return@forEachLine
            }
            repeat(pendingBlankLines) { posts.add(Post.Unreadable(posts.size + 1)) }
            pendingBlankLines = 0
            val fields = line.split(SEPARATOR)
            if (lineCount == 1) {
                // 空白だけかは、タグを外して文字参照を戻したあとで判定する。
                title =
                    fields
                        .getOrNull(TITLE_INDEX)
                        ?.let { unescape(stripTags(it)) }
                        ?.takeIf { it.isNotBlank() }
            }
            posts.add(toPost(posts.size + 1, fields))
        }
        return DatThread(title = title, posts = posts)
    }

    // 行は \n だけで分け、欄の中の \r では分けない。行末の \r は外す。
    // BufferedReader.readLine は単独の \r でも行を分けるので使わない。
    private fun forEachLine(
        reader: Reader,
        maxLineLength: Int,
        onLine: (String) -> Unit,
    ) {
        val buffer = CharArray(READ_BUFFER_SIZE)
        val line = StringBuilder()

        fun append(
            from: Int,
            to: Int,
        ) {
            line.appendRange(buffer, from, to)
            if (line.length > maxLineLength) throw LineTooLongException("1 行が $maxLineLength 文字を超えています。")
        }

        fun emit() {
            if (line.endsWith('\r')) line.setLength(line.length - 1)
            onLine(line.toString())
            line.setLength(0)
        }
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            var start = 0
            for (i in 0 until read) {
                if (buffer[i] == '\n') {
                    append(start, i)
                    emit()
                    start = i + 1
                }
            }
            append(start, read)
        }
        // 最後の \n のあとの文字列も、1 行として扱う。\n で終わるときは空になり、空白だけの行として捨てられる。
        emit()
    }

    private fun toPost(
        number: Int,
        fields: List<String>,
    ): Post =
        if (fields.size < MIN_FIELD_COUNT) {
            Post.Unreadable(number)
        } else {
            Post.Readable(
                number = number,
                name = unescape(stripTags(fields[0])),
                mail = fields[1],
                dateAndId = fields[2],
                body = unescape(formatBody(fields[3])),
            )
        }

    // 書き込むときに足された空白を、<br> を改行にする前に 1 つずつ取り除く。
    // <br> は先に改行にする。タグを先に外すと、<br> が改行にならずに消える。
    private fun formatBody(raw: String): String = stripTags(removeAddedSpaces(raw).replace("<br>", "\n"))

    private fun removeAddedSpaces(raw: String): String =
        raw
            .removePrefix(" ")
            .removeSuffix(" ")
            .replace(" <br>", "<br>")
            .replace("<br> ", "<br>")

    private fun stripTags(raw: String): String = raw.replace(TAG, "")

    // 文字参照は、タグを外したあとに戻す。先に戻すと、&lt;b&gt; が <b> になり、タグとして消える。
    // 範囲を超えた数値の文字参照では例外が出るため、その欄は文字参照を戻さずに残す。
    private fun unescape(raw: String): String =
        try {
            StringEscapeUtils.unescapeHtml4(raw)
        } catch (_: IllegalArgumentException) {
            raw
        }
}
