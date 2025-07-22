package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.utils.pom
import io.komune.fixers.gradle.plugin.kotlin.MppPlugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign

object PublishMppSetup {

	fun setupMppPublish(project: Project, config: ConfigExtension) {
		project.plugins.withType(MppPlugin::class.java) {
			project.setupMppPublishJar()
			project.setupPublication(config)
		}
	}

	private fun Project.setupPublication(config: ConfigExtension) {
		val projectName = name

		//TODO Check correction of https://github.com/gradle/gradle/issues/26091
		tasks.withType<AbstractPublishToMaven>().configureEach {
			val signingTasks = tasks.withType<Sign>()
			mustRunAfter(signingTasks)
		}
		configure<PublishingExtension> {
			publications.withType<MavenPublication> {
				val mavenPublication = this
				mavenPublication.artifactId = getArtifactId(projectName, mavenPublication.name)
				val publication = project.pom(config.bundle)
				mavenPublication.pom(publication)

				val javadocJarTask = tasks["javadocJar"]
				mavenPublication.artifact(javadocJarTask)
			}
		}
	}

	private fun getArtifactId(projectName: String, publicationName: String): String {
		return "${projectName}${"-$publicationName".takeUnless { "kotlinMultiplatform" in publicationName }.orEmpty()}"
	}

	private fun Project.setupMppPublishJar() {
		tasks.register("javadocJar", Jar::class.java) {
			archiveClassifier.set("javadoc")
		}
	}
}
