package io.github.tenth151151.aathreadviewer.dat

import java.io.Reader

/** [text] を、1 回の読み込みで最大 [chunkSize] 文字ずつ返す [Reader]。読み込みの境目を、小さい入力で試すために使う。 */
class ChunkedReader(
    private val text: String,
    private val chunkSize: Int,
) : Reader() {
    private var position = 0

    override fun read(
        cbuf: CharArray,
        off: Int,
        len: Int,
    ): Int {
        if (len == 0) return 0
        if (position >= text.length) return -1
        val count = minOf(len, chunkSize, text.length - position)
        text.toCharArray(cbuf, off, position, position + count)
        position += count
        return count
    }

    override fun close() = Unit
}
