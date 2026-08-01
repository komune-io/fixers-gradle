package io.komune.fixers.gradle.dependencies

/**
 * Tool versions applied by the fixers Gradle plugins to consumer projects.
 * The Kotlin version is derived from the applied Kotlin Gradle Plugin.
 * Dependency versions are managed by consumers through the f2-bom, c2-bom or s2-bom.
 */
object PluginVersions {
	/**
	 * org.jacoco:jacoco
	 */
	const val jacoco = "0.8.14"
}
