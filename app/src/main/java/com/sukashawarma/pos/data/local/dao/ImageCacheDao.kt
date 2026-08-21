package com.sukashawarma.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sukashawarma.pos.data.local.entity.LocalImageCacheEntity

@Dao
interface ImageCacheDao {
    @Query("SELECT * FROM local_image_cache")
    suspend fun getAll(): List<LocalImageCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalImageCacheEntity)

    @Query("DELETE FROM local_image_cache WHERE remoteUrl = :remoteUrl")
    suspend fun delete(remoteUrl: String)

    /** Baris paling lama didownload dulu (lastAccessedAt cuma diisi sekali saat
     *  download, lihat MenuImageCache.resolve) — dipakai untuk eviction saat kapasitas
     *  local_image_cache penuh, kasus yang nyaris tidak pernah kejadian untuk satu outlet. */
    @Query("SELECT * FROM local_image_cache ORDER BY lastAccessedAt ASC")
    suspend fun allByLeastRecentlyUsed(): List<LocalImageCacheEntity>
}
