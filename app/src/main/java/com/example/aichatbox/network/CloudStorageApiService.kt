package com.example.aichatbox.network

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.util.Log
import com.example.aichatbox.database.AppDatabase
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale

/**
 * The TranscriptionApi serves as a network API to connect to IBM Cloud Object Storage Service.
 * The local database and image recognition model are updated by requesting the latest versions
 * from the server.
 */

private const val TAG = "CloudStorageApiService"

// SERVICE CREDENTIALS
// URL Details
private const val BASE_URL = "https://s3.eu-gb.cloud-object-storage.appdomain.cloud"
private const val DATABASE_ENDPOINT = "/mvb/data.db"
private const val MODEL_ENDPOINT = "/mvb/model.tflite"

// Request Headers
private const val AUTHORISATION_HEADER = "Authorization"
private const val LAST_MODIFIED_HEADER = "If-Modified-Since"

// RFC 1123 Timestamp Formatting
private const val RFC_1123_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z"
private const val RFC_1123_TIMEZONE = "GMT"

// File writing buffer size.
private const val BUFFER_SIZE = 4096

/*
* Retrofit object with the base URL. No converter factory
* is needed as the response is binary data.
 */
private val retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .build()

/*
* Network layer: this interface defines the Retrofit HTTP requests.
 */
interface CloudStorageApiService {
    /*
    * This function performs a GET request for the database file
    * if the local version is outdated. If not, 304 is returned.
     */
    @GET(DATABASE_ENDPOINT)
    @Streaming
    suspend fun getDatabase(
        @Header(AUTHORISATION_HEADER) bearerToken: String,
        @Header(LAST_MODIFIED_HEADER) dateTime: String
    ): Response<ResponseBody>

    /*
    * This function performs a GET request for the model file
    * if the local version is outdated. If not, 304 is returned.
     */
    @GET(MODEL_ENDPOINT)
    @Streaming
    suspend fun getModel(
        @Header(AUTHORISATION_HEADER) bearerToken: String,
        @Header(LAST_MODIFIED_HEADER) dateTime: String
    ): Response<ResponseBody>
}


/*
* CloudStorageApi connects to IBM Cloud Object Storage Service.
* It is initialised as a public singleton object to conserve resources
* by ensuring that the Retrofit API service is only initialised once.
 */
object CloudStorageApi {

    // File options to update.
    private enum class Option { DATABASE, MODEL }

    // Initialise the retrofit service only at first usage (by lazy).
    private val retrofitService: CloudStorageApiService by lazy {
        retrofit.create(CloudStorageApiService::class.java)
    }

    /*
    * This function updates the local database if a
    * newer version is available. The returned Boolean
    * indicates if the update was successful.
     */
    suspend fun updateDatabase(context: Context): Boolean {
        return updateFile(context, Option.DATABASE)
    }

    /*
    * This function updates the local model file if a
    * newer version is available. The returned Boolean
    * indicates if the update was successful.
     */
    suspend fun updateModel(context: Context): Boolean {
        return updateFile(context, Option.MODEL)
    }

    /*
    * This function downloads the latest version of the selected file
    * from the server if needed and overwrites the local version.
    * The returned Boolean indicates if the update was successful.
     */
    private suspend fun updateFile(context: Context, option: Option): Boolean {
        // Use correct filepath for selected option.
        val file = when (option) {
            Option.DATABASE -> File(context.filesDir, AppDatabase.FILENAME)
            Option.MODEL -> File(context.filesDir, "model.tflite")
        }

        // Get the selected file from the server, return false if unsuccessful.
        val response = getFileFromServer(file, option) ?: return false

        // Get the responseBody, return false if unsuccessful.
        val responseBody = if (response.code() == 304) {
            Log.i(TAG, "Local $option file is up to date")
            // Local file is already up to date, return true.
            return true
        } else if (response.isSuccessful) {
            response.body() ?: return false
        } else {
            Log.e(TAG, "HTTP error: ${response.code()} - ${response.message()}")
            return false
        }
        return writeToFile(file, responseBody)
    }

    /*
    * This function requests the file corresponding to "option" from
    * the server, returning null if an Exception occurs. The last
    * modified time of the local file is also passed to the server
    * to check if the request is necessary.
     */
    private suspend fun getFileFromServer(
        localFile: File,
        option: Option
    ): Response<ResponseBody>? {
        // Get a valid IAM token, return null if unsuccessful.
        val token = TokenApi.getToken() ?: return null
        return try {
            when (option) {
                Option.DATABASE -> retrofitService.getDatabase(
                    "Bearer $token", getLastModified(localFile)
                )

                Option.MODEL -> retrofitService.getModel(
                    "Bearer $token", getLastModified(localFile)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFileFromServer: exception occurred", e)
            null
        }
    }

    /*
    * This function gets the last modified date and time of a file
    * in RFC 1123 format (GMT timezone). Unix Epoch is returned if
    * the file does not exist.
     */
    private fun getLastModified(file: File): String {
        val lastModifiedTime = try {
            file.lastModified()
        } catch (e: Exception) {
            0L
        }

        val dateFormat = SimpleDateFormat(RFC_1123_PATTERN, Locale.ENGLISH)
        dateFormat.timeZone = TimeZone.getTimeZone(RFC_1123_TIMEZONE)

        return dateFormat.format(Date(lastModifiedTime))
    }


    /*
    * This function deletes file if it exists and writes the data
    * from responseBody to it. The returned Boolean indicates success.
     */
    private fun writeToFile(file: File, responseBody: ResponseBody): Boolean {
        // Delete the file if it already exists.
        if (file.exists()) {
            file.delete()
        }

        return try {
            writeData(file, responseBody)
            Log.i(TAG, "Local database updated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeToFile: exception occurred", e)
            false
        }
    }

    /*
    * This function writes the data from responseBody to file.
     */
    private fun writeData(file: File, responseBody: ResponseBody) {
        responseBody.byteStream().use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                // Keep writing buffered data to outputStream until the end of inputStream.
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }

}