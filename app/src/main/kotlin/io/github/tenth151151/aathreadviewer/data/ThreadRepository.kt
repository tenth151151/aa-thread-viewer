package io.github.tenth151151.aathreadviewer.data

import io.github.tenth151151.aathreadviewer.dat.DatThread

interface ThreadRepository {
    /** [uri] の dat を読んでスレッドにする。読めないときは失敗の [Result] を返す。 */
    suspend fun loadThread(uri: String): Result<DatThread>
}
