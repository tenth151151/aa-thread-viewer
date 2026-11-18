package io.github.tenth151151.aathreadviewer.data

import io.github.tenth151151.aathreadviewer.dat.DatParser
import io.github.tenth151151.aathreadviewer.dat.LineTooLongException
import io.github.tenth151151.aathreadviewer.dat.Post
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStreamReader

class DefaultThreadRepository(
    private val datSource: DatSource,
    private val boardType: BoardType,
    private val dispatcher: CoroutineDispatcher,
) : ThreadRepository {
    // runCatching は CancellationException も捕まえてしまうので、IOException だけを捕まえる。
    // 1 行が長すぎるときは、dat として読めないので、NotDat にする。
    // 解析は、データ源の read に渡すブロックの中で動く。本番のデータ源は、ブロックを自分のディスパッチャーの中で呼ぶので、解析はそのスレッドで動く。
    // ここで渡されたディスパッチャーに切り替えるのは、データ源の実装が切り替えるかどうかに頼らず、この関数自身を main-safe にするため。
    override suspend fun loadThread(uri: String): Result<LoadedThread> =
        try {
            withContext(dispatcher) {
                datSource.read(uri) { stream, fileName ->
                    // 1 行ずつ、文字コードを変換しながら読んで解析する。変換できないバイトは、置き換え文字になる。
                    val thread =
                        try {
                            DatParser.parse(InputStreamReader(stream, boardType.charset))
                        } catch (e: LineTooLongException) {
                            return@read Result.failure(ThreadLoadException(ThreadLoadFailure.NotDat, "1 行が長すぎます。", e))
                        }
                    // 1 行も dat として読めないときは、違うファイルを選んだと考えて、エラーにする。
                    if (thread.posts.any { it is Post.Readable }) {
                        Result.success(LoadedThread(thread, fileName))
                    } else {
                        Result.failure(ThreadLoadException(ThreadLoadFailure.NotDat, "dat として読める行がありません。"))
                    }
                }
            }
        } catch (e: IOException) {
            val failure = if (e is FileTooLargeException) ThreadLoadFailure.TooLarge else ThreadLoadFailure.CannotOpen
            Result.failure(ThreadLoadException(failure, "dat を読めません。", e))
        }
}
