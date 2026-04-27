package com.synfusion.vault.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VaultEntity::class, ErrorEntity::class], version = 3, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun errorDao(): ErrorDao
}
