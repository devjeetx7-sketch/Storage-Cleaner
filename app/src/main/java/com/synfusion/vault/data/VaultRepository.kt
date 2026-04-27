package com.synfusion.vault.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.synfusion.vault.security.EncryptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultDao: VaultDao,
    private val encryptionManager: EncryptionManager
) {

    private val vaultDir = File(context.getExternalFilesDir(null), "vault").apply {
        if (!exists()) mkdirs()
    }

    fun getAllItems(): Flow<List<VaultEntity>> = vaultDao.getAllItems()

    fun getItemsByType(type: String): Flow<List<VaultEntity>> = vaultDao.getItemsByType(type)

    fun searchItems(query: String): Flow<List<VaultEntity>> = vaultDao.searchItems(query)

    private fun getFileSize(uri: Uri): Long {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index != -1 && !cursor.isNull(index)) {
                        return cursor.getLong(index)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }

    suspend fun importAndEncryptFile(uri: Uri?, mediaType: String): Uri? = withContext(Dispatchers.IO) {
        if (uri == null) {
            return@withContext null
        }

        try {
            if (uri.scheme == "content") {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (e: SecurityException) {
            // Ignore if provider doesn't support persistable permissions
        }

        val contentResolver = context.contentResolver
        val mimeType = try {
            contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }

        if (mimeType != null && !mimeType.startsWith("image/") && !mimeType.startsWith("video/") && !mimeType.startsWith("audio/")) {
            return@withContext null
        }

        try {
            val originalName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
            val originalSize = getFileSize(uri)

            // Duplicate prevention
            val existingItem = vaultDao.getItemByNameAndSize(originalName, originalSize)
            if (existingItem != null) {
                return@withContext uri // Return original URI so the original can be removed if user selected to delete
            }

            val originalPath = uri.toString()
            val id = UUID.randomUUID().toString()
            val encryptedFileName = "$id.enc"

            val typeDir = File(vaultDir, mediaType).apply {
                if (!exists()) mkdirs()
            }

            val encryptedFile = File(typeDir, encryptedFileName)

            // Generate thumbnail instantly from original URI before encrypting
            if (mediaType == "images" || mediaType == "videos") {
                try {
                    val cachedThumb = File(context.cacheDir, "thumb_cache_${id}.jpg")
                    var bitmap: android.graphics.Bitmap? = null

                    if (mediaType == "videos") {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, uri)
                            bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } catch (e: Exception) {
                            // Ignore
                        } finally {
                            retriever.release()
                        }
                    } else {
                        val pfd = contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            val boundsOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, boundsOptions)

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

                            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                                this.inSampleSize = inSampleSize
                            }
                            bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, decodeOptions)
                            pfd.close()
                        }
                    }

                    if (bitmap != null) {
                        FileOutputStream(cachedThumb).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(encryptedFile).use { outputStream ->
                    encryptionManager.encrypt(inputStream, outputStream)
                }
            } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

            val entity = VaultEntity(
                id = id,
                originalName = originalName,
                originalPath = originalPath,
                encryptedPath = encryptedFile.absolutePath,
                mediaType = mediaType,
                size = originalSize,
                dateAdded = System.currentTimeMillis()
            )

            vaultDao.insertItem(entity)

            return@withContext uri
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun decryptAndExportFile(item: VaultEntity): String? = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(item.encryptedPath)
            if (!encryptedFile.exists()) {
                throw java.io.FileNotFoundException("Encrypted file missing: ${item.encryptedPath}")
            }

            val collection = when(item.mediaType) {
                "images" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "videos" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Files.getContentUri("external")
            }

            val relativePath = when(item.mediaType) {
                "images" -> Environment.DIRECTORY_PICTURES
                "videos" -> Environment.DIRECTORY_MOVIES
                "audio" -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_DOWNLOADS
            }

            val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(item.originalName)
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalName)
                put(MediaStore.MediaColumns.SIZE, item.size)
                if (mimeType != null) {
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val newUri = context.contentResolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore record")

            context.contentResolver.openOutputStream(newUri)?.use { outputStream ->
                encryptedFile.inputStream().use { inputStream ->
                    encryptionManager.decrypt(inputStream, outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(newUri, contentValues, null, null)
            }

            deleteVaultItem(item)

            return@withContext newUri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun deleteVaultItem(item: VaultEntity) = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(item.encryptedPath)
            if (encryptedFile.exists()) {
                encryptedFile.delete()
            }

            // Delete cached thumbnail if it exists
            val cachedThumb = File(context.cacheDir, "thumb_cache_${item.id}.jpg")
            if (cachedThumb.exists()) {
                cachedThumb.delete()
            }

            vaultDao.deleteItem(item)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1 && !cursor.isNull(index)) {
                            return cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return null
    }
}
