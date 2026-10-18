package io.github.tenth151151.aathreadviewer.dat

object DatParser {
    private const val SEPARATOR = "<>"
    private const val MIN_FIELD_COUNT = 4
    private const val TITLE_INDEX = 4

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
                        name = fields[0],
                        mail = fields[1],
                        dateAndId = fields[2],
                        body = fields[3],
                    )
                }
            }
        val title = rows.firstOrNull()?.getOrNull(TITLE_INDEX)?.takeIf { it.isNotBlank() }
        return DatThread(title = title, posts = posts)
    }
}
