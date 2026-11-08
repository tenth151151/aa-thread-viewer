package io.github.tenth151151.aathreadviewer

import android.app.Application

class AatvApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
