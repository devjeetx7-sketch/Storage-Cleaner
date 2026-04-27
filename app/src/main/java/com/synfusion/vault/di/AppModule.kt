package com.synfusion.vault.di

import android.content.Context
import androidx.room.Room
import com.synfusion.vault.data.VaultDao
import com.synfusion.vault.data.ErrorDao
import com.synfusion.vault.data.VaultDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase {
        return Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideVaultDao(database: VaultDatabase): VaultDao {
        return database.vaultDao()
    }

    @Provides
    fun provideErrorDao(database: VaultDatabase): ErrorDao {
        return database.errorDao()
    }
}
