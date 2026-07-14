package com.chury.camerasentry.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AppStateEntity::class, PhotoUploadEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CameraSentryDatabase : RoomDatabase() {
    abstract fun dao(): CameraSentryDao

    companion object {
        @Volatile private var instance: CameraSentryDatabase? = null

        fun get(context: Context): CameraSentryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CameraSentryDatabase::class.java,
                    "camera_sentry.db"
                ).build().also { instance = it }
            }
    }
}
