package io.github.tenth151151.aathreadviewer.thread

import io.github.tenth151151.aathreadviewer.R
import io.github.tenth151151.aathreadviewer.data.ThreadLoadFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadErrorMessageTest {
    @Test
    fun `開けない原因には開けないときの文言を返す`() {
        assertEquals(R.string.thread_error_cannot_open, errorMessage(ThreadLoadFailure.CannotOpen))
    }

    @Test
    fun `datとして読めない原因にはdatではないときの文言を返す`() {
        assertEquals(R.string.thread_error_not_dat, errorMessage(ThreadLoadFailure.NotDat))
    }

    @Test
    fun `大きすぎる原因には大きすぎるときの文言を返す`() {
        assertEquals(R.string.thread_error_too_large, errorMessage(ThreadLoadFailure.TooLarge))
    }
}
