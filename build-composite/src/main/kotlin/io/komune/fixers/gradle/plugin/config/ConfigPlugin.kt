package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Repositories
import io.komune.fixers.gradle.config.utils.resolveVersion
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

/**
 * Plugin that applies and configures the ConfigExtension for a project.
 * 
 * This plugin:
 * 1. Creates a ConfigExtension for the target project if it doesn't exist
 * 2. Logs configuration information
 * 3. Propagates configuration from the root project to subprojects
 * 4. Configures Kotlin to TypeScript generation if enabled
 * 5. From the root, forces a patched BouncyCastle on KGP's PGP helper configuration
 *    in every project (see [alignBouncyCastle])
 */
/**
 * Set on `gradle` by the fixers settings plugin, which declares every repository in the settings
 * and forbids project repositories: this plugin then adds none.
 */
internal const val SETTINGS_PLUGIN_MARKER = "io.komune.fixers.gradle.settings"

class ConfigPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Apply base plugin to get lifecycle tasks (assemble, build, check) if not already present
        if (!target.plugins.hasPlugin("base")) {
            target.plugins.apply("base")
        }

        val extension = target.config()
        val root: Project = target.rootProject
        // Once per build: every module applies this plugin, and a later pass would overwrite
        // a version a build script set in between.
        val extra = root.extensions.extraProperties
        if (!extra.has(VERSION_SET)) {
            extra.set(VERSION_SET, true)
            root.setVersion()
        }

        // From the root, across every project: Kotlin subprojects do not all apply a fixers
        // plugin (MPP modules skip ConfigPlugin, some apply plain `kotlin("jvm")`), yet KGP
        // creates the configuration in each of them.
        if (target == root) {
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
                if (!root.gradle.extensions.extraProperties.has(SETTINGS_PLUGIN_MARKER)) {
                    root.allprojects.forEach { proj ->
                        proj.configureRepositories(extension.repositories)
                    }
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
        subprojectConfig.kt2Ts.mergeFrom(rootConfig.kt2Ts)
        subprojectConfig.npm.mergeFrom(rootConfig.npm)
        subprojectConfig.pom.mergeFrom(rootConfig.pom)
        subprojectConfig.repositories.mergeFrom(rootConfig.repositories)
        subprojectConfig.sonar.mergeFrom(rootConfig.sonar)
    }

    private fun Project.configureRepositories(repos: Repositories) {
        val handler = repositories
        if (repos.mavenLocal.get()) {
            handler.mavenLocal()
        }
        if (repos.mavenCentral.get()) {
            handler.mavenCentral()
        }
        if (repos.sonatypeSnapshots.get()) {
            handler.maven(Action<MavenArtifactRepository> {
                url = java.net.URI("https://central.sonatype.com/repository/maven-snapshots")
            })
        }
        repos.mavenUrls.get().forEach { repoUrl ->
            handler.maven(Action<MavenArtifactRepository> {
                url = java.net.URI(repoUrl)
            })
        }
    }

    private companion object {
        const val VERSION_SET = "io.komune.fixers.gradle.config.versionSet"
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
