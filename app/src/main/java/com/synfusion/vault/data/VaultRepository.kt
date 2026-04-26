package com.synfusion.vault.data

import android.content.Context
import android.net.Uri
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

    private val vaultDir = File(context.filesDir, ".core_system").apply {
        if (!exists()) mkdirs()
    }

    fun getAllItems(): Flow<List<VaultEntity>> = vaultDao.getAllItems()

    fun getItemsByType(type: String): Flow<List<VaultEntity>> = vaultDao.getItemsByType(type)

    fun searchItems(query: String): Flow<List<VaultEntity>> = vaultDao.searchItems(query)

    suspend fun importAndEncryptFile(uri: Uri, mediaType: String) = withContext(Dispatchers.IO) {
        val originalName = getFileName(uri) ?: "unknown_file"
        val id = UUID.randomUUID().toString()
        val encryptedFileName = "$id.enc"

        val typeDir = File(vaultDir, mediaType).apply {
            if (!exists()) mkdirs()
        }

        val encryptedFile = File(typeDir, encryptedFileName)

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(encryptedFile).use { outputStream ->
                encryptionManager.encrypt(inputStream, outputStream)
            }
        }

        val entity = VaultEntity(
            id = id,
            originalName = originalName,
            encryptedPath = encryptedFile.absolutePath,
            mediaType = mediaType,
            size = encryptedFile.length(),
            dateAdded = System.currentTimeMillis()
        )

        vaultDao.insertItem(entity)

        // Return original uri to allow deletion request via MediaStore
        uri
    }

    suspend fun decryptAndExportFile(item: VaultEntity, destDir: File) = withContext(Dispatchers.IO) {
        val encryptedFile = File(item.encryptedPath)
        val decryptedFile = File(destDir, item.originalName)

        encryptedFile.inputStream().use { inputStream ->
            FileOutputStream(decryptedFile).use { outputStream ->
                encryptionManager.decrypt(inputStream, outputStream)
            }
        }

        // Remove from vault
        vaultDao.deleteItem(item)
        encryptedFile.delete()

        decryptedFile.absolutePath
    }

    suspend fun deleteVaultItem(item: VaultEntity) = withContext(Dispatchers.IO) {
        val encryptedFile = File(item.encryptedPath)
        if (encryptedFile.exists()) {
            encryptedFile.delete()
        }
        vaultDao.deleteItem(item)
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result
    }
}
