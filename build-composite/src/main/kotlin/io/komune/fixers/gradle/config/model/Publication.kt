package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom
import org.gradle.kotlin.dsl.property

/**
 * Configuration for Maven publication.
 */
class Publication(
    private val project: Project
) {
    /**
     * The action to configure the Maven POM.
     */
    val configure: Property<Action<MavenPom>> = project.objects.property()

    /**
     * Merges properties from the source Publication into this Publication.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Publication to merge from
     * @return This Publication after merging
     */
    fun mergeFrom(source: Publication): Publication {
        configure.mergeIfNotPresent(source.configure)

        return this
    }
}
