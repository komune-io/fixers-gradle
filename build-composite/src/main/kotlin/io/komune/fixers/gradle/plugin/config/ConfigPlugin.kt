package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.utils.versionFromFile
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin that applies and configures the ConfigExtension for a project.
 * 
 * This plugin:
 * 1. Creates a ConfigExtension for the target project if it doesn't exist
 * 2. Logs configuration information
 * 3. Propagates configuration from the root project to subprojects
 * 4. Configures Kotlin to TypeScript generation if enabled
 */
class ConfigPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.config()
        val root: Project = target.rootProject
        root.setVersion()
        target.afterEvaluate {
            log(target, root, extension)

            if (target == root) {
                root.subprojects.forEach { subproject ->
                    subproject.mergeConfig(extension)
                }
            }

            root.extensions.fixers?.let { config ->
                target.configureKt2Ts(config)
            }
        }
    }

    private fun log(
        target: Project,
        root: Project,
        config: ConfigExtension,
    ) {
        target.logger.info("=== Config Plugin ===")
        target.logger.info("Target project: ${target.name}")
        target.logger.info("Root project: ${root.name}")
        target.logger.info("Fixers Config ${config}")
        target.logger.info("====================")
    }

    private fun Project.setVersion() {
        version = versionFromFile() ?: version
        subprojects.forEach { subproject ->
            subproject.setVersion()
        }
    }
    private fun Project.mergeConfig(rootConfig: ConfigExtension) {
        val subprojectConfig = this.config()

        with(subprojectConfig.bundle) {
            // Basic properties
            mergePropertyIfNotPresent(id, rootConfig.bundle.id)
            mergePropertyIfNotPresent(description, rootConfig.bundle.description)
            mergePropertyIfNotPresent(url, rootConfig.bundle.url)

            // License properties
            mergePropertyIfNotPresent(licenseName, rootConfig.bundle.licenseName)
            mergePropertyIfNotPresent(licenseUrl, rootConfig.bundle.licenseUrl)
            mergePropertyIfNotPresent(licenseDistribution, rootConfig.bundle.licenseDistribution)

            // Developer properties
            mergePropertyIfNotPresent(developerId, rootConfig.bundle.developerId)
            mergePropertyIfNotPresent(developerName, rootConfig.bundle.developerName)
            mergePropertyIfNotPresent(developerOrganization, rootConfig.bundle.developerOrganization)
            mergePropertyIfNotPresent(developerOrganizationUrl, rootConfig.bundle.developerOrganizationUrl)

            // SCM properties
            mergePropertyIfNotPresent(scmConnection, rootConfig.bundle.scmConnection)
            mergePropertyIfNotPresent(scmDeveloperConnection, rootConfig.bundle.scmDeveloperConnection)
        }

        mergePublish(rootConfig, subprojectConfig)
    }

    /**
     * Helper function to merge a property from source to target if target is not present and source is present
     */
    private fun <T> mergePropertyIfNotPresent(
        targetProp: org.gradle.api.provider.Property<T>, 
        sourceProp: org.gradle.api.provider.Property<T>
    ) {
        if (!targetProp.isPresent && sourceProp.isPresent) {
            targetProp.set(sourceProp)
        }
    }

    /**
     * Helper function to merge a list property from source to target if target is not present and source is present
     */
    private fun <T> mergeListPropertyIfNotPresent(
        targetProp: org.gradle.api.provider.ListProperty<T>, 
        sourceProp: org.gradle.api.provider.ListProperty<T>
    ) {
        if (!targetProp.isPresent && sourceProp.isPresent) {
            targetProp.set(sourceProp)
        }
    }

    private fun mergePublish(
        rootConfig: ConfigExtension,
        subprojectConfig: ConfigExtension,
    ) {
        with(subprojectConfig.publish) {
            // URL properties
            mergePropertyIfNotPresent(mavenCentralUrl, rootConfig.publish.mavenCentralUrl)
            mergePropertyIfNotPresent(mavenSnapshotsUrl, rootConfig.publish.mavenSnapshotsUrl)

            // Package deployment properties
            mergePropertyIfNotPresent(pkgDeployType, rootConfig.publish.pkgDeployType)
            mergePropertyIfNotPresent(pkgMavenRepo, rootConfig.publish.pkgMavenRepo)

            // GitHub properties
            mergePropertyIfNotPresent(pkgGithubUsername, rootConfig.publish.pkgGithubUsername)
            mergePropertyIfNotPresent(pkgGithubToken, rootConfig.publish.pkgGithubToken)

            // Signing properties
            mergePropertyIfNotPresent(signingKey, rootConfig.publish.signingKey)
            mergePropertyIfNotPresent(signingPassword, rootConfig.publish.signingPassword)

            // Gradle plugin properties (ListProperty)
            mergeListPropertyIfNotPresent(gradlePlugin, rootConfig.publish.gradlePlugin)
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
