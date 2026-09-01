package com.lexis.words.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BlockEntity::class, WordListEntity::class, WordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao
    abstract fun wordListDao(): WordListDao
    abstract fun wordDao(): WordDao

    companion object {
        const val DB_NAME = "lexis.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .build().also { instance = it }
            }

        /** Closes and clears the singleton so a freshly-restored DB file can be reopened. */
        fun reset(context: Context) {
            synchronized(this) {
                instance?.close()
                instance = null
                context.deleteDatabase(DB_NAME)
            }
        }
    }
}
