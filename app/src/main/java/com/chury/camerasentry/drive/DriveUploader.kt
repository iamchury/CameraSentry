package com.chury.camerasentry.drive

import android.content.Context
import com.chury.camerasentry.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.chury.camerasentry.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DriveUploader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val settingsDataStore = SettingsDataStore(context)

    suspend fun upload(file: File, capturedAtMillis: Long): Result<Unit> {
        val token = freshAccessToken() ?: settingsDataStore.driveState.first().accessToken
            ?: return Result.failure(IllegalStateException(context.getString(R.string.drive_not_connected)))
        if (!file.exists()) {
            return Result.failure(IllegalStateException(context.getString(R.string.drive_upload_file_missing, file.name)))
        }

        return runCatching {
            val folderId = ensureCaptureFolder(token, capturedAtMillis)
            val metadata = JSONObject()
                .put("name", file.name)
                .put("mimeType", "image/jpeg")
                .put("parents", JSONArray().put(folderId))
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val media = file.asRequestBody(JPEG_MEDIA_TYPE)
            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata)
                .addPart(media)
                .build()
            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(context.getString(R.string.drive_upload_failed, response.code, response.body?.string().orEmpty()))
                }
            }
        }
    }

    private suspend fun freshAccessToken(): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveAuthorizationManager.DRIVE_FILE_SCOPE)))
            .build()
        return suspendCancellableCoroutine { continuation ->
            Identity.getAuthorizationClient(context)
                .authorize(request)
                .addOnSuccessListener { result ->
                    continuation.resume(if (result.hasResolution()) null else result.accessToken)
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }
    }

    private fun ensureCaptureFolder(token: String, capturedAtMillis: Long): String {
        val date = Instant.ofEpochMilli(capturedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val segments = listOf(
            ROOT_FOLDER_NAME,
            YEAR_FORMAT.format(date),
            MONTH_FORMAT.format(date),
            DAY_FORMAT.format(date)
        )
        var parentId = DRIVE_ROOT_ID
        for (segment in segments) {
            parentId = findFolder(token, parentId, segment)
                ?: createFolder(token, parentId, segment)
        }
        return parentId
    }

    private fun findFolder(token: String, parentId: String, folderName: String): String? {
        val query = "'${escapeDriveQueryValue(parentId)}' in parents and " +
            "name = '${escapeDriveQueryValue(folderName)}' and " +
            "mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("pageSize", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(context.getString(R.string.drive_folder_lookup_failed, response.code, response.body?.string().orEmpty()))
            }
            val files = JSONObject(response.body?.string().orEmpty()).optJSONArray("files")
            return files?.optJSONObject(0)?.optString("id")?.takeIf(String::isNotBlank)
        }
    }

    private fun createFolder(token: String, parentId: String, folderName: String): String {
        val metadata = JSONObject()
            .put("name", folderName)
            .put("mimeType", FOLDER_MIME_TYPE)
            .apply {
                if (parentId != DRIVE_ROOT_ID) {
                    put("parents", JSONArray().put(parentId))
                }
            }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .addHeader("Authorization", "Bearer $token")
            .post(metadata)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(context.getString(R.string.drive_folder_create_failed, folderName, response.code, response.body?.string().orEmpty()))
            }
            return JSONObject(response.body?.string().orEmpty())
                .optString("id")
                .takeIf(String::isNotBlank)
                ?: error(context.getString(R.string.drive_folder_create_id_missing, folderName))
        }
    }

    private fun escapeDriveQueryValue(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private companion object {
        const val ROOT_FOLDER_NAME = "Sentry"
        const val DRIVE_ROOT_ID = "root"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
        val YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM")
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd")
    }
}
