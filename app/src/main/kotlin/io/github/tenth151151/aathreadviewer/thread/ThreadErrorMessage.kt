package io.github.tenth151151.aathreadviewer.thread

import androidx.annotation.StringRes
import io.github.tenth151151.aathreadviewer.R
import io.github.tenth151151.aathreadviewer.data.ThreadLoadFailure

/** 読めなかった原因 [failure] に対応する、画面に出す文字列のリソースの ID。 */
@StringRes
fun errorMessage(failure: ThreadLoadFailure): Int =
    when (failure) {
        ThreadLoadFailure.CannotOpen -> R.string.thread_error_cannot_open
        ThreadLoadFailure.NotDat -> R.string.thread_error_not_dat
        ThreadLoadFailure.TooLarge -> R.string.thread_error_too_large
    }
