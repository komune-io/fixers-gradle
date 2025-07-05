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
        target.logger.lifecycle("GitHub Packages URL: ${extension.githubPackagesUrl.get()}")
        target.logger.lifecycle("====================")
    }

    private fun Project.mergeConfig(extension: ConfigExtension) {
        val subprojectExtension = this.config()

        // Copy bundle properties
        subprojectExtension.bundle.apply {
            // Copy main bundle properties
            if (id == null) {
                id = extension.bundle.id
            }
            if (description == null) {
                description = extension.bundle.description
            }
            if (url == null) {
                url = extension.bundle.url
            }

            // Copy license properties
            if (licenseName == null) {
                licenseName = extension.bundle.licenseName
            }
            if (licenseUrl == null) {
                licenseUrl = extension.bundle.licenseUrl
            }
            if (licenseDistribution == null) {
                licenseDistribution = extension.bundle.licenseDistribution
            }

            // Copy developer properties
            if (developerId == null) {
                developerId = extension.bundle.developerId
            }
            if (developerName == null) {
                developerName = extension.bundle.developerName
            }
            if (developerOrganization == null) {
                developerOrganization = extension.bundle.developerOrganization
            }
            if (developerOrganizationUrl == null) {
                developerOrganizationUrl = extension.bundle.developerOrganizationUrl
            }

            // Copy SCM properties
            if (scmConnection == null) {
                scmConnection = extension.bundle.scmConnection
            }
            if (scmDeveloperConnection == null) {
                scmDeveloperConnection = extension.bundle.scmDeveloperConnection
            }
        }
    }
}

/**
 * Extension function to get or create the config extension for a Gradle project.
 *
 * This function provides a convenient way to access and configure the ConfigExtension
 * for a Gradle project. If the extension doesn't exist yet, it creates a new one.
 *
 * @param configure A configuration block to apply to the extension
 * @return The configured ConfigExtension instance
 */
fun Project.config(configure: ConfigExtension.() -> Unit = {}): ConfigExtension {
    val extension = extensions.findByType(ConfigExtension::class.java) ?: extensions.create(
        ConfigExtension.NAME, ConfigExtension::class.java, this
    )

    extension.configure()
    return extension
}
