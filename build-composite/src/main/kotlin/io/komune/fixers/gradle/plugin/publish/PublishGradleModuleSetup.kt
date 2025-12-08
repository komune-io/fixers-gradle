package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.utils.pom
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication

class PublishGradleModuleSetup(
    private val project: Project,
    private val config: ConfigExtension,
    private val publications: PublicationContainer,
) {

    fun configurePluginPublications() {
        // Configure main plugin publication
        publications.findByName("pluginMaven")?.let { publication ->
            (publication as MavenPublication).pom(getMavenCentralMetadata())
        }

        // Configure explicitly listed marker publications
        val markerPublications = config.publish.gradlePlugin.get()
        markerPublications.forEach { publicationName ->
            publications.findByName(publicationName)?.let { publication ->
                (publication as MavenPublication).pom(getPomMetadata())
            }
        }

        // Also configure ALL PluginMarkerMaven publications to ensure they have required POM metadata
        publications.withType(MavenPublication::class.java).configureEach {
            if (name.endsWith("PluginMarkerMaven")) {
                pom(getPomMetadata())
            }
        }
    }

    private fun getPomMetadata(): Action<MavenPom> = project.pom(config.bundle)

    private fun getMavenCentralMetadata(): Action<MavenPom> = Action {
        project.pom(config.bundle).execute(this)
    }

}

fun PublicationContainer.configureMavenPublications(
    project: Project,
    configExtension: ConfigExtension
) {
    val artifactSetup = PublishGradleModuleSetup(project, configExtension, this)
    val hasPublishPlugin = project.plugins.hasPlugin("com.gradle.plugin-publish")
    if (hasPublishPlugin) {
        artifactSetup.configurePluginPublications()
    }
}
