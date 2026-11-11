package io.komune.fixers.gradle.plugin.publish

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging

/**
 * Uploads a staging directory to Maven Central via the Central Portal Publisher API.
 *
 * The staging directory is ZIPped and uploaded as a single bundle. The upload only
 * *initiates* a deployment, so the deployment status endpoint is then polled until the
 * deployment reaches a terminal state: a bundle that fails validation must fail the build
 * rather than be silently dropped.
 */
@Suppress("TooManyFunctions")
object CentralPortalUploader {

    const val PUBLISHING_TYPE_AUTOMATIC = "AUTOMATIC"
    const val PUBLISHING_TYPE_USER_MANAGED = "USER_MANAGED"

    private const val BYTES_PER_KB = 1024
    private const val MILLIS_PER_SECOND = 1000
    private const val HTTP_OK = 200
    private const val HTTP_LAST_SUCCESS = 299
    private val HTTP_SUCCESS = HTTP_OK..HTTP_LAST_SUCCESS

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val UPLOAD_READ_TIMEOUT_MS = 600_000
    private const val STATUS_READ_TIMEOUT_MS = 60_000

    private const val DEFAULT_POLL_INTERVAL_MS = 10_000L
    private const val DEFAULT_POLL_TIMEOUT_MS = 600_000L

    private const val STATE_FAILED = "FAILED"
    private const val STATE_PUBLISHED = "PUBLISHED"
    private const val STATE_VALIDATED = "VALIDATED"

    private const val DEPLOYMENTS_URL = "https://central.sonatype.com/publishing/deployments"

    private const val CRLF = "\r\n"

    private val logger = Logging.getLogger(CentralPortalUploader::class.java)

    private val DEPLOYMENT_STATE_REGEX = """"deploymentState"\s*:\s*"([A-Z_]+)"""".toRegex()

    /**
     * Tunables for a single deployment. Grouped so that [upload] keeps a readable signature
     * and so that tests can poll on a millisecond scale instead of the production interval.
     *
     * @property publishingType `AUTOMATIC` publishes as soon as validation passes; `USER_MANAGED`
     * stops at `VALIDATED` and waits for a manual release from the Central Portal UI.
     * @property statusPollIntervalMillis The wait between two status checks.
     * @property statusPollTimeoutMillis How long to poll the status; zero disables polling.
     */
    data class Options(
        val publishingType: String = PUBLISHING_TYPE_AUTOMATIC,
        val statusPollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MS,
        val statusPollTimeoutMillis: Long = DEFAULT_POLL_TIMEOUT_MS,
    )

    data class Credentials(val username: String, val password: String)

    fun upload(
        stagingDir: File,
        baseUrl: String,
        credentials: Credentials,
        bundleName: String = "bundle",
        options: Options = Options(),
    ) {
        if (!stagingDir.exists() || stagingDir.listFiles()?.isEmpty() != false) {
            logger.lifecycle("No artifacts found in staging directory: $stagingDir")
            return
        }

        logger.lifecycle("Creating bundle from staging directory: $stagingDir")
        val zipBytes = createZipBundle(stagingDir)
        logger.lifecycle("Bundle '$bundleName' size: ${zipBytes.size / BYTES_PER_KB} KB")

        val token = Base64.getEncoder().encodeToString("${credentials.username}:${credentials.password}".toByteArray())
        val filename = "$bundleName.zip"
        val deploymentId = uploadBundle(
            baseUrl = baseUrl,
            token = token,
            zipBytes = zipBytes,
            filename = filename,
            publishingType = options.publishingType,
        )
        logger.lifecycle("Deployment initiated: $deploymentId (publishingType=${options.publishingType})")

        require(options.statusPollTimeoutMillis >= 0) {
            "statusPollTimeoutSeconds must be zero (polling disabled) or positive, " +
                "got ${options.statusPollTimeoutMillis / MILLIS_PER_SECOND}"
        }
        if (options.statusPollTimeoutMillis == 0L) {
            logger.lifecycle("Status polling disabled — check the deployment at $DEPLOYMENTS_URL")
            return
        }
        awaitTerminalState(baseUrl = baseUrl, token = token, deploymentId = deploymentId, options = options)
    }

