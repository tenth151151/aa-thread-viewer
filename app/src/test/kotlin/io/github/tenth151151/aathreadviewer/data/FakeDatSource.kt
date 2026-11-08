package io.github.tenth151151.aathreadviewer.data

import java.io.IOException

/** [bytes] を返すか、[error] を投げるデータ源。 */
class FakeDatSource(
    private val bytes: ByteArray = ByteArray(0),
    private val error: IOException? = null,
) : DatSource {
    override suspend fun read(uri: String): ByteArray = error?.let { throw it } ?: bytes
}
