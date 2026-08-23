package com.example.data.backup

import android.content.Context
import android.content.Intent
import com.example.data.datasource.ExpenseDatabase
import com.example.domain.model.Expense
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed class BackupResult {
    data class Success(val message: String, val count: Int = 0) : BackupResult()
    data class Error(val message: String) : BackupResult()
}

class DriveBackupManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val driveScope = Scope("https://www.googleapis.com/auth/drive.appdata")

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveScope)
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun exportToJsonString(): String = withContext(Dispatchers.IO) {
        val dao = ExpenseDatabase.getDatabase(context).expenseDao()
        val expenses = dao.getAllExpensesSync()
        val jsonArray = JSONArray()
        for (expense in expenses) {
            val obj = JSONObject()
            obj.put("id", expense.id)
            obj.put("shopName", expense.shopName)
            obj.put("amount", expense.amount)
            obj.put("date", expense.date)
            obj.put("imagePath", expense.imagePath ?: "")
            obj.put("note", expense.note ?: "")
            obj.put("category", expense.category)
            obj.put("isPendingAnalysis", expense.isPendingAnalysis)
            jsonArray.put(obj)
        }
        val wrapper = JSONObject()
        wrapper.put("version", 2)
        wrapper.put("timestamp", System.currentTimeMillis())
        wrapper.put("expenses", jsonArray)
        wrapper.toString(2)
    }

    suspend fun importFromJsonString(jsonString: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val wrapper = JSONObject(jsonString)
            val jsonArray = wrapper.getJSONArray("expenses")
            val dao = ExpenseDatabase.getDatabase(context).expenseDao()
            val list = mutableListOf<Expense>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val expense = Expense(
                    id = if (obj.has("id")) obj.getLong("id") else 0L,
                    shopName = obj.optString("shopName", "Unknown Shop"),
                    amount = obj.optDouble("amount", 0.0),
                    date = obj.optLong("date", System.currentTimeMillis()),
                    imagePath = obj.optString("imagePath").takeIf { !it.isNull_or_blank() },
                    isPendingAnalysis = obj.optBoolean("isPendingAnalysis", false),
                    note = obj.optString("note", ""),
                    category = obj.optString("category", "General").ifBlank { "General" }
                )
                list.add(expense)
            }
            dao.insertExpenses(list)
            BackupResult.Success("Successfully imported ${list.size} expense entries.", count = list.size)
        } catch (e: Exception) {
            BackupResult.Error("Failed to parse backup data: ${e.localizedMessage ?: "Invalid JSON format"}")
        }
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    suspend fun backupToDrive(account: GoogleSignInAccount): BackupResult = withContext(Dispatchers.IO) {
        try {
            val token = GoogleAuthUtil.getToken(
                context,
                account.account ?: return@withContext BackupResult.Error("No valid Google Account attached."),
                "oauth2:https://www.googleapis.com/auth/drive.appdata"
            )

            val jsonContent = exportToJsonString()

            // Step 1: Search if backup file exists in appDataFolder
            val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='expense_tracker_backup.json'&fields=files(id,name)"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val searchResponse = client.newCall(searchRequest).execute()
            val searchResponseBody = searchResponse.body?.string() ?: ""

            if (!searchResponse.isSuccessful) {
                return@withContext BackupResult.Error("Google Drive search failed (${searchResponse.code}): $searchResponseBody")
            }

            val searchJson = JSONObject(searchResponseBody)
            val files = searchJson.optJSONArray("files")
            val existingFileId = if (files != null && files.length() > 0) {
                files.getJSONObject(0).getString("id")
            } else null

            val mediaType = "application/json; charset=utf-8".toMediaType()

            if (existingFileId != null) {
                // Update existing backup file
                val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
                val putRequest = Request.Builder()
                    .url(uploadUrl)
                    .header("Authorization", "Bearer $token")
                    .patch(jsonContent.toRequestBody(mediaType))
                    .build()

                val putResponse = client.newCall(putRequest).execute()
                if (putResponse.isSuccessful) {
                    BackupResult.Success("Backup successfully updated on Google Drive.")
                } else {
                    BackupResult.Error("Failed to update Google Drive file: ${putResponse.code}")
                }
            } else {
                // Create new backup file in appDataFolder using simple or multipart upload
                val metadata = JSONObject()
                metadata.put("name", "expense_tracker_backup.json")
                metadata.put("parents", JSONArray().put("appDataFolder"))

                val boundary = "---BackupBoundary" + System.currentTimeMillis()
                val multipartBody = StringBuilder()
                    .append("--$boundary\r\n")
                    .append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    .append(metadata.toString())
                    .append("\r\n--$boundary\r\n")
                    .append("Content-Type: application/json\r\n\r\n")
                    .append(jsonContent)
                    .append("\r\n--$boundary--\r\n")
                    .toString()

                val createRequest = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "multipart/related; boundary=$boundary")
                    .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                    .build()

                val createResponse = client.newCall(createRequest).execute()
                if (createResponse.isSuccessful) {
                    BackupResult.Success("Backup file created successfully on Google Drive!")
                } else {
                    val errBody = createResponse.body?.string()
                    BackupResult.Error("Failed to create Google Drive file (${createResponse.code}): $errBody")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult.Error("Google Drive backup error: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }

    suspend fun restoreFromDrive(account: GoogleSignInAccount): BackupResult = withContext(Dispatchers.IO) {
        try {
            val token = GoogleAuthUtil.getToken(
                context,
                account.account ?: return@withContext BackupResult.Error("No valid Google Account attached."),
                "oauth2:https://www.googleapis.com/auth/drive.appdata"
            )

            // Step 1: Find backup file
            val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='expense_tracker_backup.json'&fields=files(id,name)"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val searchResponse = client.newCall(searchRequest).execute()
            val searchResponseBody = searchResponse.body?.string() ?: ""

            if (!searchResponse.isSuccessful) {
                return@withContext BackupResult.Error("Google Drive search failed: ${searchResponse.code}")
            }

            val searchJson = JSONObject(searchResponseBody)
            val files = searchJson.optJSONArray("files")
            if (files == null || files.length() == 0) {
                return@withContext BackupResult.Error("No backup file found in your Google Drive AppData folder.")
            }

            val fileId = files.getJSONObject(0).getString("id")

            // Step 2: Download file content
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val downloadResponse = client.newCall(downloadRequest).execute()
            val jsonContent = downloadResponse.body?.string() ?: ""

            if (!downloadResponse.isSuccessful || jsonContent.isEmpty()) {
                return@withContext BackupResult.Error("Failed to download backup file from Google Drive.")
            }

            importFromJsonString(jsonContent)
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult.Error("Restore error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
