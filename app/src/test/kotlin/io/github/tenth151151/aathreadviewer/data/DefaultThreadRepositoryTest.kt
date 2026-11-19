package io.github.tenth151151.aathreadviewer.data

import io.github.tenth151151.aathreadviewer.dat.DatParser
import io.github.tenth151151.aathreadviewer.dat.Post
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultThreadRepositoryTest {
    // windows-31j のバイト列を、変換を通さずに直接書く。① は 0x87 0x40、髙 は 0xFB 0xFC。
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private val ascii = { text: String -> text.toByteArray(Charsets.US_ASCII) }

    /** 渡された仕事を溜め、[runAll] を呼ぶまで実行しないディスパッチャー。 */
    private class ManualDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }
    }

    private fun TestScope.repository(datSource: DatSource) =
        DefaultThreadRepository(
            datSource = datSource,
            boardType = BoardType.ZeroChannelPlus,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

    @Test
    fun `windows-31jのバイト列から拡張文字を含むスレッドを返す`() =
        runTest {
            // 名前: ① 、メール: m、日付と ID: d、本文: 髙、タイトル: t
            val dat =
                bytes(0x87, 0x40) + ascii("<>m<>d<>") + bytes(0xFB, 0xFC) + ascii("<>t\n")
            val repository = repository(FakeDatSource(dat))

            val thread = repository.loadThread("content://example/thread.dat").getOrThrow().thread

            assertEquals("t", thread.title)
            assertEquals(
                listOf(Post.Readable(1, "①", "m", "d", "髙")),
                thread.posts,
            )
        }

    private suspend fun failureOf(repository: ThreadRepository): ThreadLoadFailure {
        val result = repository.loadThread("content://example/thread.dat")
        return (result.exceptionOrNull() as ThreadLoadException).failure
    }

    @Test
    fun `データ源が開けないときは開けないエラーを返す`() =
        runTest {
            val repository = repository(FakeDatSource(error = IOException("読めません")))

            assertEquals(ThreadLoadFailure.CannotOpen, failureOf(repository))
        }

    @Test
    fun `データ源が大きすぎると言うときは大きすぎるエラーを返す`() =
        runTest {
            val repository = repository(FakeDatSource(error = FileTooLargeException("大きすぎます")))

            assertEquals(ThreadLoadFailure.TooLarge, failureOf(repository))
        }

    @Test
    fun `1行もdatとして読めないときはdatとして読めないエラーを返す`() =
        runTest {
            val repository = repository(FakeDatSource(ascii("not a dat\nanother line\n")))

            assertEquals(ThreadLoadFailure.NotDat, failureOf(repository))
        }

    @Test
    fun `空のファイルはdatとして読めないエラーを返す`() =
        runTest {
            val repository = repository(FakeDatSource(ByteArray(0)))

            assertEquals(ThreadLoadFailure.NotDat, failureOf(repository))
        }

    @Test
    fun `一部の行が読めなくても読める行があればスレッドを返す`() =
        runTest {
            val dat = ascii("a<>b<>c<>d<>t\nbroken line\n")
            val repository = repository(FakeDatSource(dat))

            val thread = repository.loadThread("content://example/thread.dat").getOrThrow().thread

            assertEquals(listOf(Post.Readable(1, "a", "b", "c", "d"), Post.Unreadable(2)), thread.posts)
        }

    @Test
    fun `データ源が返したファイル名をスレッドといっしょに返す`() =
        runTest {
            val repository = repository(FakeDatSource(ascii("a<>b<>c<>d<>t\n"), fileName = "thread.dat"))

            val loaded = repository.loadThread("content://example/thread.dat").getOrThrow()

            assertEquals("thread.dat", loaded.fileName)
            assertEquals("t", loaded.thread.title)
        }

    @Test
    fun `データ源がファイル名を返さないときはファイル名がない`() =
        runTest {
            val repository = repository(FakeDatSource(ascii("a<>b<>c<>d<>t\n"), fileName = null))

            val loaded = repository.loadThread("content://example/thread.dat").getOrThrow()

            assertNull(loaded.fileName)
        }

    @Test
    fun `データ源を読む前に渡されたディスパッチャーへ切り替える`() =
        runTest {
            // 仕事を溜めるだけで、手で動かすまで実行しないディスパッチャーを渡す。
            // テスト用のディスパッチャーは、runTest とスケジューラーを共有しないと使えず、共有すると advanceUntilIdle が両方を進めてしまう。
            // そのため、渡したディスパッチャーを使わなくても通るテストになってしまうので、使わない。
            val workerDispatcher = ManualDispatcher()
            var readStarted = false
            val repository =
                DefaultThreadRepository(
                    datSource =
                        FakeDatSource(
                            streamFactory = {
                                object : ByteArrayInputStream(ascii("a<>b<>c<>d<>t\n")) {
                                    override fun read(
                                        b: ByteArray,
                                        off: Int,
                                        len: Int,
                                    ): Int {
                                        readStarted = true
                                        return super.read(b, off, len)
                                    }
                                }
                            },
                        ),
                    boardType = BoardType.ZeroChannelPlus,
                    dispatcher = workerDispatcher,
                )

            val result = async { repository.loadThread("content://example/thread.dat") }
            advanceUntilIdle()
            // 確認が失敗しても、溜まった仕事を流してから終わる。流さないと、runTest のタイムアウトまで待って失敗し、本当の失敗が隠れる。
            try {
                assertFalse(result.isCompleted)
                assertFalse(readStarted)
            } finally {
                workerDispatcher.runAll()
            }
            advanceUntilIdle()

            assertTrue(result.isCompleted)
            assertEquals(
                "t",
                result
                    .await()
                    .getOrThrow()
                    .thread.title,
            )
        }

    /** [first] を 1 回目の読み込みで返し、2 回目の読み込みで [error] を投げるストリーム。 */
    private class FailingAfterFirstReadInputStream(
        private val first: ByteArray,
        private val error: Exception,
    ) : InputStream() {
        private var position = 0

        override fun read(): Int = throw UnsupportedOperationException()

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (position >= first.size) throw error
            val count = minOf(len, first.size - position)
            first.copyInto(b, off, position, position + count)
            position += count
            return count
        }
    }

    @Test
    fun `解析の途中で上限を超えたときは途中までのスレッドを返さず大きすぎるエラーを返す`() =
        runTest {
            val line = ascii("a<>b<>c<>d<>t\n")
            val repository =
                repository(
                    FakeDatSource(streamFactory = { FailingAfterFirstReadInputStream(line, FileTooLargeException("大きすぎます")) }),
                )

            val result = repository.loadThread("content://example/thread.dat")

            assertTrue(result.isFailure)
            assertEquals(ThreadLoadFailure.TooLarge, (result.exceptionOrNull() as ThreadLoadException).failure)
        }

    @Test
    fun `読んでいる途中のIOExceptionは開けないエラーを返す`() =
        runTest {
            val line = ascii("a<>b<>c<>d<>t\n")
            val repository =
                repository(
                    FakeDatSource(streamFactory = { FailingAfterFirstReadInputStream(line, IOException("途切れました")) }),
                )

            assertEquals(ThreadLoadFailure.CannotOpen, failureOf(repository))
        }

    @Test
    fun `ブロックの中で起きたIOException以外の例外は捕まえずに投げる`() =
        runTest {
            val line = ascii("a<>b<>c<>d<>t\n")
            val repository =
                repository(
                    FakeDatSource(streamFactory = { FailingAfterFirstReadInputStream(line, IllegalStateException("想定外")) }),
                )

            val thrown = runCatching { repository.loadThread("content://example/thread.dat") }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException)
        }

    @Test
    fun `1行が長すぎるときはdatとして読めないエラーを返す`() =
        runTest {
            val repository = repository(FakeDatSource(bytes = ascii("a".repeat(1024 * 1024 + 1))))

            assertEquals(ThreadLoadFailure.NotDat, failureOf(repository))
        }

    @Test
    fun `変換できないバイトは文字列に変換するときと同じ置き換え文字になる`() =
        runTest {
            // 先頭バイトのあとが不正なバイト、先頭バイトだけで終わるバイト列、どの文字にもならないバイトを含む。
            val body = bytes(0x81, 0x20, 0x41, 0xFF, 0x82, 0x40, 0xFC)
            val dat = ascii("a<>b<>c<>") + body + ascii("<>t\n") + bytes(0x81)
            val repository = repository(FakeDatSource(dat))

            val expected = DatParser.parse(StringReader(String(dat, BoardType.ZeroChannelPlus.charset)))
            val actual = repository.loadThread("content://example/thread.dat").getOrThrow().thread

            assertEquals(expected, actual)
            assertTrue((actual.posts[0] as Post.Readable).body.contains('�'))
        }
}
