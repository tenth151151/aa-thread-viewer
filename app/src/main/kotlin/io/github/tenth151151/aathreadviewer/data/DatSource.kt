package io.github.tenth151151.aathreadviewer.data

import java.io.IOException
import java.io.InputStream

/**
 * dat のストリームとファイル名を渡すデータ源。
 * スレッドを塞ぐ実装は、[read] を main-safe にする (受け取ったディスパッチャーで切り替える)。
 */
interface DatSource {
    /**
     * [uri] を開き、ストリームとファイル名を [block] に渡して、[block] の結果を返す。ストリームは、[block] が終わったら閉じる。
     * ファイル名は、取れないときは null。取れないことは、失敗にしない。
     * 開けないときは [IOException] を投げる。[block] の中で起きた例外は、そのまま投げる。
     */
    suspend fun <T> read(
        uri: String,
        block: (stream: InputStream, fileName: String?) -> T,
    ): T
}
