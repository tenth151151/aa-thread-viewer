package io.github.tenth151151.aathreadviewer.data

import java.io.IOException

/** dat のバイト列とファイル名を返すデータ源。 */
interface DatSource {
    /** [uri] の中身とファイル名を返す。読めないときは [IOException] を投げる。ファイル名が取れないことは、失敗にしない。 */
    suspend fun read(uri: String): DatFile
}
