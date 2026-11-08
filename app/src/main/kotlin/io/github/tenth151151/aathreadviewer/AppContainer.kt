package io.github.tenth151151.aathreadviewer

import android.content.Context
import io.github.tenth151151.aathreadviewer.data.BoardType
import io.github.tenth151151.aathreadviewer.data.ContentResolverDatSource
import io.github.tenth151151.aathreadviewer.data.DefaultThreadRepository
import io.github.tenth151151.aathreadviewer.data.ThreadRepository
import kotlinx.coroutines.Dispatchers

/** Repository などを作って、ViewModel に渡す入れ物。 */
class AppContainer(
    context: Context,
) {
    // 今の掲示板の種類は、ぜろちゃんねるプラス系だけ。
    val threadRepository: ThreadRepository =
        DefaultThreadRepository(
            datSource = ContentResolverDatSource(context.contentResolver),
            boardType = BoardType.ZeroChannelPlus,
            dispatcher = Dispatchers.Default,
        )
}
