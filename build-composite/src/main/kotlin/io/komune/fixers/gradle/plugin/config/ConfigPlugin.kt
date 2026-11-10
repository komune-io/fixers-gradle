package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.utils.resolveVersion
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Set on `gradle` by the fixers settings plugin, which declares every repository of the build.
 */
internal const val SETTINGS_PLUGIN_MARKER = "io.komune.fixers.gradle.settings"

internal const val CONFIG_PLUGIN_ID = "io.komune.fixers.gradle.config"

/**
 * Creates the `fixers` extension, sets the version of every project from the `VERSION` file and
 * merges the root's configuration into the subprojects. From the root, it forces a patched
 * BouncyCastle on KGP's PGP helper configuration, enforces the Spring Boot image convention and
 * the yarn resolutions on every project, and registers the TypeScript contract tasks.
 *
 * It fails in a build without the fixers settings plugin, and in a module whose root project
 * does not apply it: the conventions above would then be left out without a word.
 */
class ConfigPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val root: Project = target.rootProject
        if (!target.gradle.extensions.extraProperties.has(SETTINGS_PLUGIN_MARKER)) {
            throw GradleException(
                "$CONFIG_PLUGIN_ID needs the fixers settings plugin: " +
                    "apply the $SETTINGS_PLUGIN_MARKER plugin in settings.gradle.kts."
            )
        }
        if (target != root && !root.plugins.hasPlugin(ConfigPlugin::class.java)) {
            throw GradleException(
                "${target.path} applies $CONFIG_PLUGIN_ID, the root project does not: apply it in the root build.gradle.kts."
            )
        }
        target.pluginManager.apply("base")

        val extension = target.config()
        if (target == root) {
            root.setVersion()
            // Kotlin subprojects do not all apply a fixers plugin (MPP modules skip this one, some
            // apply plain `kotlin("jvm")`), yet KGP creates the configuration in each of them.
            root.allprojects {
                alignBouncyCastle()
                enforceBootImageConvention()
            }
            root.registerYarnResolutionCheck()
        }

        // Use gradle.projectsEvaluated for configuration cache compatibility
        // This runs once after all projects are evaluated, avoiding per-project afterEvaluate
        target.gradle.projectsEvaluated {
            log(target, root, extension)

            if (target == root) {
                val bundleGroup = extension.bundle.group.orNull
                if (bundleGroup != null) {
                    root.group = bundleGroup
                }
                root.subprojects.forEach { subproject ->
                    subproject.mergeConfig(extension)
                    if (bundleGroup != null) {
                        subproject.group = bundleGroup
                    }
                }
                root.configureKt2Ts(extension)
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
        target.logger.info("Fixers Config $config")
        target.logger.info("====================")
    }

    private fun Project.setVersion() {
        version = resolveVersion() ?: version
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
        subprojectConfig.jacoco.mergeFrom(rootConfig.jacoco)
        subprojectConfig.jdk.mergeFrom(rootConfig.jdk)
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
