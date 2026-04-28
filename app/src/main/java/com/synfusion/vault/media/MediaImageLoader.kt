package com.synfusion.vault.media

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import com.synfusion.vault.data.VaultEntity
import com.synfusion.vault.security.EncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedMediaFetcherFactory @Inject constructor(
    private val encryptionManager: EncryptionManager
) : Fetcher.Factory<VaultEntity> {

    override fun create(data: VaultEntity, options: Options, imageLoader: ImageLoader): Fetcher {
        return EncryptedMediaFetcher(data, options)
    }
}

class EncryptedMediaFetcher(
    private val entity: VaultEntity,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // Optimization: Use permanent thumbnail path for instant loading
        val thumbPath = entity.thumbnailPath
        if (thumbPath != null) {
            val thumbFile = File(thumbPath)
            if (thumbFile.exists()) {
                return withContext(Dispatchers.IO) {
                    val bitmap = BitmapFactory.decodeFile(thumbPath)
                    if (bitmap != null) {
                        DrawableResult(
                            drawable = BitmapDrawable(options.context.resources, bitmap),
                            isSampled = false,
                            dataSource = DataSource.DISK
                        )
                    } else null
                }
            }
        }

        // Fallback for audio or missing thumbnails
        return null
    }
}

class VaultEntityKeyer : Keyer<VaultEntity> {
    override fun key(data: VaultEntity, options: Options): String = data.id
}
