package io.github.tenth151151.aathreadviewer.thread

import io.github.tenth151151.aathreadviewer.dat.Post
import io.github.tenth151151.aathreadviewer.data.ThreadLoadFailure

/** スレッドの見出し。 */
sealed interface ThreadHeading {
    /** dat のタイトル。 */
    data class Title(
        val text: String,
    ) : ThreadHeading

    /** dat にタイトルがないときの、ファイル名。 */
    data class FileName(
        val name: String,
    ) : ThreadHeading

    /** タイトルもファイル名もない。 */
    data object None : ThreadHeading
}

sealed interface ThreadUiState {
    data object Loading : ThreadUiState

    data class Success(
        val heading: ThreadHeading,
        val posts: List<Post>,
    ) : ThreadUiState

    /** [failure] は、読めなかった原因。 */
    data class Error(
        val failure: ThreadLoadFailure,
    ) : ThreadUiState
}
