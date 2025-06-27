package io.komune.fixers.gradle.publishing

import io.komune.fixers.gradle.config.ConfigExtension
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
    private val extension: PublishingExtension,
    private val configExtension: ConfigExtension,
    private val publications: PublicationContainer,
) {

    fun configurePluginPublications() {
        publications.findByName("pluginMaven")?.let { publication ->
            (publication as MavenPublication).pom(getMavenCentralMetadata())
        }

        val markerPublications = extension.markerPublications.get()
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


    private fun getPomMetadata(): Action<MavenPom> = Action {
        getPomConfiguration().execute(this)
    }

    private fun getMavenCentralMetadata(): Action<MavenPom> = Action {
        name.set(project.name)
        description.set(project.description)
        getPomConfiguration().execute(this)
    }


    private fun getPomConfiguration(): Action<MavenPom> = Action {
        licenses {
            license {
                name.set(configExtension.licenseName.get())
                url.set(configExtension.licenseUrl.get())
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set(configExtension.organizationId.get())
                name.set(configExtension.organizationName.get())
                organization.set(configExtension.organizationId.get())
                organizationUrl.set(configExtension.organizationUrl.get())
            }
        }
        url.set(configExtension.repositoryUrl.get())
        scm {
            url.set(configExtension.repositoryUrl.get())
        }
    }

}

fun PublicationContainer.configureMavenPublications(
    project: Project,
    extension: PublishingExtension,
    configExtension: ConfigExtension
) {
    val mavenConfigurer = MavenConfigurer(project, extension, configExtension, this)
    val hasPublishPlugin = project.plugins.hasPlugin("com.gradle.plugin-publish")

    if (hasPublishPlugin) {
        mavenConfigurer.configurePluginPublications()
    } else {
        mavenConfigurer.configureLibraryPublication()
    }
}
