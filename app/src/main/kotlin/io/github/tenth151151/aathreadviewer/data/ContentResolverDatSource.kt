package io.github.tenth151151.aathreadviewer.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** ContentResolver で Uri を読むデータ源。失敗は、すべて [IOException] にして投げる。取り消しは、そのまま投げる。 */
class ContentResolverDatSource(
    private val contentResolver: ContentResolver,
) : DatSource {
    override suspend fun read(uri: String): DatFile =
        withContext(Dispatchers.IO) {
            try {
                val parsedUri = uri.toUri()
                val stream = contentResolver.openInputStream(parsedUri) ?: throw IOException("Uri を開けません。")
                val bytes = stream.use { it.readBytesWithLimit(MAX_BYTES) }
                DatFile(bytes, queryFileName(parsedUri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw e
            } catch (e: SecurityException) {
                throw IOException("Uri を読む権限がありません。", e)
            } catch (e: Exception) {
                throw IOException("Uri を読めません。", e)
            }
        }

    private companion object {
        // 読む大きさの上限は 16 MB。これを超えるファイルは、全体を読まずに [FileTooLargeException] にする。
        // Android 17 のエミュレータ (heap の上限 192 MB) で、32 MB の dat は解析の途中でメモリ不足になり、
        // 約 16 MB の dat は表示できた。
        const val MAX_BYTES = 16 * 1024 * 1024
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
