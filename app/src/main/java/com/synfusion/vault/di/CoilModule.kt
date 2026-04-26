package com.synfusion.vault.di

import android.content.Context
import coil.ImageLoader
import com.synfusion.vault.data.VaultEntity
import com.synfusion.vault.media.EncryptedMediaFetcherFactory
import com.synfusion.vault.media.VaultEntityKeyer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        fetcherFactory: EncryptedMediaFetcherFactory
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(fetcherFactory)
                add(VaultEntityKeyer())
            }
            .build()
    }
}
