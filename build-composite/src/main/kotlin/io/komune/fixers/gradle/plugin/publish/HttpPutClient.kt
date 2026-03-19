package io.komune.fixers.gradle.plugin.publish

import java.io.File

fun interface HttpPutClient {
    fun put(url: String, authHeader: String, file: File): HttpPutResult
}

sealed class HttpPutResult {
    data object Success : HttpPutResult()
    data object Conflict : HttpPutResult()
    data class Error(val code: Int, val message: String) : HttpPutResult()
}
