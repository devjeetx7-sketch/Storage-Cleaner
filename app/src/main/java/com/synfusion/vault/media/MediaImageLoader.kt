package com.synfusion.vault.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
import com.synfusion.vault.data.VaultEntity
import com.synfusion.vault.security.EncryptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import coil.decode.ImageSource
import okio.buffer
import okio.source
import coil.fetch.SourceResult
import coil.key.Keyer
import android.graphics.drawable.BitmapDrawable

@Singleton
class EncryptedMediaFetcherFactory @Inject constructor(
    private val encryptionManager: EncryptionManager
) : Fetcher.Factory<VaultEntity> {

    override fun create(data: VaultEntity, options: Options, imageLoader: ImageLoader): Fetcher {
        return EncryptedMediaFetcher(data, options, encryptionManager)
    }
}

class EncryptedMediaFetcher(
    private val entity: VaultEntity,
    private val options: Options,
    private val encryptionManager: EncryptionManager
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val file = File(entity.encryptedPath)
        if (!file.exists()) {
            return null
        }

        return try {
            withContext(Dispatchers.IO) {
                val cachedThumb = File(options.context.cacheDir, "thumb_cache_${entity.id}.jpg")
                if (cachedThumb.exists()) {
                    val bitmap = BitmapFactory.decodeFile(cachedThumb.absolutePath)
                    if (bitmap != null) {
                        return@withContext DrawableResult(
                            drawable = BitmapDrawable(options.context.resources, bitmap),
                            isSampled = false,
                            dataSource = DataSource.DISK
                        )
                    }
                }

                var tempFile: File? = null
                try {
                    val bitmap = if (entity.mediaType == "videos") {
                        val tFile = File(options.context.cacheDir, "dec_${entity.id}.tmp")
                        tempFile = tFile
                        file.inputStream().use { input ->
                            FileOutputStream(tFile).use { output ->
                                encryptionManager.decrypt(input, output)
                            }
                        }

                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(tFile.absolutePath)
                            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } catch (e: Exception) {
                            null
                        } finally {
                            retriever.release()
                        }
                    } else {
                        val tFile = File(options.context.cacheDir, "dec_${entity.id}.tmp")
                        tempFile = tFile
                        file.inputStream().use { input ->
                            FileOutputStream(tFile).use { output ->
                                encryptionManager.decrypt(input, output)
                            }
                        }
                        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(tFile.absolutePath, boundsOptions)

                        val outWidth = boundsOptions.outWidth
                        val outHeight = boundsOptions.outHeight
                        var inSampleSize = 1
                        val targetWidth = 300
                        val targetHeight = 300

                        if (outHeight > targetHeight || outWidth > targetWidth) {
                            val halfHeight: Int = outHeight / 2
                            val halfWidth: Int = outWidth / 2
                            while (halfHeight / inSampleSize >= targetHeight && halfWidth / inSampleSize >= targetWidth) {
                                inSampleSize *= 2
                            }
                        }

                        val decodeOptions = BitmapFactory.Options().apply {
                            this.inSampleSize = inSampleSize
                        }
                        BitmapFactory.decodeFile(tFile.absolutePath, decodeOptions)
                    }

                    if (bitmap != null) {
                        // Cache the thumbnail
                        if (!cachedThumb.exists()) {
                            try {
                                FileOutputStream(cachedThumb).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        DrawableResult(
                            drawable = BitmapDrawable(options.context.resources, bitmap),
                            isSampled = false,
                            dataSource = DataSource.DISK
                        )
                    } else null
                } finally {
                    tempFile?.delete()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

class VaultEntityKeyer : Keyer<VaultEntity> {
    override fun key(data: VaultEntity, options: Options): String = data.id
}
