package io.github.tenth151151.aathreadviewer.home

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeKeyTest {
    @Test
    fun `ホーム画面のキーはシリアライズして元に戻せる`() {
        val json = Json.encodeToString(HomeKey.serializer(), HomeKey)

        assertEquals(HomeKey, Json.decodeFromString(HomeKey.serializer(), json))
    }
}
