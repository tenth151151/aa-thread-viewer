package io.github.tenth151151.aathreadviewer.dat

import org.apache.commons.text.StringEscapeUtils

object DatParser {
    private const val SEPARATOR = "<>"
    private const val MIN_FIELD_COUNT = 4
    private const val TITLE_INDEX = 4
    private val TAG = Regex("<[^>]*>")

    fun parse(text: String): DatThread {
        // 行は \n だけで分け、欄の中の \r では分けない。行末の \r を外してから、末尾の空白だけの行を捨てる。
        val lines = text.split('\n').map { it.removeSuffix("\r") }.dropLastWhile { it.isBlank() }
        val rows = lines.map { it.split(SEPARATOR) }
        val posts =
            rows.mapIndexed { index, fields ->
                val number = index + 1
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
            }
        val title =
            rows
                .firstOrNull()
                ?.getOrNull(TITLE_INDEX)
                ?.takeIf { it.isNotBlank() }
                ?.let { unescape(stripTags(it)) }
        return DatThread(title = title, posts = posts)
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
