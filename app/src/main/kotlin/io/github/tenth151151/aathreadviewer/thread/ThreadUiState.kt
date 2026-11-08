package io.github.tenth151151.aathreadviewer.thread

import io.github.tenth151151.aathreadviewer.dat.Post

sealed interface ThreadUiState {
    data object Loading : ThreadUiState

    /** [title] は、dat にタイトルがないときは null。 */
    data class Success(
        val title: String?,
        val posts: List<Post>,
    ) : ThreadUiState

    data object Error : ThreadUiState
}
