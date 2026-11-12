package io.komune.fixers.gradle.plugin.publish

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for DefaultHttpPutClient against a local HTTP server.
 */
class DefaultHttpPutClientTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var server: HttpServer
    private var requestMethod: String? = null
    private var authHeader: String? = null
    private var contentType: String? = null
    private var requestBody: String? = null
    private var responseCode: Int = 201
    private var responseBody: String = ""
    private var retryAfter: String? = null

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            requestMethod = exchange.requestMethod
            authHeader = exchange.requestHeaders.getFirst("Authorization")
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            requestBody = String(exchange.requestBody.readBytes())
            val bytes = responseBody.toByteArray()
            retryAfter?.let { exchange.responseHeaders.add("Retry-After", it) }
            exchange.sendResponseHeaders(responseCode, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) {
                exchange.responseBody.use { it.write(bytes) }
            } else {
                exchange.responseBody.close()
            }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun url() = "http://localhost:${server.address.port}/repo/artifact.jar"

    private fun artifactFile(content: String = "artifact-bytes"): File {
        val file = File(tempDir, "artifact.jar")
        file.writeText(content)
        return file
    }

    @Test
    fun `should PUT file content with auth header and return Success`() {
        val result = DefaultHttpPutClient().put(url(), "Basic dXNlcjp0b2tlbg==", artifactFile("payload"))

        assertThat(result).isEqualTo(HttpPutResult.Success)
        assertThat(requestMethod).isEqualTo("PUT")
        assertThat(authHeader).isEqualTo("Basic dXNlcjp0b2tlbg==")
        assertThat(contentType).isEqualTo("application/octet-stream")
        assertThat(requestBody).isEqualTo("payload")
    }

    @Test
    fun `should return Conflict on http 409`() {
        responseCode = 409

        val result = DefaultHttpPutClient().put(url(), "Basic auth", artifactFile())

        assertThat(result).isEqualTo(HttpPutResult.Conflict)
    }

    @Test
    fun `should return Error with status and body on failure`() {
        responseCode = 500
        responseBody = "Internal failure"

        val result = DefaultHttpPutClient().put(url(), "Basic auth", artifactFile())

        assertThat(result).isInstanceOf(HttpPutResult.Error::class.java)
        val error = result as HttpPutResult.Error
        assertThat(error.code).isEqualTo(500)
        assertThat(error.message).isEqualTo("Internal failure")
    }

    @Test
    fun `should carry the Retry-After header of a refusal`() {
        responseCode = 429
        responseBody = "error code: 1015"
        retryAfter = "30"

        val result = DefaultHttpPutClient().put(url(), "Basic auth", artifactFile())

        assertThat(result).isEqualTo(HttpPutResult.Error(429, "error code: 1015", retryAfter = "30"))
    }

    @Test
    fun `should leave Retry-After empty when the server sends none`() {
        responseCode = 503

        val result = DefaultHttpPutClient().put(url(), "Basic auth", artifactFile())

        assertThat((result as HttpPutResult.Error).retryAfter).isNull()
    }
}
