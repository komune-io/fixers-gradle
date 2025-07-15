package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
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
        envKey = "SONAR_URL",
        projectKey = "sonar.url",
        defaultValue = "https://sonarcloud.io"
    )

    /**
     * The organization key in SonarCloud.
     */
    val organization: Property<String> = project.property(
        envKey = "SONAR_ORGANIZATION",
        projectKey = "sonar.organization",
        defaultValue = ""
    )

    /**
     * The project key in SonarQube/SonarCloud.
     */
    val projectKey: Property<String> = project.property(
        envKey = "SONAR_PROJECT_KEY",
        projectKey = "sonar.projectKey",
        defaultValue = ""
    )

    /**
     * The path to the JaCoCo XML report.
     */
    val jacoco: Property<String> = project.property(
        envKey = "SONAR_JACOCO",
        projectKey = "sonar.jacoco",
        defaultValue = "${project.rootDir}/**/build/reports/jacoco/test/jacocoTestReport.xml"
    )

    /**
     * The language of the project.
     */
    val language: Property<String> = project.property(
        envKey = "SONAR_LANGUAGE",
        projectKey = "sonar.language",
        defaultValue = "kotlin"
    )

    /**
     * The path to the Detekt XML report.
     */
    val detekt: Property<String> = project.property(
        envKey = "SONAR_KOTLIN_DETEKT_REPORT_PATHS",
        projectKey = "sonar.kotlin.detekt.reportPaths",
        defaultValue = "build/reports/detekt/detekt.xml"
    )

    /**
     * The exclusions pattern for SonarQube/SonarCloud analysis.
     */
    val exclusions: Property<String> = project.property(
        envKey = "SONAR_EXCLUSIONS",
        projectKey = "sonar.pullrequest.github.summary_comment",
        defaultValue = "**/*.java"
    )

    /**
     * Whether to add a summary comment to GitHub pull requests.
     */
    val githubSummaryComment: Property<String> = project.property(
        envKey = "SONAR_GITHUB_SUMMARY_COMMENT",
        projectKey = "sonar.githubSummaryComment",
        defaultValue = "true"
    )

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
        githubSummaryComment.mergeIfNotPresent(source.githubSummaryComment)

        return this
    }

    companion object
}

/**
 * Creates a Sonar instance configured for SonarCloud.
 */
fun Sonar.Companion.sonarCloud(project: Project) = Sonar(project)
