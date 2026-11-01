package io.github.tenth151151.aathreadviewer.thread

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** [uri] は、選んだ dat のファイルの Uri を文字列にしたもの。 */
@Serializable
data class ThreadKey(
    val uri: String,
) : NavKey
