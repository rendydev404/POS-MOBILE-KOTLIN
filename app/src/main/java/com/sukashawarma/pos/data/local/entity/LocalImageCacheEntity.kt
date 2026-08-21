package com.sukashawarma.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Indeks foto (menu, dst) yang sudah didownload permanen ke [Context.getFilesDir]
 * (BUKAN cacheDir — cacheDir boleh dibuang OS kapan saja saat storage menipis,
 * itulah kenapa foto yang "kemarin sudah kebuka" bisa hilang lagi keesokan
 * harinya walau tidak pernah di-logout). Satu baris = satu file remote yang
 * sudah tersimpan lokal secara permanen sampai sengaja dievict (menu berubah
 * foto, atau kapasitas [MenuImageCache] penuh dan baris ini yang paling lama
 * tidak dipakai).
 */
@Entity(tableName = "local_image_cache")
data class LocalImageCacheEntity(
    @PrimaryKey val remoteUrl: String,
    val localPath: String,
    val sizeBytes: Long,
    val lastAccessedAt: Long
)
