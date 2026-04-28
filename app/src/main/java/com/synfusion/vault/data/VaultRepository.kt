package com.synfusion.vault.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.synfusion.vault.security.EncryptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
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

    private val thumbDir = File(vaultDir, "thumbnails").apply {
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

    suspend fun importAndEncryptFile(uri: Uri, mediaType: String, folderId: String? = null): Uri? = withContext(Dispatchers.IO) {
        try {
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

            val originalName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
            val originalSize = getFileSize(uri)

            // Duplicate prevention
            val existingItem = vaultDao.getItemByNameAndSize(originalName, originalSize)
            if (existingItem != null) {
                return@withContext uri
            }

            val id = UUID.randomUUID().toString()
            val typeDir = File(vaultDir, mediaType).apply { if (!exists()) mkdirs() }

            // Handle duplicate filenames in filesystem by using UUID
            val encryptedFile = File(typeDir, "$id.enc")
            val thumbnailFile = File(thumbDir, "$id.jpg")

            // 1. Generate thumbnail first (unencrypted for fast loading)
            if (mediaType == "images" || mediaType == "videos") {
                generateThumbnail(uri, mediaType, thumbnailFile)
            }

            // 2. Encrypt and copy
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedInputStream(inputStream).use { bufferedInput ->
                    FileOutputStream(encryptedFile).use { fileOut ->
                        BufferedOutputStream(fileOut).use { bufferedOutput ->
                            encryptionManager.encrypt(bufferedInput, bufferedOutput)
                        }
                    }
                }
            } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

            // 3. Verify success
            if (!encryptedFile.exists() || encryptedFile.length() == 0L) {
                encryptedFile.delete()
                thumbnailFile.delete()
                return@withContext null
            }

            // 4. Save metadata
            val entity = VaultEntity(
                id = id,
                originalName = originalName,
                originalPath = uri.toString(),
                encryptedPath = encryptedFile.absolutePath,
                mediaType = mediaType,
                size = originalSize,
                dateAdded = System.currentTimeMillis(),
                thumbnailPath = if (thumbnailFile.exists()) thumbnailFile.absolutePath else null,
                vaultFolderId = folderId
            )
            vaultDao.insertItem(entity)

            return@withContext uri
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun generateThumbnail(uri: Uri, mediaType: String, outputFile: File) {
        try {
            var bitmap: Bitmap? = null
            if (mediaType == "videos") {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)

                    val targetSize = 256
                    var inSampleSize = 1
                    while (options.outWidth / inSampleSize > targetSize || options.outHeight / inSampleSize > targetSize) {
                        inSampleSize *= 2
                    }

                    options.inJustDecodeBounds = false
                    options.inSampleSize = inSampleSize
                    bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                }
            }

            bitmap?.let {
                FileOutputStream(outputFile).use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun decryptAndExportFile(item: VaultEntity): String? = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(item.encryptedPath)
            if (!encryptedFile.exists()) return@withContext null

            val collection = when (item.mediaType) {
                "images" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "videos" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Files.getContentUri("external")
            }

            val relativePath = when (item.mediaType) {
                "images" -> Environment.DIRECTORY_PICTURES
                "videos" -> Environment.DIRECTORY_MOVIES
                "audio" -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_DOWNLOADS
            }

            val extension = MimeTypeMap.getFileExtensionFromUrl(item.originalName)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalName)
                put(MediaStore.MediaColumns.SIZE, item.size)
                if (mimeType != null) put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val newUri = context.contentResolver.insert(collection, contentValues) ?: return@withContext null

            context.contentResolver.openOutputStream(newUri)?.use { outputStream ->
                BufferedOutputStream(outputStream).use { bufferedOutput ->
                    encryptedFile.inputStream().use { inputStream ->
                        BufferedInputStream(inputStream).use { bufferedInput ->
                            encryptionManager.decrypt(bufferedInput, bufferedOutput)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(newUri, contentValues, null, null)
            }

            // After successful export, delete from vault
            deleteVaultItem(item)

            // Trigger media scanner for older versions
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, newUri))
            }

            return@withContext newUri.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun deleteVaultItem(item: VaultEntity) = withContext(Dispatchers.IO) {
        try {
            File(item.encryptedPath).delete()
            item.thumbnailPath?.let { File(it).delete() }
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
