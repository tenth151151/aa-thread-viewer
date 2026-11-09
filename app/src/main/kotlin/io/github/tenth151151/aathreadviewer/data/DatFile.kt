package io.github.tenth151151.aathreadviewer.data

/** データ源が読んだ dat。[fileName] は、ファイル名が取れないときは null。 */
class DatFile(
    val bytes: ByteArray,
    val fileName: String?,
)
