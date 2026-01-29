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
fun Project.hasFixersCatalog(): Boolean {
    return extensions.findByType(VersionCatalogsExtension::class.java)
        ?.find("fixers")?.isPresent
        ?: false
}

/**
 * Extension to safely get the fixers version catalog
 */
fun Project.findFixersCatalog(): VersionCatalog? {
    return if (hasFixersCatalog()) fixersCatalog else null
}
