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

    suspend fun importAndEncryptFile(uri: Uri?, mediaType: String): Uri? = withContext(Dispatchers.IO) {
        if (uri == null) {
            return@withContext null
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
        }

        val contentResolver = context.contentResolver
        val mimeType = try {
            contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }

        if (mimeType == null || (!mimeType.startsWith("image/") && !mimeType.startsWith("video/") && !mimeType.startsWith("audio/"))) {
            return@withContext null
        }

        var tempFile: File? = null
        try {
            val originalName = getFileName(uri) ?: UUID.randomUUID().toString()
            val originalPath = uri.toString()
            val id = UUID.randomUUID().toString()
            val encryptedFileName = "$id.enc"

            val typeDir = File(vaultDir, mediaType).apply {
                if (!exists()) mkdirs()
            }

            tempFile = File(context.cacheDir, "$id.tmp")

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 8192)
                }
            } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

            val encryptedFile = File(typeDir, encryptedFileName)

            tempFile.inputStream().use { inputStream ->
                FileOutputStream(encryptedFile).use { outputStream ->
                    encryptionManager.encrypt(inputStream, outputStream)
                }
            }

            val entity = VaultEntity(
                id = id,
                originalName = originalName,
                originalPath = originalPath,
                encryptedPath = encryptedFile.absolutePath,
                mediaType = mediaType,
                size = encryptedFile.length(),
                dateAdded = System.currentTimeMillis()
            )

            vaultDao.insertItem(entity)
            return@withContext uri
        } catch (e: Exception) {
            return@withContext null
        } finally {
            try {
                tempFile?.delete()
            } catch (e: Exception) {
            }
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
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }

            val relativePath = when(item.mediaType) {
                "images" -> Environment.DIRECTORY_PICTURES
                "videos" -> Environment.DIRECTORY_MOVIES
                "audio" -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_DOWNLOADS
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalName)
                put(MediaStore.MediaColumns.SIZE, item.size)
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

            vaultDao.deleteItem(item)
            encryptedFile.delete()

            return@withContext newUri.toString()
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun deleteVaultItem(item: VaultEntity) = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(item.encryptedPath)
            if (encryptedFile.exists()) {
                encryptedFile.delete()
            }
            vaultDao.deleteItem(item)
        } catch (e: Exception) {
            // Log ignored
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            return cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
        return null
    }
}
