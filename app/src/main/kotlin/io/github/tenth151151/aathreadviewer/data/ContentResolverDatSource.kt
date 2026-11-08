package io.github.tenth151151.aathreadviewer.data

import android.content.ContentResolver
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** ContentResolver で Uri を読むデータ源。失敗は、すべて [IOException] にして投げる。取り消しは、そのまま投げる。 */
class ContentResolverDatSource(
    private val contentResolver: ContentResolver,
) : DatSource {
    override suspend fun read(uri: String): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                val stream = contentResolver.openInputStream(uri.toUri()) ?: throw IOException("Uri を開けません。")
                stream.use { it.readBytes() }
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
}
