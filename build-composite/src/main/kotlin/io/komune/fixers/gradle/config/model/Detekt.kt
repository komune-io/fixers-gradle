package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration for Detekt static code analysis.
 */
class Detekt(
	private val project: Project
) {
	/**
	 * Whether to disable Detekt static code analysis.
	 */
	val disable: Property<Boolean> = project.property(
		envKey = "DETEKT_DISABLE",
		projectKey = "detekt.disable",
		defaultValue = false
	)

	/**
	 * The baseline file for Detekt.
	 */
	val baseline: Property<String> = project.property(
		envKey = "DETEKT_BASELINE",
		projectKey = "detekt.baseline"
	)

	/**
	 * The configuration file for Detekt.
	 */
	val config: Property<String> = project.property(
		envKey = "DETEKT_CONFIG",
		projectKey = "detekt.config",
		defaultValue = "detekt.yml"
	)

	/**
	 * Whether to build upon the default Detekt configuration.
	 * When true, custom config extends the default rather than replacing it.
	 */
	val buildUponDefaultConfig: Property<Boolean> = project.property(
		envKey = "DETEKT_BUILD_UPON_DEFAULT_CONFIG",
		projectKey = "detekt.buildUponDefaultConfig",
		defaultValue = true
	)

	/**
	 * Whether to enable the checkstyle (XML) report.
	 */
	val checkstyleReport: Property<Boolean> = project.property(
		envKey = "DETEKT_REPORT_CHECKSTYLE",
		projectKey = "detekt.report.checkstyle",
		defaultValue = true
	)

	/**
	 * Whether to enable the HTML report.
	 */
	val htmlReport: Property<Boolean> = project.property(
		envKey = "DETEKT_REPORT_HTML",
		projectKey = "detekt.report.html",
		defaultValue = true
	)

	/**
	 * Whether to enable the SARIF report.
	 */
	val sarifReport: Property<Boolean> = project.property(
		envKey = "DETEKT_REPORT_SARIF",
		projectKey = "detekt.report.sarif",
		defaultValue = true
	)

	/**
	 * Whether to enable the Markdown report.
	 */
	val markdownReport: Property<Boolean> = project.property(
		envKey = "DETEKT_REPORT_MARKDOWN",
		projectKey = "detekt.report.markdown",
		defaultValue = true
	)

	/**
	 * Merges properties from the source Detekt into this Detekt.
	 * Properties are only merged if the target property is not present and the source property is present.
	 *
	 * @param source The source Detekt to merge from
	 * @return This Detekt after merging
	 */
	fun mergeFrom(source: Detekt): Detekt {
		disable.mergeIfNotPresent(source.disable)
		baseline.mergeIfNotPresent(source.baseline)
		config.mergeIfNotPresent(source.config)
		buildUponDefaultConfig.mergeIfNotPresent(source.buildUponDefaultConfig)
		checkstyleReport.mergeIfNotPresent(source.checkstyleReport)
		htmlReport.mergeIfNotPresent(source.htmlReport)
		sarifReport.mergeIfNotPresent(source.sarifReport)
		markdownReport.mergeIfNotPresent(source.markdownReport)

		return this
	}
}
