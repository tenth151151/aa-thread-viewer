package io.github.tenth151151.aathreadviewer.data

import java.io.IOException

/** dat のバイト列を返すデータ源。 */
interface DatSource {
    /** [uri] の中身をバイト列で返す。読めないときは [IOException] を投げる。 */
    suspend fun read(uri: String): ByteArray
}
