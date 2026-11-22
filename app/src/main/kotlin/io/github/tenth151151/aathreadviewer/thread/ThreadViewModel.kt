package io.github.tenth151151.aathreadviewer.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.tenth151151.aathreadviewer.data.LoadedThread
import io.github.tenth151151.aathreadviewer.data.ThreadLoadException
import io.github.tenth151151.aathreadviewer.data.ThreadLoadFailure
import io.github.tenth151151.aathreadviewer.data.ThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel
    @AssistedInject
    constructor(
        @Assisted uri: String,
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

        /** 画面のキーの Uri の文字列 [uri] を渡して、ViewModel を作る。 */
        @AssistedFactory
        interface Factory {
            fun create(uri: String): ThreadViewModel
        }
    }
