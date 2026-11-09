package io.github.tenth151151.aathreadviewer.data

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
import java.io.IOException
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
    fun `文字列への変換と解析は渡されたディスパッチャーで行う`() =
        runTest {
            // 仕事を溜めるだけで、手で動かすまで実行しないディスパッチャーを渡す。
            // テスト用のディスパッチャーは、runTest とスケジューラーを共有しないと使えず、共有すると advanceUntilIdle が両方を進めてしまう。
            // そのため、渡したディスパッチャーを使わなくても通るテストになってしまうので、使わない。
            val workerDispatcher = ManualDispatcher()
            val repository =
                DefaultThreadRepository(
                    datSource = FakeDatSource(ascii("a<>b<>c<>d<>t\n")),
                    boardType = BoardType.ZeroChannelPlus,
                    dispatcher = workerDispatcher,
                )

            val result = async { repository.loadThread("content://example/thread.dat") }
            advanceUntilIdle()
            assertFalse(result.isCompleted)

            workerDispatcher.runAll()
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
}
