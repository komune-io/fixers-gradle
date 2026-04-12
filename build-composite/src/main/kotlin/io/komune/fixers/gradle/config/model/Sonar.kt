package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration for SonarQube/SonarCloud analysis.
 */
class Sonar(
    private val project: Project
) {
    /**
     * The URL of the SonarQube/SonarCloud server.
     */
    val url: Property<String> = project.property(
        envKey = "FIXERS_SONAR_URL",
        projectKey = "fixers.sonar.url",
        defaultValue = "https://sonarcloud.io"
    )

    /**
     * The organization key in SonarCloud.
     */
    val organization: Property<String> = project.property(
        envKey = "FIXERS_SONAR_ORGANIZATION",
        projectKey = "fixers.sonar.organization",
        defaultValue = ""
    )

    /**
     * The project key in SonarQube/SonarCloud.
     */
    val projectKey: Property<String> = project.property(
        envKey = "FIXERS_SONAR_PROJECT_KEY",
        projectKey = "fixers.sonar.projectKey",
        defaultValue = ""
    )

    /**
     * The path to the JaCoCo XML report.
     * Includes paths for both standard JVM projects (test/) and Kotlin Multiplatform projects (jvmTest/).
     */
    val jacoco: Property<String> = project.property(
        envKey = "FIXERS_SONAR_JACOCO",
        projectKey = "fixers.sonar.jacoco",
        defaultValue = "**/build/reports/jacoco/**/${Jacoco.DEFAULT_XML_REPORT_FILENAME}"
    )

    /**
     * The language of the project.
     */
    val language: Property<String> = project.property(
        envKey = "FIXERS_SONAR_LANGUAGE",
        projectKey = "fixers.sonar.language",
        defaultValue = "kotlin"
    )

    /**
     * The path to the Detekt XML report.
     * Uses the merged report from the root project.
     */
    val detekt: Property<String> = project.property(
        envKey = "FIXERS_SONAR_DETEKT_REPORT_PATHS",
        projectKey = "fixers.sonar.detekt.reportPaths",
        defaultValue = "build/reports/detekt/merge.xml"
    )

    /**
     * The exclusions pattern for SonarQube/SonarCloud analysis.
     */
    val exclusions: Property<String> = project.property(
        envKey = "FIXERS_SONAR_EXCLUSIONS",
        projectKey = "fixers.sonar.exclusions",
        defaultValue = "**/build/**,**/.gradle/**,**/node_modules/**,**/buildSrc/**,**/*.java"
    )

    /**
     * Whether to add a summary comment to GitHub pull requests.
     */
    val githubSummaryComment: Property<String> = project.property(
        envKey = "FIXERS_SONAR_GITHUB_SUMMARY_COMMENT",
        projectKey = "fixers.sonar.githubSummaryComment",
        defaultValue = "true"
    )

    /**
     * The sources pattern for SonarQube/SonarCloud analysis.
     */
    val sources: Property<String> = project.property(
        envKey = "FIXERS_SONAR_SOURCES",
        projectKey = "fixers.sonar.sources",
        defaultValue = "."
    )

    /**
     * The inclusions pattern for SonarQube/SonarCloud analysis.
     */
    val inclusions: Property<String> = project.property(
        envKey = "FIXERS_SONAR_INCLUSIONS",
        projectKey = "fixers.sonar.inclusions",
        defaultValue = "**/src/*main*/kotlin/**/*.kt"
    )

    /**
     * Whether to enable verbose output for SonarQube/SonarCloud analysis.
     */
    val verbose: Property<Boolean> = project.property(
        envKey = "FIXERS_SONAR_VERBOSE",
        projectKey = "fixers.sonar.verbose",
        defaultValue = true
    )

    /**
     * The path to the Detekt configuration file for Sonar Kotlin plugin.
     */
    val detektConfigPath: Property<String> = project.property(
        envKey = "FIXERS_SONAR_DETEKT_CONFIG_PATH",
        projectKey = "fixers.sonar.detektConfigPath",
        defaultValue = "detekt.yml"
    )

    /**
     * Authentication token for SonarQube/SonarCloud.
     * Bridged to `sonar.token` via the sonarqube extension DSL in
     * `SonarQubeConfigurator.buildSonarProperties()`.
     */
    val token: Property<String> = project.property(
        envKey = "FIXERS_SONAR_TOKEN",
        projectKey = "fixers.sonar.token"
    )

    /**
     * Custom Sonar properties that will be added to the configuration.
     */
    val customProperties: MutableMap<String, String> = mutableMapOf()

    /**
     * Configures custom Sonar properties.
     *
     * Example:
     * ```kotlin
     * sonar {
     *     properties {
     *         property("sonar.coverage.exclusions", "src/generated/**/*")
     *     }
     * }
     * ```
     */
    fun properties(configure: Action<SonarProperties>) {
        configure.execute(SonarProperties(customProperties))
    }

    /**
     * DSL helper class for configuring custom Sonar properties.
     */
    class SonarProperties(private val properties: MutableMap<String, String>) {
        /**
         * Sets a custom Sonar property.
         *
         * @param key The property key (e.g., "sonar.coverage.exclusions")
         * @param value The property value
         */
        fun property(key: String, value: String) {
            properties[key] = value
        }
    }

    /**
     * Merges properties from the source Sonar into this Sonar.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Sonar to merge from
     * @return This Sonar after merging
     */
    fun mergeFrom(source: Sonar): Sonar {
        url.mergeIfNotPresent(source.url)
        organization.mergeIfNotPresent(source.organization)
        projectKey.mergeIfNotPresent(source.projectKey)
        jacoco.mergeIfNotPresent(source.jacoco)
        language.mergeIfNotPresent(source.language)
        detekt.mergeIfNotPresent(source.detekt)
        exclusions.mergeIfNotPresent(source.exclusions)
        inclusions.mergeIfNotPresent(source.inclusions)
        githubSummaryComment.mergeIfNotPresent(source.githubSummaryComment)
        sources.mergeIfNotPresent(source.sources)
        verbose.mergeIfNotPresent(source.verbose)
        detektConfigPath.mergeIfNotPresent(source.detektConfigPath)
        token.mergeIfNotPresent(source.token)

        // Merge custom properties (source properties are added only if not already present)
        source.customProperties.forEach { (key, value) ->
            customProperties.putIfAbsent(key, value)
        }

        return this
    }

    companion object
}

/**
 * Creates a Sonar instance configured for SonarCloud.
 */
fun Sonar.Companion.sonarCloud(project: Project) = Sonar(project)
