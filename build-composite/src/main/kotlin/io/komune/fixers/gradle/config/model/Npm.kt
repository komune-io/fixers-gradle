package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration for NPM package publishing.
 */
class Npm(
    private val project: Project
) {
    /**
     * Whether to publish NPM packages.
     */
    val publish: Property<Boolean> = project.property(
        envKey = "NPM_PUBLISH",
        projectKey = "npm.publish",
        defaultValue = true
    )

    /**
     * The organization name for NPM packages.
     */
    val organization: Property<String> = project.property(
        envKey = "NPM_ORGANIZATION",
        projectKey = "npm.organization",
        defaultValue = "komune-io"
    )

    /**
     * Whether to clean NPM packages before publishing.
     */
    val clean: Property<Boolean> = project.property<Boolean>(
        envKey = "NPM_CLEAN",
        projectKey = "npm.clean",
        defaultValue = true
    )

    /**
     * The version for NPM packages. If not specified, the project version will be used.
     */
    val version: Property<String> = project.property(
        envKey = "NPM_VERSION",
        projectKey = "npm.version"
    )

    /**
     * Merges properties from the source Npm into this Npm.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Npm to merge from
     * @return This Npm after merging
     */
    fun mergeFrom(source: Npm): Npm {
        publish.mergeIfNotPresent(source.publish)
        organization.mergeIfNotPresent(source.organization)
        clean.mergeIfNotPresent(source.clean)
        version.mergeIfNotPresent(source.version)

        return this
    }
}
