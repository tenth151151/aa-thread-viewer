package io.github.tenth151151.aathreadviewer.data

import io.github.tenth151151.aathreadviewer.dat.DatParser
import io.github.tenth151151.aathreadviewer.dat.DatThread
import io.github.tenth151151.aathreadviewer.dat.Post
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

class DefaultThreadRepository(
    private val datSource: DatSource,
    private val boardType: BoardType,
    private val dispatcher: CoroutineDispatcher,
) : ThreadRepository {
    // runCatching は CancellationException も捕まえてしまうので、IOException だけを捕まえる。
    override suspend fun loadThread(uri: String): Result<DatThread> {
        val bytes =
            try {
                datSource.read(uri)
            } catch (e: IOException) {
                return Result.failure(e)
            }
        // 文字列への変換と解析は CPU の処理なので、呼び出し元のスレッドを塞がないよう、渡されたディスパッチャーで行う。
        return withContext(dispatcher) {
            val thread = DatParser.parse(String(bytes, boardType.charset))
            // 1 行も dat として読めないときは、違うファイルを選んだと考えて、エラーにする。
            if (thread.posts.any { it is Post.Readable }) {
                Result.success(thread)
            } else {
                Result.failure(IOException("dat として読める行がありません。"))
            }
        }
    }
}
