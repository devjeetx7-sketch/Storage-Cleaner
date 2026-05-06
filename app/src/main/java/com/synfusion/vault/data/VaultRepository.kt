package com.synfusion.vault.data

import android.content.ContentUris
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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

    private val vaultDir = File(context.filesDir, "vault_media").apply {
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

            val id = UUID.randomUUID().toString()
            val typeDir = File(vaultDir, mediaType).apply { if (!exists()) mkdirs() }

            // Handle duplicate filenames in filesystem by using UUID
            val encryptedFile = File(typeDir, "$id.enc")
            val thumbnailFile = File(thumbDir, "$id.jpg")

            // 1. Encrypt and copy (Lossless bit-for-bit)
            val fileHash = try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                val inputStream = if (pfd != null) {
                    java.io.FileInputStream(pfd.fileDescriptor)
                } else {
                    context.contentResolver.openInputStream(uri)
                }

                inputStream?.use { stream ->
                    BufferedInputStream(stream).use { bufferedInput ->
                        FileOutputStream(encryptedFile).use { fileOut ->
                            BufferedOutputStream(fileOut).use { bufferedOutput ->
                                encryptionManager.encrypt(bufferedInput, bufferedOutput)
                            }
                        }
                    }
                } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")
            } catch (e: Exception) {
                encryptedFile.delete()
                throw e
            }

            // 2. Verify success and integrity
            if (!encryptedFile.exists() || encryptedFile.length() == 0L) {
                encryptedFile.delete()
                return@withContext null
            }

            // 3. Duplicate prevention strictly by hash and size
            val existingByHash = vaultDao.getItemByHashAndSize(fileHash, originalSize)
            if (existingByHash != null) {
                encryptedFile.delete()
                return@withContext uri
            }

            // 4. Save metadata quickly, thumbnailPath is initially null
            val entity = VaultEntity(
                id = id,
                originalName = originalName,
                originalPath = uri.toString(),
                encryptedPath = encryptedFile.absolutePath,
                mediaType = mediaType,
                size = originalSize,
                hash = fileHash,
                dateAdded = System.currentTimeMillis(),
                thumbnailPath = null,
                vaultFolderId = folderId
            )
            vaultDao.insertItem(entity)

            // 5. Generate thumbnail asynchronously
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    generateThumbnail(uri, mediaType, thumbnailFile)
                    if (thumbnailFile.exists()) {
                        vaultDao.updateThumbnailPath(id, thumbnailFile.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return@withContext uri
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun generateThumbnail(uri: Uri, mediaType: String, outputFile: File) {
        try {
            var bitmap: Bitmap? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    bitmap = context.contentResolver.loadThumbnail(uri, android.util.Size(640, 640), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (bitmap == null) {
                if (mediaType == "videos") {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                } else if (mediaType == "audio") {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                        }
                    } finally {
                        retriever.release()
                    }
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)

                        val targetSize = 640
                        var inSampleSize = 1
                        while (options.outWidth / inSampleSize > targetSize || options.outHeight / inSampleSize > targetSize) {
                            inSampleSize *= 2
                        }

                        options.inJustDecodeBounds = false
                        options.inSampleSize = inSampleSize
                        bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                    }
                }
            }

            bitmap?.let {
                FileOutputStream(outputFile).use { out ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        it.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                    } else {
                        @Suppress("DEPRECATION")
                        it.compress(Bitmap.CompressFormat.WEBP, 100, out)
                    }
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

            var exportName = item.originalName

            // Duplicate detection at destination (modify filename if needed)
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
            val selectionArgs = arrayOf(exportName, item.size.toString())

            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // File already exists with same name and size, adjust export name to prevent overwrite/skipping
                    val dotIndex = exportName.lastIndexOf('.')
                    exportName = if (dotIndex != -1) {
                        "${exportName.substring(0, dotIndex)}_${System.currentTimeMillis()}${exportName.substring(dotIndex)}"
                    } else {
                        "${exportName}_${System.currentTimeMillis()}"
                    }
                }
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, exportName)
                put(MediaStore.MediaColumns.SIZE, item.size)
                if (mimeType != null) put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val newUri = context.contentResolver.insert(collection, contentValues) ?: return@withContext null

            try {
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
            } catch (e: Exception) {
                // If stream or update fails, delete the corrupted empty media store entry
                context.contentResolver.delete(newUri, null, null)
                throw e
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
