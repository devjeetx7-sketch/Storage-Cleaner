package com.synfusion.vault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "error_logs")
data class ErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val errorCode: String,
    val errorMessage: String,
    val stackTrace: String,
    val timestamp: Long,
    val mediaType: String,
    val operation: String
)
