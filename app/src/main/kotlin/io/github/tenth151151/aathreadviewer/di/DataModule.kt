package io.github.tenth151151.aathreadviewer.di

import android.content.ContentResolver
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.tenth151151.aathreadviewer.data.BoardType
import io.github.tenth151151.aathreadviewer.data.ContentResolverDatSource
import io.github.tenth151151.aathreadviewer.data.DatSource
import io.github.tenth151151.aathreadviewer.data.DefaultThreadRepository
import io.github.tenth151151.aathreadviewer.data.ThreadRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindsThreadRepository(repository: DefaultThreadRepository): ThreadRepository

    @Binds
    abstract fun bindsDatSource(datSource: ContentResolverDatSource): DatSource

    companion object {
        @Provides
        fun providesContentResolver(
            @ApplicationContext context: Context,
        ): ContentResolver = context.contentResolver

        // 今の掲示板の種類は、ぜろちゃんねるプラス系だけ。
        @Provides
        fun providesBoardType(): BoardType = BoardType.ZeroChannelPlus
    }
}
