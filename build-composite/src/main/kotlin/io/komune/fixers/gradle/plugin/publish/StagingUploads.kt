package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import java.io.File
import org.gradle.api.GradleException

internal fun uploadToGithubPackages(stagingDir: File, fixersConfig: ConfigExtension) = uploadTo(
    repositoryName = "GitHub Packages",
    stagingDir = stagingDir,
    url = fixersConfig.publish.githubPackagesUrl.get(),
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
    repositoryName = "Maven Central Snapshots",
    stagingDir = stagingDir,
    url = fixersConfig.publish.mavenSnapshotsUrl.get(),
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

private fun requireCredential(value: String?, envVar: String): String =
    value ?: throw GradleException("$envVar is not set")

private fun uploadTo(
    repositoryName: String,
    stagingDir: File,
    url: String,
    username: String,
    password: String,
) {
    MavenRepositoryUploader.toRepository(repositoryName)
        .from(stagingDir)
        .atUrl(url)
        .withCredentials(user = username, tok = password)
        .upload()
}
