package io.komune.fixers.gradle.dependencies

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Extension to get the fixers version catalog from a project
 */
val Project.fixersCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("fixers")

/**
 * Extension to check if fixers catalog is available
 */
@Suppress("SwallowedException")
fun Project.hasFixersCatalog(): Boolean {
    return try {
        extensions.getByType<VersionCatalogsExtension>().find("fixers").isPresent
    } catch (e: Exception) {
        false
    }
}

/**
 * Extension to safely get the fixers version catalog
 */
fun Project.findFixersCatalog(): VersionCatalog? {
    return if (hasFixersCatalog()) fixersCatalog else null
}
