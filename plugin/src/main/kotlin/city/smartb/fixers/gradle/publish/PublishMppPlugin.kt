package city.smartb.fixers.gradle.publish

import city.smartb.fixers.gradle.kotlin.MppPlugin
import city.smartb.gradle.config.model.Publication
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign

object PublishMppPlugin {

	fun setupMppPublish(project: Project, publication: Publication?) {
		project.plugins.withType(MppPlugin::class.java) {
			project.setupMppPublishJar()
			project.setupPublication(publication)
		}
	}

	private fun Project.setupPublication(publication: Publication?) {
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
				publication?.let {
					mavenPublication.pom(publication.configure)
				}

				val javadocJarTask = tasks["javadocJar"]
				mavenPublication.artifact(javadocJarTask)
			}
		}
	}

	internal fun getArtifactId(projectName: String, publicationName: String): String {
		return "${projectName}${"-$publicationName".takeUnless { "kotlinMultiplatform" in publicationName }.orEmpty()}"
	}

	private fun Project.setupMppPublishJar() {
		tasks.register("javadocJar", Jar::class.java) {
			archiveClassifier.set("javadoc")
		}
	}
}
