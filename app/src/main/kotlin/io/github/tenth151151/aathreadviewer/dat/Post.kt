package io.github.tenth151151.aathreadviewer.dat

sealed interface Post {
    val number: Int

    data class Readable(
        override val number: Int,
        val name: String,
        val mail: String,
        val dateAndId: String,
        val body: String,
    ) : Post

    data class Unreadable(
        override val number: Int,
    ) : Post
}
