package io.github.tenth151151.aathreadviewer

import android.content.Context
import io.github.tenth151151.aathreadviewer.data.BoardType
import io.github.tenth151151.aathreadviewer.data.ContentResolverDatSource
import io.github.tenth151151.aathreadviewer.data.DefaultThreadRepository
import io.github.tenth151151.aathreadviewer.data.ThreadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Repository などを作って、ViewModel に渡す入れ物。 */
class AppContainer(
    context: Context,
) {
    // データ源と Repository には、同じディスパッチャーを渡す。
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // 今の掲示板の種類は、ぜろちゃんねるプラス系だけ。
    val threadRepository: ThreadRepository =
        DefaultThreadRepository(
            datSource =
                ContentResolverDatSource(
                    contentResolver = context.contentResolver,
                    dispatcher = ioDispatcher,
                ),
            boardType = BoardType.ZeroChannelPlus,
            dispatcher = ioDispatcher,
        )
}
