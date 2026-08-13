package io.komune.fixers.gradle.plugin.check

import io.komune.fixers.gradle.config.model.Bundle
import io.komune.fixers.gradle.config.model.Sonar
import java.io.File
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.sonarqube.gradle.SonarExtension

/**
 * Configurator for SonarQube plugin settings.
 * Extracted from CheckPlugin to enable unit testing.
 */
class SonarQubeConfigurator(
    private val project: Project
) {
    /**
     * Applies the SonarQube plugin and configures it based on the provided settings.
     *
     * @param sonar The Sonar configuration settings
     * @param bundle Optional bundle configuration for project name
     */
    fun configure(sonar: Sonar?, bundle: Bundle?) {
        project.plugins.apply("org.sonarqube")
        configureSonarExtension(sonar, bundle)
        registerGenerateSonarPropertiesTask(sonar)
    }

    /**
     * Configures the SonarQube extension with the provided settings.
     * This method is public to allow testing of the configuration logic.
     */
    fun configureSonarExtension(sonar: Sonar?, bundle: Bundle?) {
        project.extensions.configure(SonarExtension::class.java) {
            properties {
                if (sonar != null) {
                    buildSonarProperties(sonar, bundle).forEach { (key, value) ->
                        property(key, value)
                    }
                } else {
                    bundle?.name?.orNull?.let { property("sonar.projectName", it) }
                }
            }
        }
    }

    /**
     * Registers the GenerateSonarPropertiesTask.
     */
    fun registerGenerateSonarPropertiesTask(sonar: Sonar?) {
        project.tasks.register<GenerateSonarPropertiesTask>("generateSonarProperties") {
            group = "verification"
            description = "Generates sonar-project.properties file in build directory"
            outputFile.set(project.layout.buildDirectory.file("sonar-project.properties"))
            sonar?.let { s ->
                organization.set(s.organization)
                projectKey.set(s.projectKey)
                sources.set(s.sources)
                inclusions.set(s.inclusions)
                exclusions.set(s.exclusions)
                jacoco.set(s.jacoco)
                detekt.set(s.detekt)
                customProperties.set(s.customProperties)
            }
        }

        // Run generateSonarProperties before detekt or assemble
        project.tasks.matching { it.name == "detekt" || it.name == "assemble" }.configureEach {
            dependsOn(project.tasks.named("generateSonarProperties"))
        }
    }

    /**
     * Builds Sonar properties map from configuration.
     * This method is useful for testing the property generation logic.
     *
     * @param sonar The Sonar configuration
     * @param bundle Optional bundle configuration
     * @return Map of Sonar property key-value pairs
     */
    fun buildSonarProperties(sonar: Sonar, bundle: Bundle?): Map<String, Any> {
        val properties = mutableMapOf<String, Any>()

        bundle?.name?.orNull?.let { properties["sonar.projectName"] = it }

        // sonar.sources/sonar.inclusions are intentionally NOT set here: the Gradle
        // sonar scanner derives sources and tests from each module's source sets, and
        // a root-level sources="." + inclusions pattern makes files indexed twice.
        // They remain part of the generated sonar-project.properties used by the
        // standalone scanner (see GenerateSonarPropertiesTask).
        properties["sonar.projectKey"] = sonar.projectKey.get()
        properties["sonar.organization"] = sonar.organization.get()
        properties["sonar.host.url"] = sonar.url.get()
        properties["sonar.language"] = sonar.language.get()
        properties["sonar.exclusions"] = sonar.exclusions.get()
        properties["sonar.kotlin.detekt.reportPaths"] = resolveDetektReportPaths(sonar.detekt.get())
        properties["sonar.pullrequest.github.summary_comment"] = sonar.githubSummaryComment.get()
        properties["sonar.coverage.jacoco.xmlReportPaths"] = sonar.jacoco.get()
        properties["detekt.sonar.kotlin.config.path"] = "${project.rootDir}/${sonar.detektConfigPath.get()}"
        properties["sonar.verbose"] = sonar.verbose.get()

        sonar.token.orNull?.let { properties["sonar.token"] = it }

        // Custom properties last so they can override any computed value above
        sonar.customProperties.forEach { (key, value) ->
            properties[key] = value
        }

        return properties
    }

    /**
     * Makes the Detekt report path(s) module-independent.
     *
     * Detekt reports are merged into a single file living in the **root** project's build
     * directory (see [getDetektReportMergeXmlFile]), but Sonar resolves relative report paths
     * against the base directory of *each analysed module*. A relative path therefore makes every
     * non-root module look for `<module>/build/reports/detekt/merge.xml`, a file that never
     * exists, and the analysis logs `Unable to import detekt report file(s)` for each of them
     * while no Detekt issue is ever imported.
     *
     * Relative paths are resolved against the root project directory; absolute paths are kept
     * as-is so a project with a non-standard layout can still point Sonar wherever it wants
     * (via `fixers.sonar.detekt.reportPaths` / `FIXERS_SONAR_DETEKT_REPORT_PATHS`).
     *
     * @param paths comma-separated report paths, as configured on the Sonar model
     * @return the same list with every relative entry made absolute
     */
    internal fun resolveDetektReportPaths(paths: String): String {
        return paths.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(",") { path ->
                val file = File(path)
                if (file.isAbsolute) file.path else File(project.rootDir, path).path
            }
    }
}
