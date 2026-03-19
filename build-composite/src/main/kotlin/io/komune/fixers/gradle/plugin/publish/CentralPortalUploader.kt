package io.komune.fixers.gradle.plugin.publish

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException

/**
 * Uploads a staging directory to Maven Central via the Central Portal Publisher API.
 *
 * The staging directory is ZIPped and uploaded as a single bundle. The Central Portal
 * validates and publishes the bundle automatically.
 */
@Suppress("TooManyFunctions")
object CentralPortalUploader {

	private const val BYTES_PER_KB = 1024
	private const val HTTP_OK = 200
	private const val HTTP_LAST_SUCCESS = 299
	private val HTTP_SUCCESS = HTTP_OK..HTTP_LAST_SUCCESS

	fun upload(
		stagingDir: File,
		baseUrl: String,
		username: String,
		password: String,
		bundleName: String = "bundle"
	) {
		if (!stagingDir.exists() || stagingDir.listFiles()?.isEmpty() != false) {
			println("No artifacts found in staging directory: $stagingDir")
			return
		}

		println("Creating bundle from staging directory: $stagingDir")
		val zipBytes = createZipBundle(stagingDir)
		println("Bundle '$bundleName' size: ${zipBytes.size / BYTES_PER_KB} KB")

		val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
		val filename = "$bundleName.zip"
		val deploymentId = uploadBundle(baseUrl, token, zipBytes, filename)
		println("Deployment initiated: $deploymentId")
		println("Publishing type is AUTOMATIC — bundle will be validated and published automatically.")
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

	private fun uploadBundle(baseUrl: String, token: String, zipBytes: ByteArray, filename: String): String {
		val boundary = "----FormBoundary${System.currentTimeMillis()}"
		val url = URI("$baseUrl/upload?publishingType=AUTOMATIC").toURL()

		val connection = openPostConnection(url, token)
		connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
		connection.doOutput = true
		writeMultipartBody(connection, boundary, zipBytes, filename)

		val responseCode = connection.responseCode
		if (responseCode !in HTTP_SUCCESS) {
			val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
			throw GradleException("Central Portal upload failed (HTTP $responseCode): $errorBody")
		}

		return connection.inputStream.bufferedReader().readText().trim()
	}

	private fun writeMultipartBody(
		connection: HttpURLConnection,
		boundary: String,
		zipBytes: ByteArray,
		filename: String
	) {
		connection.outputStream.use { os ->
			os.write("--$boundary\r\n".toByteArray())
			os.write("Content-Disposition: form-data; name=\"bundle\"; filename=\"$filename\"\r\n".toByteArray())
			os.write("Content-Type: application/octet-stream\r\n".toByteArray())
			os.write("\r\n".toByteArray())
			os.write(zipBytes)
			os.write("\r\n".toByteArray())
			os.write("--$boundary--\r\n".toByteArray())
		}
	}

	private fun openPostConnection(url: java.net.URL, token: String): HttpURLConnection {
		val connection = url.openConnection() as HttpURLConnection
		connection.requestMethod = "POST"
		connection.setRequestProperty("Authorization", "Bearer $token")
		return connection
	}

}
