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
		projectKey = "detekt.config"
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

		return this
	}
}
