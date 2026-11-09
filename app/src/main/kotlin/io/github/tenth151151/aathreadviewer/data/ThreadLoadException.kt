package io.github.tenth151151.aathreadviewer.data

import java.io.IOException

/** 上限を超える大きさのデータ。[IOException] なので、データ源の失敗として扱われる。 */
class FileTooLargeException(
    message: String,
) : IOException(message)

/** スレッドを読めなかった原因。 */
enum class ThreadLoadFailure {
    /** 開けない。権限がないときを含む。 */
    CannotOpen,

    /** dat として読める行が 1 つもない。 */
    NotDat,

    /** 大きすぎる。 */
    TooLarge,
}

/** [ThreadRepository] が、読めなかった原因を [failure] に持たせて、失敗の [Result] に入れる例外。 */
class ThreadLoadException(
    val failure: ThreadLoadFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
