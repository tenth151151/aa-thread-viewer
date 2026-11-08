package io.github.tenth151151.aathreadviewer.data

import java.nio.charset.Charset

/** 掲示板の種類。dat の文字コードは、種類で決まる。 */
enum class BoardType(
    val charset: Charset,
) {
    ZeroChannelPlus(Charset.forName("windows-31j")),
}
