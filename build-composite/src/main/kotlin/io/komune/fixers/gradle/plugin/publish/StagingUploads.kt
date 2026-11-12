package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import java.io.File
import org.gradle.api.GradleException

// A snapshot goes to GitHub Packages with the same files as to Maven Central Snapshots; a release with all.
internal fun uploadToGithubPackages(stagingDir: File, fixersConfig: ConfigExtension, snapshot: Boolean) = uploadTo(
    repository = Repository(
        name = "GitHub Packages",
        url = fixersConfig.publish.githubPackagesUrl.get(),
        isSnapshot = snapshot,
    ),
    stagingDir = stagingDir,
    username = requireCredential(
        value = fixersConfig.publish.pkgGithubUsername.orNull,
        envVar = "FIXERS_PUBLISH_GITHUB_USERNAME",
    ),
    password = requireCredential(
        value = fixersConfig.publish.pkgGithubToken.orNull,
        envVar = "FIXERS_PUBLISH_GITHUB_TOKEN",
    ),
)

internal fun uploadToMavenSnapshots(stagingDir: File, fixersConfig: ConfigExtension) = uploadTo(
    repository = Repository(
        name = "Maven Central Snapshots",
        url = fixersConfig.publish.mavenSnapshotsUrl.get(),
        minRequestIntervalMillis = snapshotsMinRequestInterval(fixersConfig),
        quota = MAVEN_SNAPSHOTS_QUOTA,
        isSnapshot = true,
    ),
    stagingDir = stagingDir,
    username = requireCredential(
        value = fixersConfig.publish.mavenCentralUsername.orNull,
        envVar = "FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME",
    ),
    password = requireCredential(
        value = fixersConfig.publish.mavenCentralPassword.orNull,
        envVar = "FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD",
    ),
)

internal fun centralPortalCredentials(username: String?, password: String?) = CentralPortalUploader.Credentials(
    username = requireCredential(value = username, envVar = "FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME"),
    password = requireCredential(value = password, envVar = "FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD"),
)

internal fun uploadToCentralPortal(
    stagingDir: File,
    centralUrl: String,
    credentials: CentralPortalUploader.Credentials,
    bundleName: String,
    options: CentralPortalUploader.Options,
) {
    CentralPortalUploader.upload(
        stagingDir = stagingDir,
        baseUrl = centralUrl,
        credentials = credentials,
        bundleName = bundleName,
        options = options,
    )
}

private fun snapshotsMinRequestInterval(fixersConfig: ConfigExtension): Long {
    val millis = fixersConfig.publish.mavenSnapshotsMinRequestIntervalMillis.get().toLong()
    if (millis < 0) {
        throw GradleException(
            "fixers.publish.maven.snapshots.minRequestIntervalMillis must be 0 (no spacing) or more, got $millis"
        )
    }
    return millis
}

private fun requireCredential(value: String?, envVar: String): String =
    value ?: throw GradleException("$envVar is not set")

// GitHub Packages took a 3 022-file publish at full speed: only Maven Central Snapshots is spaced.
private data class Repository(
    val name: String,
    val url: String,
    val isSnapshot: Boolean,
    val minRequestIntervalMillis: Long = 0,
    val quota: RequestQuota? = null,
)

// Observed, not documented: 469 to 544 accepted, then a 20 min block. 450 keeps a margin.
private const val MAVEN_SNAPSHOTS_QUOTA_REQUESTS = 450
private const val MAVEN_SNAPSHOTS_QUOTA_WINDOW_MS = 1_200_000L
private val MAVEN_SNAPSHOTS_QUOTA = RequestQuota(
    requests = MAVEN_SNAPSHOTS_QUOTA_REQUESTS,
    windowMillis = MAVEN_SNAPSHOTS_QUOTA_WINDOW_MS,
)

private fun uploadTo(
    repository: Repository,
    stagingDir: File,
    username: String,
    password: String,
) {
    MavenRepositoryUploader.toRepository(repository.name)
        .from(stagingDir, snapshot = repository.isSnapshot)
        .atUrl(repository.url)
        .withCredentials(user = username, tok = password)
        .withMinRequestInterval(repository.minRequestIntervalMillis, quota = repository.quota)
        .upload()
}
