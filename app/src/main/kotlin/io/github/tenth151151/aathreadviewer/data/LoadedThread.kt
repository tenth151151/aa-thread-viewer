package io.github.tenth151151.aathreadviewer.data

import io.github.tenth151151.aathreadviewer.dat.DatThread

/** 読み込んだスレッド。[fileName] は、dat のファイル名で、取れないときは null。 */
data class LoadedThread(
    val thread: DatThread,
    val fileName: String?,
)
