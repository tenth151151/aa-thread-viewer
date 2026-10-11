package io.github.tenth151151.aathreadviewer.dat

object DatLineParser {
    fun parse(line: String): Post {
        val fields = line.split("<>")
        return Post(
            name = fields[0],
            mail = fields[1],
            dateAndId = fields[2],
            body = fields[3],
        )
    }
}
