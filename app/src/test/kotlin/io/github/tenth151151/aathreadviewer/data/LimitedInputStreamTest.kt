package io.github.tenth151151.aathreadviewer.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException

class LimitedInputStreamTest {
    private fun data(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    private fun LimitedInputStream.readOneByteAtATime(): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val value = read()
            if (value < 0) return output.toByteArray()
            output.write(value)
        }
    }

    private fun LimitedInputStream.readWithArray(bufferSize: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = read(buffer, 0, buffer.size)
            if (read < 0) return output.toByteArray()
            output.write(buffer, 0, read)
        }
    }

    /** 読んだバイト数を数える、終わりのないストリーム。 */
    private class EndlessInputStream : InputStream() {
        var totalRead = 0L

        override fun read(): Int {
            totalRead++
            return 0
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            totalRead += len
            return len
        }
    }

    @Test
    fun `上限より小さいデータは1バイトずつでも配列でも全部読める`() {
        val data = data(10)

        assertArrayEquals(data, LimitedInputStream(ByteArrayInputStream(data), 100).readOneByteAtATime())
        assertArrayEquals(data, LimitedInputStream(ByteArrayInputStream(data), 100).readWithArray(4))
    }

    @Test
    fun `ちょうど上限の大きさのデータは1バイトずつでも配列でも全部読める`() {
        val data = data(100)

        assertArrayEquals(data, LimitedInputStream(ByteArrayInputStream(data), 100).readOneByteAtATime())
        assertArrayEquals(data, LimitedInputStream(ByteArrayInputStream(data), 100).readWithArray(30))
        assertArrayEquals(data, LimitedInputStream(ByteArrayInputStream(data), 100).readWithArray(1000))
    }

    @Test
    fun `空のデータは1バイトずつでも配列でも空になる`() {
        assertEquals(0, LimitedInputStream(ByteArrayInputStream(ByteArray(0)), 100).readOneByteAtATime().size)
        assertEquals(0, LimitedInputStream(ByteArrayInputStream(ByteArray(0)), 100).readWithArray(8).size)
    }

    @Test
    fun `上限を1バイト超えるデータは1バイトずつ読むと大きすぎるとして失敗する`() {
        val stream = LimitedInputStream(ByteArrayInputStream(data(101)), 100)

        assertThrows(FileTooLargeException::class.java) { stream.readOneByteAtATime() }
    }

    @Test
    fun `上限を1バイト超えるデータは配列で読むと大きすぎるとして失敗する`() {
        val stream = LimitedInputStream(ByteArrayInputStream(data(101)), 100)

        assertThrows(FileTooLargeException::class.java) { stream.readWithArray(30) }
    }

    @Test
    fun `配列が上限より大きくても上限を超えたデータは失敗する`() {
        val stream = LimitedInputStream(ByteArrayInputStream(data(101)), 100)

        assertThrows(FileTooLargeException::class.java) { stream.readWithArray(1000) }
    }

    @Test
    fun `終わりのないストリームは1バイトずつ読んでも上限を超えた1バイトで読むのをやめる`() {
        val endless = EndlessInputStream()

        assertThrows(FileTooLargeException::class.java) { LimitedInputStream(endless, 100).readOneByteAtATime() }
        assertEquals(101L, endless.totalRead)
    }

    @Test
    fun `終わりのないストリームは配列で読んでも上限を超えた1バイトで読むのをやめる`() {
        val endless = EndlessInputStream()

        assertThrows(FileTooLargeException::class.java) { LimitedInputStream(endless, 100).readWithArray(30) }
        assertEquals(101L, endless.totalRead)
    }

    @Test
    fun `長さ0の読み込みは数えず何も読まない`() {
        val endless = EndlessInputStream()
        val stream = LimitedInputStream(endless, 100)

        assertEquals(0, stream.read(ByteArray(8), 0, 0))
        assertEquals(0L, endless.totalRead)
    }

    @Test
    fun `閉じると包んだストリームも閉じる`() {
        var closed = false
        val delegate =
            object : ByteArrayInputStream(ByteArray(0)) {
                override fun close() {
                    closed = true
                }
            }

        LimitedInputStream(delegate, 100).close()

        assertTrue(closed)
    }

    /** 呼ばれた回数を数え、読むと [error] を投げるストリーム。 */
    private class ThrowingInputStream(
        private val error: Throwable,
    ) : InputStream() {
        var readCalls = 0

        override fun read(): Int {
            readCalls++
            throw error
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            readCalls++
            throw error
        }
    }

    @Test
    fun `包んだストリームがIOException以外を投げたら1バイトずつ読むときはIOExceptionに包んで投げる`() {
        val cause = IllegalStateException("想定外")
        val stream = LimitedInputStream(ThrowingInputStream(cause), 100)

        val thrown = assertThrows(IOException::class.java) { stream.read() }

        assertSame(cause, thrown.cause)
    }

    @Test
    fun `包んだストリームがIOException以外を投げたら配列で読むときはIOExceptionに包んで投げる`() {
        val cause = SecurityException("権限がありません")
        val stream = LimitedInputStream(ThrowingInputStream(cause), 100)

        val thrown = assertThrows(IOException::class.java) { stream.read(ByteArray(8), 0, 8) }

        assertSame(cause, thrown.cause)
    }

    @Test
    fun `包んだストリームがCancellationExceptionを投げたら包まずにそのまま投げる`() {
        val cause = CancellationException("取り消されました")

        assertSame(cause, assertThrows(CancellationException::class.java) { LimitedInputStream(ThrowingInputStream(cause), 100).read() })
        assertSame(
            cause,
            assertThrows(
                CancellationException::class.java,
            ) { LimitedInputStream(ThrowingInputStream(cause), 100).read(ByteArray(8), 0, 8) },
        )
    }

    @Test
    fun `包んだストリームがIOExceptionを投げたらそのまま投げる`() {
        val cause = IOException("途切れました")

        assertSame(cause, assertThrows(IOException::class.java) { LimitedInputStream(ThrowingInputStream(cause), 100).read() })
        assertSame(
            cause,
            assertThrows(IOException::class.java) { LimitedInputStream(ThrowingInputStream(cause), 100).read(ByteArray(8), 0, 8) },
        )
    }

    @Test
    fun `上限を超えたあとに1バイトずつ読んでも元のストリームを読まずに失敗し続ける`() {
        val endless = EndlessInputStream()
        val stream = LimitedInputStream(endless, 100)
        assertThrows(FileTooLargeException::class.java) { stream.readOneByteAtATime() }

        assertThrows(FileTooLargeException::class.java) { stream.read() }
        assertThrows(FileTooLargeException::class.java) { stream.read(ByteArray(8), 0, 8) }
        assertEquals(101L, endless.totalRead)
    }

    @Test
    fun `上限を超えたあとに配列で読んでも元のストリームを読まずに失敗し続ける`() {
        val endless = EndlessInputStream()
        val stream = LimitedInputStream(endless, 100)
        assertThrows(FileTooLargeException::class.java) { stream.readWithArray(30) }

        assertThrows(FileTooLargeException::class.java) { stream.read(ByteArray(8), 0, 8) }
        assertThrows(FileTooLargeException::class.java) { stream.read() }
        assertEquals(101L, endless.totalRead)
    }
}
