package io.github.tenth151151.aathreadviewer.thread

import io.github.tenth151151.aathreadviewer.dat.DatThread
import io.github.tenth151151.aathreadviewer.dat.Post
import io.github.tenth151151.aathreadviewer.data.FakeThreadRepository
import io.github.tenth151151.aathreadviewer.data.LoadedThread
import io.github.tenth151151.aathreadviewer.data.ThreadLoadException
import io.github.tenth151151.aathreadviewer.data.ThreadLoadFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest(testDispatcher, testBody = block)

    @Test
    fun `読み込みの間は読み込み中で、読めたら表示できるになる`() =
        runViewModelTest {
            val repository = FakeThreadRepository()
            val viewModel = ThreadViewModel("content://example/thread.dat", repository)

            advanceUntilIdle()
            assertEquals(ThreadUiState.Loading, viewModel.uiState.value)

            val posts = listOf(Post.Readable(1, "n", "m", "d", "b"), Post.Unreadable(2))
            repository.result.complete(Result.success(LoadedThread(DatThread(title = "題", posts = posts), fileName = null)))
            advanceUntilIdle()

            assertEquals(ThreadUiState.Success(heading = ThreadHeading.Title("題"), posts = posts), viewModel.uiState.value)
            assertEquals(listOf("content://example/thread.dat"), repository.requestedUris)
        }

    private suspend fun TestScope.headingOf(
        title: String?,
        fileName: String?,
    ): ThreadHeading {
        val repository = FakeThreadRepository()
        val viewModel = ThreadViewModel("content://example/thread.dat", repository)
        repository.result.complete(Result.success(LoadedThread(DatThread(title = title, posts = emptyList()), fileName)))
        advanceUntilIdle()
        return (viewModel.uiState.value as ThreadUiState.Success).heading
    }

    @Test
    fun `タイトルがあればファイル名があってもタイトルを見出しにする`() =
        runViewModelTest {
            assertEquals(ThreadHeading.Title("題"), headingOf(title = "題", fileName = "thread.dat"))
        }

    @Test
    fun `タイトルがなくファイル名があればファイル名を見出しにする`() =
        runViewModelTest {
            assertEquals(ThreadHeading.FileName("thread.dat"), headingOf(title = null, fileName = "thread.dat"))
        }

    @Test
    fun `タイトルもファイル名もなければ見出しはない`() =
        runViewModelTest {
            assertEquals(ThreadHeading.None, headingOf(title = null, fileName = null))
        }

    @Test
    fun `ファイル名が空白だけなら見出しはない`() =
        runViewModelTest {
            assertEquals(ThreadHeading.None, headingOf(title = null, fileName = " "))
        }

    private suspend fun TestScope.uiStateAfterFailure(error: Throwable): ThreadUiState {
        val repository = FakeThreadRepository()
        val viewModel = ThreadViewModel("content://example/thread.dat", repository)

        advanceUntilIdle()
        assertEquals(ThreadUiState.Loading, viewModel.uiState.value)

        repository.result.complete(Result.failure(error))
        advanceUntilIdle()
        return viewModel.uiState.value
    }

    @Test
    fun `開けなかったら開けない原因のエラーになる`() =
        runViewModelTest {
            val state = uiStateAfterFailure(ThreadLoadException(ThreadLoadFailure.CannotOpen, "開けません"))

            assertEquals(ThreadUiState.Error(ThreadLoadFailure.CannotOpen), state)
        }

    @Test
    fun `datとして読めなかったらdatとして読めない原因のエラーになる`() =
        runViewModelTest {
            val state = uiStateAfterFailure(ThreadLoadException(ThreadLoadFailure.NotDat, "dat ではありません"))

            assertEquals(ThreadUiState.Error(ThreadLoadFailure.NotDat), state)
        }

    @Test
    fun `大きすぎたら大きすぎる原因のエラーになる`() =
        runViewModelTest {
            val state = uiStateAfterFailure(ThreadLoadException(ThreadLoadFailure.TooLarge, "大きすぎます"))

            assertEquals(ThreadUiState.Error(ThreadLoadFailure.TooLarge), state)
        }

    @Test
    fun `原因が分からない失敗は開けない原因のエラーにする`() =
        runViewModelTest {
            val state = uiStateAfterFailure(IOException("読めません"))

            assertEquals(ThreadUiState.Error(ThreadLoadFailure.CannotOpen), state)
        }
}
