package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import java.io.File
import org.gradle.api.GradleException

internal fun uploadToGithubPackages(stagingDir: File, fixersConfig: ConfigExtension) = uploadTo(
	repositoryName = "GitHub Packages",
	stagingDir = stagingDir,
	url = fixersConfig.publish.githubPackagesUrl.get(),
	username = requireCredential(fixersConfig.publish.pkgGithubUsername.orNull, "FIXERS_PUBLISH_GITHUB_USERNAME"),
	password = requireCredential(fixersConfig.publish.pkgGithubToken.orNull, "FIXERS_PUBLISH_GITHUB_TOKEN"),
)

internal fun uploadToMavenSnapshots(stagingDir: File, fixersConfig: ConfigExtension) = uploadTo(
	repositoryName = "Maven Central Snapshots",
	stagingDir = stagingDir,
	url = fixersConfig.publish.mavenSnapshotsUrl.get(),
	username = requireCredential(
		fixersConfig.publish.mavenCentralUsername.orNull, "FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME"
	),
	password = requireCredential(
		fixersConfig.publish.mavenCentralPassword.orNull, "FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD"
	),
)

internal fun uploadToCentralPortal(
	stagingDir: File,
	centralUrl: String,
	username: String?,
	password: String?,
	bundleName: String,
	options: CentralPortalUploader.Options,
) {
	val resolvedUsername = requireCredential(username, "FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME")
	val resolvedPassword = requireCredential(password, "FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD")
	CentralPortalUploader.upload(
		stagingDir = stagingDir,
		baseUrl = centralUrl,
		username = resolvedUsername,
		password = resolvedPassword,
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
	MavenRepositoryUploader.to(repositoryName)
		.from(stagingDir)
		.at(url)
		.withCredentials(username, password)
		.upload()
}
