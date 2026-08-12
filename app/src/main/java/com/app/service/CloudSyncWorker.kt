package com.app.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.app.data.FinanceRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(applicationContext)?.account
            if (account == null) {
                return@withContext Result.failure()
            }

            // Get access token for Drive
            val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.readonly"
            val token = GoogleAuthUtil.getToken(applicationContext, account, scope)

            // Read database (assuming we'll need to export everything to a JSON)
            val database = com.app.data.AppDatabase.getDatabase(applicationContext)
            val repository = com.app.data.FinanceRepository(database.financeDao(), database)
            val exportedData = repository.exportAllDataAsJson()

val folderName = "[APP_FINANCE]"
            val fileName = "finance_backup.json"
            val client = OkHttpClient()

            // 1. Search for folder
            var folderId: String? = null
            val searchFolderRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=name='${folderName}' and mimeType='application/vnd.google-apps.folder'&spaces=drive")
                .header("Authorization", "Bearer $token")
                .build()
            val searchFolderResponse = client.newCall(searchFolderRequest).execute()
            if (searchFolderResponse.isSuccessful) {
                val json = searchFolderResponse.body?.string()
                json?.let {
                    val jsonObj = JSONObject(it)
                    val files = jsonObj.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        folderId = files.getJSONObject(0).getString("id")
                    }
                }
            }

            // 2. Create folder if not exists
            if (folderId == null) {
                val folderMetadata = JSONObject()
                folderMetadata.put("name", folderName)
                folderMetadata.put("mimeType", "application/vnd.google-apps.folder")
                val createFolderRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .header("Authorization", "Bearer $token")
                    .post(folderMetadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                    .build()
                val createFolderResponse = client.newCall(createFolderRequest).execute()
                if (createFolderResponse.isSuccessful) {
                    val json = createFolderResponse.body?.string()
                    json?.let {
                        val jsonObj = JSONObject(it)
                        folderId = jsonObj.optString("id")
                    }
                }
            }

            if (folderId == null) {
                return@withContext Result.retry() // Failed to find or create folder
            }

            // 3. Search for file INSIDE folder
            var fileId: String? = null
            val searchFileRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=name='${fileName}' and '${folderId}' in parents&spaces=drive")
                .header("Authorization", "Bearer $token")
                .build()
            
            val searchFileResponse = client.newCall(searchFileRequest).execute()
            if (searchFileResponse.isSuccessful) {
                val json = searchFileResponse.body?.string()
                json?.let {
                    val jsonObj = JSONObject(it)
                    val files = jsonObj.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        fileId = files.getJSONObject(0).getString("id")
                    }
                }
            }
            val localJson = JSONObject(exportedData)
            val localTxArray = localJson.optJSONArray("transactions")
            val isLocalTxEmpty = localTxArray == null || localTxArray.length() == 0

            if (fileId != null && isLocalTxEmpty) {
                android.util.Log.w("CloudSyncWorker", "Aborted cloud sync: local data is empty but Google Drive contains a backup file. Avoided overwriting.")
                return@withContext Result.success()
            }

            val metadata = JSONObject()
            metadata.put("name", fileName)
            metadata.put("mimeType", "application/json")
            if (fileId == null) {
                metadata.put("parents", org.json.JSONArray().put(folderId))
            }
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                .addFormDataPart("file", fileName, exportedData.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                .build()

            val uploadUrl = if (fileId == null) {
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            } else {
                "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=multipart"
            }
            
            val requestBuilder = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer $token")
            
            if (fileId != null) {
                requestBuilder.patch(requestBody)
            } else {
                requestBuilder.post(requestBody)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                return@withContext Result.success()
            } else {
                return@withContext Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }

    companion object {
        fun triggerOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "CloudSyncOneTime",
                androidx.work.ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }

        fun setupPeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "CloudSyncService",
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        }
    }
}
