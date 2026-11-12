package io.komune.fixers.gradle.plugin.publish

import java.io.File

fun interface HttpPutClient {
    fun put(url: String, authHeader: String, file: File): HttpPutResult
}

sealed interface HttpPutResult {
    data object Success : HttpPutResult
    data object Conflict : HttpPutResult
    /**
     * A refused or failed upload. [retryAfter] is the raw `Retry-After` header of the answer,
     * when the server sent one: delay-seconds or an HTTP-date.
     */
    data class Error(val code: Int, val message: String, val retryAfter: String? = null) : HttpPutResult
}
