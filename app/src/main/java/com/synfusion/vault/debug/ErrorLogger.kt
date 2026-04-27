package com.synfusion.vault.debug

import com.synfusion.vault.data.ErrorDao
import com.synfusion.vault.data.ErrorEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorLogger @Inject constructor(
    private val errorDao: ErrorDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _latestError = MutableSharedFlow<ErrorEntity>()
    val latestError = _latestError.asSharedFlow()

    fun logError(
        errorCode: String,
        message: String,
        exception: Throwable?,
        mediaType: String = "unknown",
        operation: String = "unknown"
    ) {
        val stackTrace = exception?.stackTraceToString() ?: "No stack trace"
        val errorEntity = ErrorEntity(
            errorCode = errorCode,
            errorMessage = message,
            stackTrace = stackTrace,
            timestamp = System.currentTimeMillis(),
            mediaType = mediaType,
            operation = operation
        )

        scope.launch {
            errorDao.insertError(errorEntity)
            _latestError.emit(errorEntity)
        }
    }

    object Codes {
        const val IMPORT_IMG = "VLT-IMG-001"
        const val PLAY_VID = "VLT-VID-002"
        const val PLAY_AUD = "VLT-AUD-003"
        const val ENCRYPT = "VLT-ENC-004"
        const val DECRYPT = "VLT-DEC-005"
        const val FILE_MISSING = "VLT-FLE-006"
        const val INVALID_URI = "VLT-URI-007"
        const val UNKNOWN = "VLT-UNK-999"
    }
}
