package com.synfusion.vault.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class MockRepository @Inject constructor() {

    data class StorageStats(
        val totalSpace: Long = 128L * 1024 * 1024 * 1024,
        val usedSpace: Long,
        val junkSize: Long,
        val duplicateImagesCount: Int,
        val largeFilesCount: Int,
        val cacheSize: Long
    )

    fun getStorageStats(): Flow<StorageStats> = flow {
        while (true) {
            val usedSpace = Random.nextLong(40L * 1024 * 1024 * 1024, 100L * 1024 * 1024 * 1024)
            val junkSize = Random.nextLong(100L * 1024 * 1024, 2L * 1024 * 1024 * 1024)
            val duplicateImagesCount = Random.nextInt(5, 50)
            val largeFilesCount = Random.nextInt(2, 20)
            val cacheSize = Random.nextLong(50L * 1024 * 1024, 500L * 1024 * 1024)

            emit(StorageStats(
                usedSpace = usedSpace,
                junkSize = junkSize,
                duplicateImagesCount = duplicateImagesCount,
                largeFilesCount = largeFilesCount,
                cacheSize = cacheSize
            ))
            delay(5000) // Refresh every 5 seconds
        }
    }

    suspend fun simulateClean(): Long {
        delay(3000) // Simulate cleaning delay
        return Random.nextLong(100L * 1024 * 1024, 2L * 1024 * 1024 * 1024) // Returns freed size
    }
}
