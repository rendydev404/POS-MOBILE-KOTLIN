package com.sukashawarma.pos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sukashawarma.pos.data.local.dao.MenuItemDao
import com.sukashawarma.pos.data.local.dao.OrderDao
import com.sukashawarma.pos.data.local.dao.SyncQueueDao
import com.sukashawarma.pos.data.local.entity.LocalMenuItemEntity
import com.sukashawarma.pos.data.local.entity.LocalOrderEntity
import com.sukashawarma.pos.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        LocalOrderEntity::class,
        LocalMenuItemEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
    abstract fun menuItemDao(): MenuItemDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_sukashawarma.db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
