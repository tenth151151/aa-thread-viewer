package io.github.tenth151151.aathreadviewer.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tenth151151.aathreadviewer.AatvApplication
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
                    onSuccess = { ThreadUiState.Success(title = it.title, posts = it.posts) },
                    onFailure = { ThreadUiState.Error },
                )
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
