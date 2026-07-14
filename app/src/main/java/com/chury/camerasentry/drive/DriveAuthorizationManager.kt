package com.chury.camerasentry.drive

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import com.chury.camerasentry.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

class DriveAuthorizationManager(private val activity: Activity) {
    private val client = Identity.getAuthorizationClient(activity)
    private val scopes = listOf(Scope(DRIVE_FILE_SCOPE))

    fun requestAuthorization(
        onResolutionRequired: (IntentSenderRequest) -> Unit,
        onAuthorized: (AuthorizationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes)
            .build()
        client.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        onError(activity.getString(R.string.drive_authorization_resolution_missing))
                    } else {
                        onResolutionRequired(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    onAuthorized(result)
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to request Drive authorization", error)
                onError(error.toDriveAuthMessage(activity.getString(R.string.drive_authorization_request_failed)))
            }
    }

    fun resultFromIntent(intent: Intent?): Result<AuthorizationResult> =
        runCatching {
            client.getAuthorizationResultFromIntent(intent)
        }.recoverCatching { error ->
            Log.e(TAG, "Failed to read Drive authorization result", error)
            if (error is ApiException) {
                throw IllegalStateException(error.toDriveAuthMessage(activity.getString(R.string.drive_authorization_result_failed)))
            }
            throw error
        }

    private fun Throwable.toDriveAuthMessage(prefix: String): String {
        if (this is ApiException) {
            val statusName = CommonStatusCodes.getStatusCodeString(statusCode)
            return activity.getString(R.string.drive_oauth_setup_hint, prefix, statusCode, statusName)
        }
        return "$prefix ${message.orEmpty()}".trim()
    }

    companion object {
        private const val TAG = "DriveAuthorization"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
