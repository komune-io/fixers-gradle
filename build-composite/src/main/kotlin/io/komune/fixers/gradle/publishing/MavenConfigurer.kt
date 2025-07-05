package io.komune.fixers.gradle.publishing

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.pom
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the

class MavenConfigurer(
    private val project: Project,
    private val publishConfiguration: PublishConfiguration,
    private val configExtension: ConfigExtension,
    private val publications: PublicationContainer,
) {

    fun configurePluginPublications() {
        publications.findByName("pluginMaven")?.let { publication ->
            (publication as MavenPublication).pom(getMavenCentralMetadata())
        }

        val markerPublications = publishConfiguration.markerPublications.get()
        markerPublications.forEach { publicationName ->
            publications.findByName(publicationName)?.let { publication ->
                (publication as MavenPublication).pom(getPomMetadata())
            }
        }
    }

    fun configureLibraryPublication() {
        val sourcesJarTask = project.tasks.register<Jar>("sourcesJar") {
            from(project.the<SourceSetContainer>().getByName("main").allJava)
            archiveClassifier.set("sources")
        }

        val javadocJarTask = project.tasks.register<Jar>("javadocJar") {
            dependsOn("javadoc")
            from(project.tasks.named("javadoc").get().outputs)
            archiveClassifier.set("javadoc")
        }
        publications.create<MavenPublication>("mavenJava") {
            from(project.components.getByName("java"))
            artifact(sourcesJarTask.get())
            artifact(javadocJarTask.get())
            pom(getMavenCentralMetadata())
        }
    }


    private fun getPomMetadata(): Action<MavenPom> = project.pom(configExtension.bundle)

    private fun getMavenCentralMetadata(): Action<MavenPom> = Action {
        project.pom(configExtension.bundle).execute(this)
    }

}

fun PublicationContainer.configureMavenPublications(
    project: Project,
    publishConfiguration: PublishConfiguration,
    configExtension: ConfigExtension
) {
    val mavenConfigurer = MavenConfigurer(project, publishConfiguration, configExtension, this)
    val hasPublishPlugin = project.plugins.hasPlugin("com.gradle.plugin-publish")

    if (hasPublishPlugin) {
        mavenConfigurer.configurePluginPublications()
    } else {
        mavenConfigurer.configureLibraryPublication()
    }
}