    private fun createZipBundle(dir: File): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(dir).path
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun uploadBundle(
        baseUrl: String,
        token: String,
        zipBytes: ByteArray,
        filename: String,
        publishingType: String,
    ): String {
        val boundary = "----FormBoundary${System.currentTimeMillis()}"
        val url = URI("$baseUrl/upload?publishingType=$publishingType").toURL()

        val connection = openPostConnection(url = url, token = token, readTimeoutMillis = UPLOAD_READ_TIMEOUT_MS)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true
        writeMultipartBody(connection = connection, boundary = boundary, zipBytes = zipBytes, filename = filename)

        val responseCode = connection.responseCode
        if (responseCode !in HTTP_SUCCESS) {
            val errorBody = connection.errorStream?.run { bufferedReader().use { it.readText() } }.orEmpty()
            throw GradleException("Central Portal upload failed (HTTP $responseCode): $errorBody")
        }

        return connection.inputStream.bufferedReader().readText().trim()
    }

    // Polls the deployment status until it either succeeds or fails.
    //
    // A `FAILED` deployment fails the build. Running out of time does not: before this
    // polling existed the build never waited at all, so a slow Central Portal must not turn
    // a good release red — it degrades to a warning naming the deployment.
    private fun awaitTerminalState(baseUrl: String, token: String, deploymentId: String, options: Options) {
        val successState = if (options.publishingType == PUBLISHING_TYPE_USER_MANAGED) {
            STATE_VALIDATED
        } else {
            STATE_PUBLISHED
        }
        val deadline = System.currentTimeMillis() + options.statusPollTimeoutMillis
        var lastState: String? = null

        while (true) {
            val remainingMillis = deadline - System.currentTimeMillis()
            if (remainingMillis <= 0) break

            val readTimeoutMillis = minOf(a = remainingMillis, b = STATUS_READ_TIMEOUT_MS.toLong()).toInt()
            val state = fetchDeploymentState(
                baseUrl = baseUrl,
                token = token,
                deploymentId = deploymentId,
                readTimeoutMillis = readTimeoutMillis,
            )
            if (state != null && state != lastState) {
                logger.lifecycle("  Deployment $deploymentId state: $state")
                lastState = state
            }
            when (state) {
                STATE_FAILED -> {
                    throw GradleException(
                        "Central Portal deployment '$deploymentId' FAILED validation. " +
                            "Inspect the errors at $DEPLOYMENTS_URL"
                    )
                }
                successState -> {
                    logger.lifecycle("Central Portal deployment '$deploymentId' reached $successState.")
                    return
                }
                else -> {
                    val sleepMillis = minOf(
                        a = options.statusPollIntervalMillis,
                        b = deadline - System.currentTimeMillis(),
                    )
                    if (sleepMillis > 0) Thread.sleep(sleepMillis)
                }
            }
        }

        logger.warn(
            "WARNING: Central Portal deployment '$deploymentId' did not reach $successState within " +
                "${options.statusPollTimeoutMillis / MILLIS_PER_SECOND}s " +
                "(last known state: ${lastState ?: "unknown"}). " +
                "It may still complete — check $DEPLOYMENTS_URL"
        )
    }

    // Returns the current `deploymentState`, or `null` when the portal could not be reached
    // or answered with something unparseable. `null` means "unknown, try again", never "failed".
    private fun fetchDeploymentState(
        baseUrl: String,
        token: String,
        deploymentId: String,
        readTimeoutMillis: Int = STATUS_READ_TIMEOUT_MS,
    ): String? {
        val url = URI("$baseUrl/status?id=$deploymentId").toURL()
        return try {
            val connection = openPostConnection(url = url, token = token, readTimeoutMillis = readTimeoutMillis)
            val responseCode = connection.responseCode
            if (responseCode !in HTTP_SUCCESS) {
                logger.lifecycle("  Central Portal status check returned HTTP $responseCode, retrying")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val state = DEPLOYMENT_STATE_REGEX.find(body)?.run { groupValues[1] }
            if (state == null) {
                logger.lifecycle("  Central Portal status response had no deploymentState, retrying")
            }
            state
        } catch (e: IOException) {
            logger.lifecycle("  Central Portal status check failed (${e.message ?: e::class.java.name}), retrying")
            null
        }
    }

    private fun writeMultipartBody(
        connection: HttpURLConnection,
        boundary: String,
        zipBytes: ByteArray,
        filename: String
    ) {
        connection.outputStream.use { os ->
            os.write("--$boundary$CRLF".toByteArray())
            os.write("""Content-Disposition: form-data; name="bundle"; filename="$filename"$CRLF""".toByteArray())
            os.write("Content-Type: application/octet-stream$CRLF".toByteArray())
            os.write(CRLF.toByteArray())
            os.write(zipBytes)
            os.write(CRLF.toByteArray())
            os.write("--$boundary--$CRLF".toByteArray())
        }
    }

    private fun openPostConnection(url: URL, token: String, readTimeoutMillis: Int): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = readTimeoutMillis
        return connection
    }
}
