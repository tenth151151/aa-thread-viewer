package io.github.tenth151151.aathreadviewer.data

interface ThreadRepository {
    /** [uri] の dat を読んでスレッドにする。読めないときは失敗の [Result] を返す。 */
    suspend fun loadThread(uri: String): Result<LoadedThread>
}
