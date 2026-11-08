package io.github.tenth151151.aathreadviewer.thread

import io.github.tenth151151.aathreadviewer.dat.DatThread
import io.github.tenth151151.aathreadviewer.dat.Post
import io.github.tenth151151.aathreadviewer.data.FakeThreadRepository
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
            repository.result.complete(Result.success(DatThread(title = "題", posts = posts)))
            advanceUntilIdle()

            assertEquals(ThreadUiState.Success(title = "題", posts = posts), viewModel.uiState.value)
            assertEquals(listOf("content://example/thread.dat"), repository.requestedUris)
        }

    @Test
    fun `読めなかったらエラーになる`() =
        runViewModelTest {
            val repository = FakeThreadRepository()
            val viewModel = ThreadViewModel("content://example/thread.dat", repository)

            advanceUntilIdle()
            assertEquals(ThreadUiState.Loading, viewModel.uiState.value)

            repository.result.complete(Result.failure(IOException("読めません")))
            advanceUntilIdle()

            assertEquals(ThreadUiState.Error, viewModel.uiState.value)
        }
}
