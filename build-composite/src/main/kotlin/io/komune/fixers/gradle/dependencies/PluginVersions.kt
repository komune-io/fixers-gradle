package io.komune.fixers.gradle.dependencies

/**
 * Tool versions applied by the fixers Gradle plugins to consumer projects.
 * Dependency versions are managed by consumers through the f2-bom, c2-bom or s2-bom.
 */
object PluginVersions {
	const val kotlin = "2.3.20"
	/**
	 * org.jacoco:jacoco
	 */
	const val jacoco = "0.8.14"
}
