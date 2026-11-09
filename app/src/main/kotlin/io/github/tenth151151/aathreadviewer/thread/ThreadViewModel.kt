package io.github.tenth151151.aathreadviewer.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tenth151151.aathreadviewer.AatvApplication
import io.github.tenth151151.aathreadviewer.data.LoadedThread
import io.github.tenth151151.aathreadviewer.data.ThreadLoadException
import io.github.tenth151151.aathreadviewer.data.ThreadLoadFailure
import io.github.tenth151151.aathreadviewer.data.ThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThreadViewModel(
    uri: String,
    private val repository: ThreadRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ThreadUiState>(ThreadUiState.Loading)
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value =
                repository.loadThread(uri).fold(
                    onSuccess = {
                        ThreadUiState.Success(heading = headingOf(it), posts = it.thread.posts)
                    },
                    onFailure = { ThreadUiState.Error((it as? ThreadLoadException)?.failure ?: ThreadLoadFailure.CannotOpen) },
                )
        }
    }

    // タイトルを優先し、なければファイル名を見出しにする。
    private fun headingOf(loaded: LoadedThread): ThreadHeading {
        val title = loaded.thread.title
        val fileName = loaded.fileName
        return when {
            title != null -> ThreadHeading.Title(title)
            !fileName.isNullOrBlank() -> ThreadHeading.FileName(fileName)
            else -> ThreadHeading.None
        }
    }

    companion object {
        /** [uri] は、画面のキーの Uri の文字列。Repository は、Application の AppContainer から取る。 */
        fun factory(uri: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AatvApplication
                    ThreadViewModel(uri, application.container.threadRepository)
                }
            }
    }
}
