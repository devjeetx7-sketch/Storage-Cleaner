package com.synfusion.vault.vault

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synfusion.vault.data.VaultEntity
import com.synfusion.vault.data.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.ContentUris
import android.provider.DocumentsContract
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedMediaType = MutableStateFlow("images")
    val selectedMediaType = _selectedMediaType.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _importProgress = MutableStateFlow<String?>(null)
    val importProgress = _importProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val vaultItems: StateFlow<List<VaultEntity>> = combine(_selectedMediaType, _searchQuery) { type, query ->
        Pair(type, query)
    }.flatMapLatest { (type, query) ->
        if (query.isNotBlank()) {
            vaultRepository.searchItems(query)
        } else {
            vaultRepository.getItemsByType(type)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedItems = MutableStateFlow<Set<VaultEntity>>(emptySet())
    val selectedItems = _selectedItems.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMediaType(type: String) {
        _selectedMediaType.value = type
        _selectedItems.value = emptySet()
    }

    fun toggleSelection(item: VaultEntity) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(item)) {
            current.remove(item)
        } else {
            current.add(item)
        }
        _selectedItems.value = current
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            _isProcessing.value = true
            _selectedItems.value.forEach { item ->
                vaultRepository.deleteVaultItem(item)
            }
            clearSelection()
            _isProcessing.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun getMediaStoreUriFromSaf(uri: Uri?, type: String): Uri? {
        if (uri == null) return null
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val id = split[1]
                    val contentUri = when (type) {
                        "images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> null
                    }
                    if (contentUri != null) {
                        return ContentUris.withAppendedId(contentUri, id.toLong())
                    }
                }
            } else if (uri.toString().contains("media/picker")) {
                return null
            }

            val proj = arrayOf(MediaStore.MediaColumns._ID)
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val id = cursor.getLong(idIdx)
                    val contentUri = when (type) {
                        "images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> MediaStore.Files.getContentUri("external")
                    }
                    return ContentUris.withAppendedId(contentUri, id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun importFiles(uris: List<Uri>, mediaType: String, onComplete: (List<Uri>) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true

            val total = uris.size
            val completed = java.util.concurrent.atomic.AtomicInteger(0)
            _importProgress.value = "Importing 0 / $total..."

            val mediaStoreUris = java.util.concurrent.CopyOnWriteArrayList<Uri>()
            val failCount = java.util.concurrent.atomic.AtomicInteger(0)

            // Limit concurrency to prevent OOM / Disk IO thrashing
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            val dispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(3)

            supervisorScope {
                val jobs = uris.map { uri ->
                    async(dispatcher) {
                        val originalUri = vaultRepository.importAndEncryptFile(uri, mediaType, null)
                        if (originalUri != null) {
                            // Check if it was a duplicate (returned original URI but not in vault again)
                            // We only add to deletion list if we are sure it's safely in the vault.
                            // The repository now handles duplicate detection via hash.
                            val mediaStoreUri = getMediaStoreUriFromSaf(originalUri, mediaType) ?: originalUri
                            mediaStoreUris.add(mediaStoreUri)
                        } else {
                            failCount.incrementAndGet()
                        }

                        val currentCompleted = completed.incrementAndGet()
                        _importProgress.value = "Importing $currentCompleted / $total..."
                    }
                }
                jobs.awaitAll()
            }

            if (failCount.get() > 0) {
                _error.value = "Failed to import ${failCount.get()} file(s)."
            }

            _isProcessing.value = false
            _importProgress.value = null
            onComplete(mediaStoreUris.toList())
        }
    }

    fun exportSelectedItems(onComplete: (List<String>) -> Unit) {
         viewModelScope.launch {
            _isProcessing.value = true
            val paths = mutableListOf<String>()
            supervisorScope {
                val jobs = _selectedItems.value.map { item ->
                    async {
                        val path = vaultRepository.decryptAndExportFile(item)
                        if (path != null) {
                            paths.add(path)
                        }
                    }
                }
                jobs.awaitAll()
            }
            clearSelection()
            _isProcessing.value = false
            onComplete(paths)
        }
    }
}
