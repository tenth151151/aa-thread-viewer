package io.github.tenth151151.aathreadviewer.data

import kotlinx.coroutines.CompletableDeferred

/** [result] が完了するまで待ってから、その結果を返す Repository。呼ばれた Uri は [requestedUris] に残す。 */
class FakeThreadRepository(
    val result: CompletableDeferred<Result<LoadedThread>> = CompletableDeferred(),
) : ThreadRepository {
    val requestedUris = mutableListOf<String>()

    override suspend fun loadThread(uri: String): Result<LoadedThread> {
        requestedUris += uri
        return result.await()
    }
}
