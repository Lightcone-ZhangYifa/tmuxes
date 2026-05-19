package com.tmuxes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tmuxes.data.model.KnownHostEntity
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category

/**
 * First-version Room database. During development there is no schema
 * compatibility policy: old database files are not migrated.
 */
@Database(
    entities = [KnownHostEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun knownHostDao(): KnownHostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    AppLogger.i(Category.DB) { "db.build → tmuxes_database_v1" }
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "tmuxes_database_v1"
                    )
                        .build()
                        .also {
                            INSTANCE = it
                            AppLogger.i(Category.DB) { "db.build ← tmuxes_database ready" }
                        }
                }
            }
        }
    }
}
