package com.synfusion.vault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorDao {
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC")
    fun getAllErrors(): Flow<List<ErrorEntity>>

    @Insert
    suspend fun insertError(error: ErrorEntity)

    @Query("DELETE FROM error_logs")
    suspend fun clearAllErrors()
}
