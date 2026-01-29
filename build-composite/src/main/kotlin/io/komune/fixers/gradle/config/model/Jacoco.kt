package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration for JaCoCo code coverage.
 */
class Jacoco(
    private val project: Project
) {
    /**
     * Whether to enable JaCoCo code coverage.
     */
    val enabled: Property<Boolean> = project.property(
        envKey = "JACOCO_ENABLED",
        projectKey = "jacoco.enabled",
        defaultValue = true
    )

    /**
     * Whether to enable the HTML report.
     */
    val htmlReport: Property<Boolean> = project.property(
        envKey = "JACOCO_REPORT_HTML",
        projectKey = "jacoco.report.html",
        defaultValue = true
    )

    /**
     * Whether to enable the XML report.
     */
    val xmlReport: Property<Boolean> = project.property(
        envKey = "JACOCO_REPORT_XML",
        projectKey = "jacoco.report.xml",
        defaultValue = true
    )

    /**
     * Merges properties from the source Jacoco into this Jacoco.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Jacoco to merge from
     * @return This Jacoco after merging
     */
    fun mergeFrom(source: Jacoco): Jacoco {
        enabled.mergeIfNotPresent(source.enabled)
        htmlReport.mergeIfNotPresent(source.htmlReport)
        xmlReport.mergeIfNotPresent(source.xmlReport)

        return this
    }
}
