package com.example.aichatbox.network

import com.squareup.moshi.Json

/**
 * JSON response data from the Speech to Text service is mapped to the following data classes.
 *
 * Example response:
 * {
 *     "result_index": 0,
 *     "results": [
 *     {
 *         "alternatives": [
 *        {
 *            "confidence": 0.96
 *            "transcript": "several tornadoes touch down as a line of severe thunderstorms swept through Colorado on Sunday "
 *        }
 *         ],
 *         "final": true
 *     }
 *     ]
 * }
 */

data class TranscriptionResult(
    // Map the JSON key result_index to
    // the variable name resultIndex.
    @Json(name = "result_index") val resultIndex: Int,
    val results: List<Result>
)

data class Result(
    val final: Boolean,
    val alternatives: List<Alternative>
)

data class Alternative(
    val transcript: String,
    val confidence: Double
)