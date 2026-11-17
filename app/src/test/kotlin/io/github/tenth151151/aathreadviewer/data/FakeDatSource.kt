package io.github.tenth151151.aathreadviewer.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * [bytes] から作ったストリームと [fileName] をブロックに渡すか、開くときに [error] を投げるデータ源。
 * [streamFactory] があるときは、[bytes] の代わりに、読むたびに作るそのストリームを渡す。読んでいる途中で失敗するストリームを試すために使う。
 */
class FakeDatSource(
    private val bytes: ByteArray = ByteArray(0),
    private val error: IOException? = null,
    private val fileName: String? = null,
    private val streamFactory: (() -> InputStream)? = null,
) : DatSource {
    override suspend fun <T> read(
        uri: String,
        block: (stream: InputStream, fileName: String?) -> T,
    ): T {
        error?.let { throw it }
        val stream = streamFactory?.invoke() ?: ByteArrayInputStream(bytes)
        return stream.use { block(it, fileName) }
    }
}
