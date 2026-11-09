package io.github.tenth151151.aathreadviewer.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ReadBytesWithLimitTest {
    private fun data(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    @Test
    fun `上限より小さいデータは全部読める`() {
        val data = data(10)

        assertArrayEquals(data, ByteArrayInputStream(data).readBytesWithLimit(100))
    }

    @Test
    fun `ちょうど上限の大きさのデータは全部読める`() {
        val data = data(100)

        assertArrayEquals(data, ByteArrayInputStream(data).readBytesWithLimit(100))
    }

    @Test
    fun `空のデータは空のバイト列になる`() {
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readBytesWithLimit(100).size)
    }

    @Test
    fun `上限を1バイト超えるデータは大きすぎるとして失敗する`() {
        val data = data(101)

        assertThrows(FileTooLargeException::class.java) {
            ByteArrayInputStream(data).readBytesWithLimit(100)
        }
    }

    @Test
    fun `終わりのないストリームでも上限を超えたところで読むのをやめる`() {
        var totalRead = 0L
        val endless =
            object : InputStream() {
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

        assertThrows(FileTooLargeException::class.java) { endless.readBytesWithLimit(100) }
        // 上限を超えた 1 バイトより先は読まない。
        assertEquals(101L, totalRead)
    }
}
