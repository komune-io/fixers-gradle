package io.komune.fixers.gradle.plugin.check

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.dependencies.FixersPluginVersions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.sonarqube.gradle.SonarExtension

class CheckPlugin : Plugin<Project> {
	override fun apply(target: Project) {
		target.gradle.projectsEvaluated {
			val config = target.rootProject.extensions.fixers
			configureSonarQube(target)
			if (config?.detekt?.disable?.orNull != true) {
				target.configureDetekt()
			}
			target.subprojects {
				configureJacoco()
			}
		}
	}

	private fun configureSonarQube(target: Project) {
		target.plugins.apply("org.sonarqube")
		target.extensions.configure(SonarExtension::class.java) {
			val fixers = target.rootProject.extensions.fixers
			properties {
				fixers?.bundle?.let { property("sonar.projectName", it.name) }

				fixers?.sonar?.let { sonar ->
					property("sonar.sources", "**/src/*Main/kotlin")
					property("sonar.projectKey", sonar.projectKey.get())
					property("sonar.organization", sonar.organization.get())
					property("sonar.host.url", sonar.url.get())
					property("sonar.language", sonar.language.get())
					property("sonar.exclusions", sonar.exclusions.get())
					property("sonar.kotlin.detekt.reportPaths", sonar.detekt.get())
					property("sonar.pullrequest.github.summary_comment", sonar.githubSummaryComment.get())
					property("sonar.coverage.jacoco.xmlReportPaths", sonar.jacoco.get())
				}

				property("detekt.sonar.kotlin.config.path", "${target.rootDir}/detekt.yml")
				property("sonar.verbose", true)
			}
		}
	}

	private fun Project.configureJacoco() {
		plugins.withType(JavaPlugin::class.java).whenPluginAdded {
			plugins.apply("jacoco")
			extensions.configure(JacocoPluginExtension::class.java) {
				toolVersion = FixersPluginVersions.jacoco
			}
			tasks.withType<JacocoReport>().configureEach {
				isEnabled = true
				reports {
					html.required.set(true)
					xml.required.set(true)
				}
				dependsOn(tasks.named("test"))
			}
			tasks.withType<Test>().configureEach {
				useJUnitPlatform()
				finalizedBy(tasks.named("jacocoTestReport"))
			}
		}
	}
}
