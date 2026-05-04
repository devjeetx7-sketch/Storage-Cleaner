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
            supervisorScope {
                val jobs = _selectedItems.value.map { item ->
                    async {
                        vaultRepository.deleteVaultItem(item)
                    }
                }
                jobs.awaitAll()
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
            val mediaStoreUris = mutableListOf<Uri>()
            var failCount = 0

            supervisorScope {
                val jobs = uris.map { uri ->
                    async {
                        val originalUri = vaultRepository.importAndEncryptFile(uri, mediaType)
                        if (originalUri != null) {
                            mediaStoreUris.add(getMediaStoreUriFromSaf(originalUri, mediaType) ?: originalUri)
                        } else {
                            failCount++
                        }
                    }
                }
                jobs.awaitAll()
            }

            if (failCount > 0) {
                _error.value = "Failed to import $failCount file(s)."
            }

            _isProcessing.value = false
            onComplete(mediaStoreUris)
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
