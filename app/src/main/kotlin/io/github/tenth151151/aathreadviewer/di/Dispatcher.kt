package io.github.tenth151151.aathreadviewer.di

import javax.inject.Qualifier

/** 注入するディスパッチャーを、[AatvDispatchers] で選ぶ qualifier。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(
    val dispatcher: AatvDispatchers,
)

/** 注入するディスパッチャーの種類。今は、ブロッキングの I/O 用だけを使う。 */
enum class AatvDispatchers {
    IO,
}
