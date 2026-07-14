package com.chury.camerasentry.database

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)

    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name
}
