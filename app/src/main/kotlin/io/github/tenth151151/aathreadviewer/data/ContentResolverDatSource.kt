package io.github.tenth151151.aathreadviewer.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * ContentResolver で Uri を読むデータ源。ストリームを開く失敗は、すべて [IOException] にして投げる。ファイル名が取れないことは失敗にせず、ファイル名を null にする。
 * 取り消しと、ブロックの中で起きた例外は、そのまま投げる。
 * ブロックに渡すストリームは、読んだバイト数が上限を超えると [FileTooLargeException] を投げる。
 * 読み込みの途中で、元のストリームが [IOException] 以外の例外を投げたときは、[IOException] に包んで投げる。
 */
class ContentResolverDatSource(
    private val contentResolver: ContentResolver,
) : DatSource {
    override suspend fun <T> read(
        uri: String,
        block: (stream: InputStream, fileName: String?) -> T,
    ): T =
        withContext(Dispatchers.IO) {
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
        // 読む大きさの上限は 16 MB。これを超えるファイルは、解析の途中でも読むのをやめて [FileTooLargeException] にする。
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
