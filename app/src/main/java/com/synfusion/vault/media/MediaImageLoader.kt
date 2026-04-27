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
import java.io.ByteArrayOutputStream
import java.io.File
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
        if (!file.exists()) return null

        return withContext(Dispatchers.IO) {
            val outputStream = ByteArrayOutputStream()
            file.inputStream().use { input ->
                encryptionManager.decrypt(input, outputStream)
            }
            val decryptedBytes = outputStream.toByteArray()

            val bitmap = if (entity.mediaType == "videos") {
                val tempFile = File.createTempFile("temp_vid", ".mp4", options.context.cacheDir)
                tempFile.writeBytes(decryptedBytes)
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(tempFile.absolutePath)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                tempFile.delete()
                frame
            } else {
                BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
            }

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

class VaultEntityKeyer : Keyer<VaultEntity> {
    override fun key(data: VaultEntity, options: Options): String = data.id
}
