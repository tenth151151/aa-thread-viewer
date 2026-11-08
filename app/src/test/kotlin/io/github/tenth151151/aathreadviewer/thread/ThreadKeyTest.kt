package io.github.tenth151151.aathreadviewer.thread

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadKeyTest {
    @Test
    fun `画面のキーはエスケープを含むUriの文字列をそのまま保ってシリアライズできる`() {
        val key = ThreadKey("content://com.android.providers.downloads.documents/document/msf%3A1000%E3%81%82")

        val json = Json.encodeToString(ThreadKey.serializer(), key)

        assertEquals(key, Json.decodeFromString(ThreadKey.serializer(), json))
    }
}
