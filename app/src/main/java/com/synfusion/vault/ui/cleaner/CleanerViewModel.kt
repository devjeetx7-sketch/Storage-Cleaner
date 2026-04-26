package com.synfusion.vault.ui.cleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synfusion.vault.data.MockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CleanerViewModel @Inject constructor(
    private val mockRepository: MockRepository
) : ViewModel() {

    private val _storageStats = MutableStateFlow(MockRepository.StorageStats(0, 0, 0, 0, 0, 0))
    val storageStats: StateFlow<MockRepository.StorageStats> = _storageStats.asStateFlow()

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning: StateFlow<Boolean> = _isCleaning.asStateFlow()

    private val _freedSpace = MutableStateFlow(0L)
    val freedSpace: StateFlow<Long> = _freedSpace.asStateFlow()

    init {
        viewModelScope.launch {
            mockRepository.getStorageStats()
                .catch { e -> e.printStackTrace() }
                .collect { stats ->
                    if (!_isCleaning.value) {
                        _storageStats.value = stats
                    }
                }
        }
    }

    fun startCleaning() {
        if (_isCleaning.value) return

        viewModelScope.launch {
            _isCleaning.value = true
            _freedSpace.value = 0L
            val freed = mockRepository.simulateClean()
            _freedSpace.value = freed
            _isCleaning.value = false

            // Adjust stats after fake cleaning
            _storageStats.value = _storageStats.value.copy(
                usedSpace = _storageStats.value.usedSpace - freed,
                junkSize = 0,
                cacheSize = 0
            )
        }
    }
}
