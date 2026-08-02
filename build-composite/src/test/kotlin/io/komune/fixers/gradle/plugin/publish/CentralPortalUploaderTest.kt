package io.komune.fixers.gradle.plugin.publish

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipInputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for CentralPortalUploader against a local HTTP server.
 */
class CentralPortalUploaderTest {

    @TempDir
    lateinit var stagingDir: File

    private lateinit var server: HttpServer
    private var requestPath: String? = null
    private var requestQuery: String? = null
    private var authHeader: String? = null
    private var requestBody: ByteArray = ByteArray(0)
    private var responseCode: Int = 200
    private var responseBody: String = "deployment-id-123"

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            requestPath = exchange.requestURI.path
            requestQuery = exchange.requestURI.query
            authHeader = exchange.requestHeaders.getFirst("Authorization")
            requestBody = exchange.requestBody.readBytes()
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(responseCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun baseUrl() = "http://localhost:${server.address.port}"

    private fun createStagingFile(relativePath: String, content: String = "artifact-content") {
        val file = File(stagingDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `should upload zip bundle with bearer token and automatic publishing`() {
        createStagingFile("io/komune/test/1.0/test-1.0.jar")

        CentralPortalUploader.upload(stagingDir, baseUrl(), "user", "pass", "my-bundle")

        assertThat(requestPath).isEqualTo("/upload")
        assertThat(requestQuery).isEqualTo("publishingType=AUTOMATIC")
        val expectedToken = java.util.Base64.getEncoder().encodeToString("user:pass".toByteArray())
        assertThat(authHeader).isEqualTo("Bearer $expectedToken")
        assertThat(String(requestBody)).contains("filename=\"my-bundle.zip\"")
    }

    @Test
    fun `should zip staging directory content preserving relative paths`() {
        createStagingFile("io/komune/test/1.0/test-1.0.jar", "jar-bytes")
        createStagingFile("io/komune/test/1.0/test-1.0.pom", "pom-bytes")

        CentralPortalUploader.upload(stagingDir, baseUrl(), "user", "pass")

        val zipBytes = extractZipFromMultipart(requestBody)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = String(zis.readBytes())
                entry = zis.nextEntry
            }
        }
        assertThat(entries.keys).containsExactlyInAnyOrder(
            "io/komune/test/1.0/test-1.0.jar",
            "io/komune/test/1.0/test-1.0.pom"
        )
        assertThat(entries["io/komune/test/1.0/test-1.0.jar"]).isEqualTo("jar-bytes")
    }

    @Test
    fun `should skip upload when staging directory is empty`() {
        CentralPortalUploader.upload(stagingDir, baseUrl(), "user", "pass")

        assertThat(requestPath).isNull()
    }

    @Test
    fun `should skip upload when staging directory does not exist`() {
        val missing = File(stagingDir, "missing")

        CentralPortalUploader.upload(missing, baseUrl(), "user", "pass")

        assertThat(requestPath).isNull()
    }

    @Test
    fun `should throw GradleException with body on http error`() {
        createStagingFile("artifact.jar")
        responseCode = 401
        responseBody = "Unauthorized access"

        assertThatThrownBy {
            CentralPortalUploader.upload(stagingDir, baseUrl(), "user", "bad-pass")
        }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("HTTP 401")
            .hasMessageContaining("Unauthorized access")
    }

    private fun extractZipFromMultipart(body: ByteArray): ByteArray {
        // Zip content starts after the multipart headers (double CRLF) and
        // ends before the trailing CRLF + closing boundary.
        val headerEnd = body.indexOfSequence("\r\n\r\n".toByteArray()) + 4
        val closingBoundaryStart = body.lastIndexOfSequence("\r\n--".toByteArray())
        return body.copyOfRange(headerEnd, closingBoundaryStart)
    }

    private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
        outer@ for (i in 0..(size - sequence.size)) {
            for (j in sequence.indices) {
                if (this[i + j] != sequence[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun ByteArray.lastIndexOfSequence(sequence: ByteArray): Int {
        outer@ for (i in (size - sequence.size) downTo 0) {
            for (j in sequence.indices) {
                if (this[i + j] != sequence[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
