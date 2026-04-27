package com.synfusion.vault.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synfusion.vault.data.ErrorDao
import com.synfusion.vault.data.ErrorEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val errorDao: ErrorDao,
    val errorLogger: ErrorLogger
) : ViewModel() {

    val errors: StateFlow<List<ErrorEntity>> = errorDao.getAllErrors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearErrors() {
        viewModelScope.launch {
            errorDao.clearAllErrors()
        }
    }
}
