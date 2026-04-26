package com.synfusion.vault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultEntity(
    @PrimaryKey val id: String,
    val originalName: String,
    val encryptedPath: String,
    val mediaType: String,
    val size: Long,
    val dateAdded: Long,
    val isHidden: Boolean = true
)
