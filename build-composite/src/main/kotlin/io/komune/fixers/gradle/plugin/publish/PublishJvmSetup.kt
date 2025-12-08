package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.utils.pom
import io.komune.fixers.gradle.plugin.kotlin.JvmPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the

object PublishJvmSetup {

	fun setupJVMPublish(project: Project, config: ConfigExtension) {
		project.plugins.withType(JvmPlugin::class.java) {
			project.setupJvmPublishJar()
			project.setupPublication(config)
			project.setupExistingPublication(config)
		}
	}

	private fun Project.setupExistingPublication(config: ConfigExtension) {
		val variantName = name
		configure<PublishingExtension> {
			publications.configureEach {
				(this as? MavenPublication)?.let { mavenPublication ->
					mavenPublication.artifactId = getArtifactId(variantName, name)
					val publication = project.pom(config.bundle)
					mavenPublication.pom(publication)
					tasks.findByName("javadocJar")?.let { mavenPublication.artifact(it) }
					tasks.findByName("sourcesJar")?.let { mavenPublication.artifact(it) }
				}
			}
		}
	}


	internal fun getArtifactId(projectName: String, publicationName: String): String {
		if(publicationName.endsWith("PluginMarkerMaven")) {
			return publicationName.replace("PluginMarkerMaven", ".gradle.plugin")
		}
		return projectName
	}

	private fun Project.setupPublication(config: ConfigExtension) {
		project.extensions.findByType(PublishingExtension::class.java)?.let { publishing ->
			extensions.findByType(JavaPluginExtension::class.java)?.let {
				publishing.publications {
					if (findByName("maven") == null) {
						create<MavenPublication>("maven") {
							from(components["kotlin"])
							val publication = project.pom(config.bundle)
							pom(publication)
						}
					}
				}
			}
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
