package io.github.tenth151151.aathreadviewer.data

import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * 読んだバイト数を数え、[maxBytes] を超えたら [FileTooLargeException] を投げる [InputStream]。
 * 上限を超えたことが分かる最小の 1 バイトまでしか読まないので、終わりのないストリームでも、上限で読むのをやめる。
 * 上限を超えたあとに読まれたときは、[input] を読まずに、また [FileTooLargeException] を投げる。
 * [input] の読み込みが [IOException] と [CancellationException] 以外の例外 ([SecurityException] など) を投げたときは、原因にその例外を入れた [IOException] にして投げる。
 * [CancellationException] は、包まずにそのまま投げる。
 * このストリームを読む側 (解析など) で起きた例外は、包まずにそのまま外に出る。
 * 閉じると、[input] も閉じる。
 */
class LimitedInputStream(
    private val input: InputStream,
    private val maxBytes: Int,
) : InputStream() {
    private var count = 0L

    override fun read(): Int {
        checkNotExceeded()
        val value = readFromInput { input.read() }
        if (value >= 0) add(1)
        return value
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        if (len == 0) return 0
        checkNotExceeded()
        // 上限を超えたあとは checkNotExceeded で投げるので、ここでは count は maxBytes 以下であり、remaining は 1 以上になる。
        val remaining = maxBytes.toLong() + 1 - count
        val read = readFromInput { input.read(b, off, minOf(len.toLong(), remaining).toInt()) }
        if (read > 0) add(read)
        return read
    }

    override fun close() = input.close()

    private fun checkNotExceeded() {
        if (count > maxBytes) throw tooLarge()
    }

    private fun add(read: Int) {
        count += read
        checkNotExceeded()
    }

    private fun tooLarge() = FileTooLargeException("ファイルが $maxBytes バイトを超えています。")

    private inline fun readFromInput(block: () -> Int): Int =
        try {
            block()
        } catch (e: IOException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("読み込み中に失敗しました。", e)
        }
}
