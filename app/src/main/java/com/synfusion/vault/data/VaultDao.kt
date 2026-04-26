package com.synfusion.vault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY dateAdded DESC")
    fun getAllItems(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_items WHERE mediaType = :type ORDER BY dateAdded DESC")
    fun getItemsByType(type: String): Flow<List<VaultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultEntity)

    @Delete
    suspend fun deleteItem(item: VaultEntity)

    @Query("SELECT * FROM vault_items WHERE originalName LIKE '%' || :query || '%'")
    fun searchItems(query: String): Flow<List<VaultEntity>>
}
