package com.synfusion.vault.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synfusion.vault.data.VaultEntity
import com.synfusion.vault.data.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedMediaType = MutableStateFlow("images")
    val selectedMediaType = _selectedMediaType.asStateFlow()

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
            _selectedItems.value.forEach { item ->
                vaultRepository.deleteVaultItem(item)
            }
            clearSelection()
        }
    }

    fun importFiles(uris: List<Uri>, mediaType: String, onComplete: (List<Uri>) -> Unit) {
        viewModelScope.launch {
            val originalUris = mutableListOf<Uri>()
            uris.forEach { uri ->
                val originalUri = vaultRepository.importAndEncryptFile(uri, mediaType)
                originalUris.add(originalUri)
            }
            onComplete(originalUris)
        }
    }

    fun exportSelectedItems(destDir: File, onComplete: (List<String>) -> Unit) {
         viewModelScope.launch {
            val paths = mutableListOf<String>()
            _selectedItems.value.forEach { item ->
                val path = vaultRepository.decryptAndExportFile(item, destDir)
                paths.add(path)
            }
            clearSelection()
            onComplete(paths)
        }
    }
}
