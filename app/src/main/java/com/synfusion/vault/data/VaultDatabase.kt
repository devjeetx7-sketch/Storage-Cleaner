package com.synfusion.vault.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VaultEntity::class], version = 2, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
