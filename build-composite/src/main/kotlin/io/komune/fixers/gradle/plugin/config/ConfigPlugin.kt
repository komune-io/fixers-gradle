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

        // Use the model-specific merge methods
        subprojectConfig.bundle.mergeFrom(rootConfig.bundle)
        subprojectConfig.publish.mergeFrom(rootConfig.publish)
        subprojectConfig.detekt.mergeFrom(rootConfig.detekt)
        subprojectConfig.jdk.mergeFrom(rootConfig.jdk)
        subprojectConfig.kt2Ts.mergeFrom(rootConfig.kt2Ts)
        subprojectConfig.npm.mergeFrom(rootConfig.npm)
        subprojectConfig.pom.mergeFrom(rootConfig.pom)
        subprojectConfig.sonar.mergeFrom(rootConfig.sonar)
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
