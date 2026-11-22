package io.github.tenth151151.aathreadviewer.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import io.github.tenth151151.aathreadviewer.di.AatvDispatchers
import io.github.tenth151151.aathreadviewer.di.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * ContentResolver で Uri を読むデータ源。ストリームを開く失敗は、すべて [IOException] にして投げる。ファイル名が取れないことは失敗にせず、ファイル名を null にする。
 * 取り消しと、ブロックの中で起きた例外は、そのまま投げる。
 * ブロックに渡すストリームは、読んだバイト数が上限を超えると [FileTooLargeException] を投げる。
 * 読み込みの途中で、元のストリームが [IOException] 以外の例外を投げたときは、[IOException] に包んで投げる。
 */
class ContentResolverDatSource
    @Inject
    constructor(
        private val contentResolver: ContentResolver,
        @Dispatcher(AatvDispatchers.IO) private val dispatcher: CoroutineDispatcher,
    ) : DatSource {
        override suspend fun <T> read(
            uri: String,
            block: (stream: InputStream, fileName: String?) -> T,
        ): T =
            withContext(dispatcher) {
                val parsedUri: Uri
                val fileName: String?
                val stream: InputStream
                try {
                    parsedUri = uri.toUri()
                    fileName = queryFileName(parsedUri)
                    stream = contentResolver.openInputStream(parsedUri) ?: throw IOException("Uri を開けません。")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw e
                } catch (e: SecurityException) {
                    throw IOException("Uri を読む権限がありません。", e)
                } catch (e: Exception) {
                    throw IOException("Uri を読めません。", e)
                }
                stream.use { block(LimitedInputStream(it, MAX_BYTES), fileName) }
            }

        private companion object {
            // 読む大きさの上限は 32 MB。これを超えるファイルは、解析の途中でも読むのをやめて [FileTooLargeException] にする。
            // Android 17 のエミュレータ (heap の上限 192 MB) で、上限を上げて測ると、32 MB、48 MB、64 MB の dat はどれも表示できた。
            // 開いた直後の Java Heap は、32 MB で約 65 MB、48 MB で約 88 MB、64 MB で約 120 MB だった。
            // heap の上限は端末ごとに違うので、192 MB より小さい端末でも余裕を残すため、上限は 32 MB にしている。
            const val MAX_BYTES = 32 * 1024 * 1024
        }

        // ファイル名が取れないことは、読み込みの失敗にしない。
        private fun queryFileName(uri: Uri): String? =
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getString(column) else null
                }
            } catch (e: Exception) {
                null
            }
    }
