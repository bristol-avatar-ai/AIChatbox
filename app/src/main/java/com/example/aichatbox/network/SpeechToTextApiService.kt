package com.example.aichatbox.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.File

data class TranscriptionResult (
    @Json(name = "result_index") val resultIndex: Int,
    val results: List<Result>
)

data class Alternative(
    val transcript: String,
    val confidence: Double
)

data class Result(
    val final: Boolean,
    val alternatives: List<Alternative>
)

private const val CONFIDENCE_THRESHOLD = 0.5f
private const val BASE_URL =
    "https://api.eu-gb.speech-to-text.watson.cloud.ibm.com/instances/b45276de-e0ba-405b-a3fe-3810419e1847/"
private const val ENDPOINT =
    "/v1/recognize"
private const val API_KEY = "asFnyT-j2YTK5XMPoLR2oR9pbnd0wUCQA4QC5jIKF7Ax"

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private val retrofit = Retrofit.Builder()
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .baseUrl(BASE_URL)
    .build()

interface SpeechToTextApiService {
    @POST(ENDPOINT)
    suspend fun getTranscription(
        @Header("Authorization") authHeader: String,
        @Body audioFile: RequestBody
    ): TranscriptionResult
}

object SpeechToTextApi {
    private val retrofitService : SpeechToTextApiService by lazy {
        retrofit.create(SpeechToTextApiService::class.java) }

    suspend fun transcribe(file: File): String {
        // maximum of 100 MB and a minimum of 100 bytes of audio TODO: Check for this
        val authHeader = okhttp3.Credentials.basic("apikey", API_KEY)
        val requestBody: RequestBody = file.asRequestBody("audio/ogg".toMediaTypeOrNull())

        val transcriptionResult =  retrofitService.getTranscription(authHeader, requestBody)
        return transcriptionResult.results
            .filter { it.alternatives[0].confidence.toFloat() > CONFIDENCE_THRESHOLD }
            .joinToString("\n") { it.alternatives[0].transcript }
    }
}