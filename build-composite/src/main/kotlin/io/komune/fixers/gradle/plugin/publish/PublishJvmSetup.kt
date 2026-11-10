package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.plugin.kotlin.JvmPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the

object PublishJvmSetup {

    fun setupJVMPublish(project: Project) {
        project.plugins.withType(JvmPlugin::class.java) {
            project.setupJvmPublishJar()
            project.setupPublication()
            project.setupExistingPublication()
        }
    }

    private fun Project.setupExistingPublication() {
        val variantName = name
        configure<PublishingExtension> {
            publications.configureEach {
                val mavenPublication = this
                if (mavenPublication is MavenPublication) {
                    mavenPublication.artifactId = pluginMarkerArtifactId(variantName, name)
                    tasks.findByName("javadocJar")?.let { task -> mavenPublication.artifact(task) }
                    tasks.findByName("sourcesJar")?.let { task -> mavenPublication.artifact(task) }
                }
            }
        }
    }


    internal fun pluginMarkerArtifactId(projectName: String, publicationName: String): String {
        if(publicationName.endsWith("PluginMarkerMaven")) {
            return publicationName.replace("PluginMarkerMaven", ".gradle.plugin")
        }
        return projectName
    }

    private fun Project.setupPublication() {
        if (extensions.findByType(JavaPluginExtension::class.java) != null) {
            createMavenPublicationIfAbsent("kotlin")
        }
    }

    private fun Project.setupJvmPublishJar() {
        plugins.withType(JvmPlugin::class.java) {
            tasks.register("javadocJar", Jar::class.java) {
                val javadoc = tasks.named("javadoc")
                dependsOn.add(javadoc)
                archiveClassifier.set("javadoc")
                from(javadoc)
            }

            tasks.register("sourcesJar", Jar::class.java) {
                archiveClassifier.set("sources")
                val sourceSets = project.the<SourceSetContainer>()
                from(sourceSets["main"].allSource)
            }
        }
    }
}
