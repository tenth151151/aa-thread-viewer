package io.github.tenth151151.aathreadviewer.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 全体を読んでバイト列にする。[maxBytes] を超えるときは、上限を 1 バイト超えたところで読むのをやめて、
 * [FileTooLargeException] を投げる。
 */
fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
        // 上限を超えたことが分かる最小の 1 バイトまでしか読まない。
        val remaining = maxBytes.toLong() + 1 - output.size()
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) return output.toByteArray()
        output.write(buffer, 0, read)
        if (output.size() > maxBytes) throw FileTooLargeException("ファイルが $maxBytes バイトを超えています。")
    }
}

private const val BUFFER_SIZE = 8 * 1024
