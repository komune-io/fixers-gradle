package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * The reports Detekt writes. The rules are fixers' own: see `configureDetekt`.
 */
class Detekt(
    private val project: Project
) {
    /**
     * Whether to enable the checkstyle (XML) report.
     */
    val checkstyleReport: Property<Boolean> = project.property(
        envKey = "FIXERS_DETEKT_REPORT_CHECKSTYLE",
        projectKey = "fixers.detekt.report.checkstyle",
        defaultValue = true
    )

    /**
     * Whether to enable the HTML report.
     */
    val htmlReport: Property<Boolean> = project.property(
        envKey = "FIXERS_DETEKT_REPORT_HTML",
        projectKey = "fixers.detekt.report.html",
        defaultValue = true
    )

    /**
     * Whether to enable the SARIF report.
     */
    val sarifReport: Property<Boolean> = project.property(
        envKey = "FIXERS_DETEKT_REPORT_SARIF",
        projectKey = "fixers.detekt.report.sarif",
        defaultValue = true
    )

    /**
     * Whether to enable the Markdown report.
     */
    val markdownReport: Property<Boolean> = project.property(
        envKey = "FIXERS_DETEKT_REPORT_MARKDOWN",
        projectKey = "fixers.detekt.report.markdown",
        defaultValue = true
    )

    /**
     * Merges properties from the source Detekt into this Detekt.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Detekt to merge from
     */
    fun mergeFrom(source: Detekt) {
        checkstyleReport.mergeIfNotPresent(source.checkstyleReport)
        htmlReport.mergeIfNotPresent(source.htmlReport)
        sarifReport.mergeIfNotPresent(source.sarifReport)
        markdownReport.mergeIfNotPresent(source.markdownReport)
    }
}
