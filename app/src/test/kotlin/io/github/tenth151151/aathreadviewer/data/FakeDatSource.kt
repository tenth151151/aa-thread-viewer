package io.github.tenth151151.aathreadviewer.data

import java.io.IOException

/** [bytes] と [fileName] を返すか、[error] を投げるデータ源。 */
class FakeDatSource(
    private val bytes: ByteArray = ByteArray(0),
    private val error: IOException? = null,
    private val fileName: String? = null,
) : DatSource {
    override suspend fun read(uri: String): DatFile = error?.let { throw it } ?: DatFile(bytes, fileName)
}
