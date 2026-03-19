package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

class DefaultHttpPutClient : HttpPutClient {

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_LAST_SUCCESS = 299
        const val HTTP_CONFLICT = 409
        val HTTP_SUCCESS = HTTP_OK..HTTP_LAST_SUCCESS
    }

    override fun put(url: String, authHeader: String, file: File): HttpPutResult {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Authorization", authHeader)
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.doOutput = true

        connection.outputStream.use { os ->
            file.inputStream().use { it.copyTo(os) }
        }

        val responseCode = connection.responseCode
        return when {
            responseCode in HTTP_SUCCESS -> HttpPutResult.Success
            responseCode == HTTP_CONFLICT -> HttpPutResult.Conflict
            else -> {
                val body = connection.errorStream?.bufferedReader()?.readText() ?: ""
                HttpPutResult.Error(responseCode, body)
            }
        }
    }
}
