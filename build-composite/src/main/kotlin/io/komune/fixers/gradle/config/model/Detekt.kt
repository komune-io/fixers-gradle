package io.komune.fixers.gradle.config.model

import org.gradle.api.Project
import org.gradle.api.provider.Property
import io.komune.fixers.gradle.config.utils.property

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
}
