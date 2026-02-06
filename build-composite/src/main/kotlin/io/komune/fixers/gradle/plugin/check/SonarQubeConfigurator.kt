package io.komune.fixers.gradle.plugin.check

import io.komune.fixers.gradle.config.model.Bundle
import io.komune.fixers.gradle.config.model.Sonar
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
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
                bundle?.let { property("sonar.projectName", it.name) }

                sonar?.let { s ->
                    property("sonar.sources", s.sources.get())
                    property("sonar.projectKey", s.projectKey.get())
                    property("sonar.organization", s.organization.get())
                    property("sonar.host.url", s.url.get())
                    property("sonar.language", s.language.get())
                    property("sonar.exclusions", s.exclusions.get())
                    property("sonar.inclusions", s.inclusions.get())
                    property("sonar.kotlin.detekt.reportPaths", s.detekt.get())
                    property("sonar.pullrequest.github.summary_comment", s.githubSummaryComment.get())
                    property("sonar.coverage.jacoco.xmlReportPaths", s.jacoco.get())
                    property("detekt.sonar.kotlin.config.path", "${project.rootDir}/${s.detektConfigPath.get()}")
                    property("sonar.verbose", s.verbose.get())

                    // Apply custom properties
                    s.customProperties.forEach { (key, value) ->
                        property(key, value)
                    }
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

        properties["sonar.sources"] = sonar.sources.get()
        properties["sonar.projectKey"] = sonar.projectKey.get()
        properties["sonar.organization"] = sonar.organization.get()
        properties["sonar.host.url"] = sonar.url.get()
        properties["sonar.language"] = sonar.language.get()
        properties["sonar.exclusions"] = sonar.exclusions.get()
        properties["sonar.inclusions"] = sonar.inclusions.get()
        properties["sonar.kotlin.detekt.reportPaths"] = sonar.detekt.get()
        properties["sonar.pullrequest.github.summary_comment"] = sonar.githubSummaryComment.get()
        properties["sonar.coverage.jacoco.xmlReportPaths"] = sonar.jacoco.get()
        properties["detekt.sonar.kotlin.config.path"] = "${project.rootDir}/${sonar.detektConfigPath.get()}"
        properties["sonar.verbose"] = sonar.verbose.get()

        // Add custom properties
        sonar.customProperties.forEach { (key, value) ->
            properties[key] = value
        }

        return properties
    }
}
