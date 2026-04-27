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
import com.synfusion.vault.debug.ErrorLogger
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
    private val encryptionManager: EncryptionManager,
    private val errorLogger: ErrorLogger
) : Fetcher.Factory<VaultEntity> {

    override fun create(data: VaultEntity, options: Options, imageLoader: ImageLoader): Fetcher {
        return EncryptedMediaFetcher(data, options, encryptionManager, errorLogger)
    }
}

class EncryptedMediaFetcher(
    private val entity: VaultEntity,
    private val options: Options,
    private val encryptionManager: EncryptionManager,
    private val errorLogger: ErrorLogger
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val file = File(entity.encryptedPath)
        if (!file.exists()) {
            errorLogger.logError(ErrorLogger.Codes.FILE_MISSING, "File missing during fetch", null, entity.mediaType, "fetch_image")
            return null
        }

        return try {
            withContext(Dispatchers.IO) {
                var tempFile: File? = null
                try {
                    val bitmap = if (entity.mediaType == "videos") {
                        tempFile = File.createTempFile("temp_vid_thumb", ".mp4", options.context.cacheDir)
                        file.inputStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                encryptionManager.decrypt(input, output)
                            }
                        }

                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(tempFile.absolutePath)
                            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } catch (e: Exception) {
                            errorLogger.logError(ErrorLogger.Codes.PLAY_VID, "Failed to extract thumbnail", e, "videos", "fetch_thumb")
                            null
                        } finally {
                            retriever.release()
                        }
                    } else {
                        // Use a temp file for images too to avoid OOM with large image byte arrays
                        tempFile = File.createTempFile("temp_img", ".jpg", options.context.cacheDir)
                        file.inputStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                encryptionManager.decrypt(input, output)
                            }
                        }
                        BitmapFactory.decodeFile(tempFile.absolutePath)
                    }

                    if (bitmap != null) {
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
            errorLogger.logError(ErrorLogger.Codes.IMPORT_IMG, "Failed to decode media", e, entity.mediaType, "fetch")
            null
        }
    }
}

class VaultEntityKeyer : Keyer<VaultEntity> {
    override fun key(data: VaultEntity, options: Options): String = data.id
}
