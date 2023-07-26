package com.example.aichatbox.network

import android.util.Log
import com.example.aichatbox.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File

/**
 * The TranscriptionApi serves as a network API to connect to IBM Watson's Speech to Text Service.
 *
 * Please ensure that the service credentials have been added to gradle.properties in the format:
 * SPEECH_TO_TEXT_BASE_URL="{apikey}"
 * SPEECH_TO_TEXT_API_KEY="{url}"
 *
 * Note that a maximum of 100 MB and a minimum of 100 bytes is allowed for each request.
 */

private const val TAG = "TranscriptionApiService"

// Minimum confidence threshold for valid
// transcription results (Range: 0-1).
private const val CONFIDENCE_THRESHOLD = 0.3f

// Time in milliseconds before a TimeoutException
// is called on the transcription request.
private const val TIMEOUT_DURATION = 6000L

// Speech to Text Service file size limits (bytes).
private const val MIN_FILE_SIZE = 100
private const val MAX_FILE_SIZE = 104857600

private const val AUDIO_FILE_TYPE = "audio/ogg"

// SERVICE CREDENTIALS
// URL Details
private const val BASE_URL = BuildConfig.SPEECH_TO_TEXT_BASE_URL
private const val ENDPOINT = "/v1/recognize"

// Basic Authentication Details
private const val AUTHORISATION_HEADER = "Authorization"
private const val USERNAME = "apikey"
private const val API_KEY = BuildConfig.SPEECH_TO_TEXT_API_KEY

/*
* Moshi Converter Factory - decodes JSON web data.
 */
private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

/*
* Retrofit object with the base URL. Fetches data from Watson Speech
* to Text and decodes it with the Moshi Converter Factory.
 */
private val retrofit = Retrofit.Builder()
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .baseUrl(BASE_URL)
    .build()

/*
* Network layer: this interface defines the Retrofit HTTP requests.
 */
interface TranscriptionApiService {
    /*
    * This function performs a POST request for a transcription result.
    * The JSON response is converted into a TranscriptionResult object.
    * See the data classes in TranscriptionResult.kt for more information.
     */
    @POST(ENDPOINT)
    suspend fun getTranscription(
        @Header(AUTHORISATION_HEADER) authHeader: String,
        @Query("model") languageModel: String,
        @Body audioFile: RequestBody
    ): TranscriptionResult
}

/*
* TranscriptionApi connects to IBM Watson's Speech to Text Service.
* It is initialised as a public singleton object to conserve resources
* by ensuring that the Retrofit API service is only initialised once.
 */
object TranscriptionApi {

    // Initialise the retrofit service only at first usage (by lazy).
    private val retrofitService: TranscriptionApiService by lazy {
        retrofit.create(TranscriptionApiService::class.java)
    }

    /*
    * This function transcribes an audio file (.ogg) into a String via Watson Speech to Text.
     */
    suspend fun transcribe(file: File): String {
        validateFile(file)
        // Create authentication header.
        val authHeader = okhttp3.Credentials.basic(USERNAME, API_KEY)
        // Create request body as data binary from audio .ogg file.
        val requestBody: RequestBody =
            file.asRequestBody(AUDIO_FILE_TYPE.toMediaTypeOrNull())

        return requestTranscription(authHeader, requestBody)
    }

    /*
    * This function checks if the file is readable and within allowed
    * size limits. A maximum of 100 MB and a minimum of 100 bytes is allowed.
     */
    private fun validateFile(file: File) {
        try {
            if (file.length() !in MIN_FILE_SIZE..MAX_FILE_SIZE) {
                throw TranscriptionAPIServiceException.InvalidFileSizeException()
            }
        } catch (e: Exception) {
            Log.e(TAG, "validateFile: failed to read file", e)
            throw TranscriptionAPIServiceException.InvalidFileException()
        }
    }

    /*
    * This function requests a transcription from the Speech to
    * Text service and processes the response.
     */
    private suspend fun requestTranscription(
        authHeader: String, requestBody: RequestBody
    ): String {
        return try {
            withTimeout(TIMEOUT_DURATION) {
                retrofitService
                    // Execute POST request.
                    .getTranscription(authHeader, "en-GB_Telephony", requestBody)
                    .results
                    // Filter out transcripts below the minimal confidence threshold.
                    .filter { it.alternatives[0].confidence.toFloat() > CONFIDENCE_THRESHOLD }
                    // Join remaining transcripts with newline separators.
                    .joinToString("\n") { it.alternatives[0].transcript }
            }
        } catch (e: HttpException) {
            Log.e(TAG, "requestTranscription: HTTP error", e)
            throw TranscriptionAPIServiceException.HttpException()
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "requestTranscription: no internet connection", e)
            throw TranscriptionAPIServiceException.NoInternetException()
        } catch (e: Exception) {
            Log.e(TAG, "requestTranscription: exception occurred", e)
            throw e
        }
    }

}