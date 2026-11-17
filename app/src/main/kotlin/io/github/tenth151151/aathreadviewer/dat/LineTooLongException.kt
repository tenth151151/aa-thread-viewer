package io.github.tenth151151.aathreadviewer.dat

/** 1 行が上限を超えて長く、dat として読めないときの例外。[java.io.IOException] ではない。 */
class LineTooLongException(
    message: String,
) : RuntimeException(message)
