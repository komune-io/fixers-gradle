package io.komune.fixers.gradle.config.model

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
}
