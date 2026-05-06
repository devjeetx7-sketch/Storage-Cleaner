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

    @Query("SELECT * FROM vault_items WHERE originalName = :name AND size = :size LIMIT 1")
    suspend fun getItemByNameAndSize(name: String, size: Long): VaultEntity?

    @Query("SELECT * FROM vault_items WHERE hash = :hash AND size = :size LIMIT 1")
    suspend fun getItemByHashAndSize(hash: String, size: Long): VaultEntity?

    @Query("UPDATE vault_items SET thumbnailPath = :path WHERE id = :id")
    suspend fun updateThumbnailPath(id: String, path: String)
}
