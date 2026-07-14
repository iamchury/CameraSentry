package com.chury.camerasentry.storage

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class PhotoStorage(private val context: Context) {
    private val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun createPhotoFile(capturedAtMillis: Long): File {
        val directory = File(context.getExternalFilesDir(null), "CameraSentry")
        directory.mkdirs()
        return File(directory, "${formatter.format(Date(capturedAtMillis))}.jpg")
    }

    fun preserveLocal(path: String, capturedAtMillis: Long): LocalPhoto {
        val source = File(path)
        require(source.exists()) { context.getString(com.chury.camerasentry.R.string.local_source_photo_missing, path) }
        val date = Instant.ofEpochMilli(capturedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val relativePath = "${Environment.DIRECTORY_PICTURES}/sentry/" +
            "${YEAR_FORMAT.format(date)}/${MONTH_FORMAT.format(date)}/${DAY_FORMAT.format(date)}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException(context.getString(com.chury.camerasentry.R.string.local_media_create_failed))

        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException(context.getString(com.chury.camerasentry.R.string.local_media_open_failed))
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            source.delete()
        }.onFailure { error ->
            resolver.delete(uri, null, null)
            throw error
        }

        return LocalPhoto(
            uri = uri.toString(),
            fileName = source.name,
            displayPath = "$relativePath/${source.name}"
        )
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private companion object {
        val YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM")
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd")
    }
}

data class LocalPhoto(
    val uri: String,
    val fileName: String,
    val displayPath: String
)
