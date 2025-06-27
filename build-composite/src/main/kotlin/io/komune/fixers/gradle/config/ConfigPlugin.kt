package io.komune.fixers.gradle.config

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin that applies and configures the ConfigExtension for a project.
 * 
 * This plugin:
 * 1. Creates a ConfigExtension for the target project if it doesn't exist
 * 2. Logs configuration information
 * 3. Propagates configuration from the root project to subprojects
 */
class ConfigPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.config()
        val root: Project = target.rootProject

        target.afterEvaluate {
            log(target, root, extension)
            if (target == root) {
                root.subprojects.forEach { subproject ->
                    subproject.mergeConfig(extension)
                }
            }
        }
    }

    private fun log(
        target: Project,
        root: Project,
        extension: ConfigExtension
    ) {
        target.logger.lifecycle("=== Config Plugin ===")
        target.logger.lifecycle("Target project: ${target.name}")
        target.logger.lifecycle("Root project: ${root.name}")
        target.logger.lifecycle("GitHub Organization: ${extension.githubOrganization.get()}")
        target.logger.lifecycle("GitHub Project: ${extension.githubProject.get()}")
        target.logger.lifecycle("Repository URL: ${extension.repositoryUrl.get()}")
        target.logger.lifecycle("GitHub Packages URL: ${extension.githubPackagesUrl.get()}")

        // Log environment variables
        target.logger.lifecycle("PKG_DEPLOY_TYPE: ${extension.pkgDeployType.orNull ?: "not set"}")
        target.logger.lifecycle("PKG_MAVEN_REPO: ${extension.pkgMavenRepo.orNull ?: "not set"}")
        target.logger.lifecycle("PKG_GITHUB_USERNAME: ${extension.pkgGithubUsername.orNull ?: "not set"}")
        // Don't log the token for security reasons
        target.logger.lifecycle("PKG_GITHUB_TOKEN: ${if (extension.pkgGithubToken.orNull.isNullOrEmpty()) "not set" else "****"}")

        target.logger.lifecycle("====================")
    }

    private fun Project.mergeConfig(extension: ConfigExtension) {
        val subprojectExtension = this.config()

        if (!subprojectExtension.githubOrganization.isPresent) {
            subprojectExtension.githubOrganization.set(extension.githubOrganization)
        }
        if (!subprojectExtension.githubProject.isPresent) {
            subprojectExtension.githubProject.set(extension.githubProject)
        }

        if (!subprojectExtension.licenseName.isPresent) {
            subprojectExtension.licenseName.set(extension.licenseName)
        }
        if (!subprojectExtension.licenseUrl.isPresent) {
            subprojectExtension.licenseUrl.set(extension.licenseUrl)
        }

        if (!subprojectExtension.organizationId.isPresent) {
            subprojectExtension.organizationId.set(extension.organizationId)
        }
        if (!subprojectExtension.organizationName.isPresent) {
            subprojectExtension.organizationName.set(extension.organizationName)
        }
        if (!subprojectExtension.organizationUrl.isPresent) {
            subprojectExtension.organizationUrl.set(extension.organizationUrl)
        }
        if (!subprojectExtension.mavenCentralUrl.isPresent) {
            subprojectExtension.mavenCentralUrl.set(extension.mavenCentralUrl)
        }
        if (!subprojectExtension.mavenSnapshotsUrl.isPresent) {
            subprojectExtension.mavenSnapshotsUrl.set(extension.mavenSnapshotsUrl)
        }

        // Propagate environment variables
        if (!subprojectExtension.pkgDeployType.isPresent) {
            subprojectExtension.pkgDeployType.set(extension.pkgDeployType)
        }
        if (!subprojectExtension.pkgMavenRepo.isPresent) {
            subprojectExtension.pkgMavenRepo.set(extension.pkgMavenRepo)
        }
        if (!subprojectExtension.pkgGithubUsername.isPresent) {
            subprojectExtension.pkgGithubUsername.set(extension.pkgGithubUsername)
        }
        if (!subprojectExtension.pkgGithubToken.isPresent) {
            subprojectExtension.pkgGithubToken.set(extension.pkgGithubToken)
        }
    }
}
