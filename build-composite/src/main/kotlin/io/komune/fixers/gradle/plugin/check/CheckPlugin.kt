package io.komune.fixers.gradle.plugin.check

import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import io.komune.fixers.gradle.config.ConfigExtension
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
			if(config?.detekt?.disable?.orNull != true) {
				target.configureDetekt()
			}
			target.subprojects {
				configureJacoco()
			}
		}
		forceKotlinVersion(target)
	}

	/**
	 *  https://detekt.dev/docs/gettingstarted/gradle#dependencies
	 */
	private fun forceKotlinVersion(target: Project) {
		target.plugins.withId("io.spring.dependency-management") {
			target.configurations.configureEach {
				resolutionStrategy.eachDependency {
					if (requested.group == "org.jetbrains.kotlin") {
						useVersion(getSupportedKotlinVersion())
					}
				}
			}
		}
		target.subprojects.forEach { subproject ->
			forceKotlinVersion(subproject)
		}
	}

	private fun configureSonarQube(target: Project) {
		target.plugins.apply("org.sonarqube")
		target.extensions.configure(SonarExtension::class.java) {
			// Use root project's config - this is where fixers { bundle { ... } } is configured
			val fixers = target.rootProject.extensions.fixers
			properties {
				fixers?.bundle?.let { property("sonar.projectName", it.name) }

				fixers?.sonar?.let { sonar ->

					property("sonar.sources", "**/src/*Main/kotlin")
					if (sonar.projectKey.isPresent) {
						property("sonar.projectKey", sonar.projectKey.get())
					}
					if (sonar.organization.isPresent) {
						property("sonar.organization", sonar.organization.get())
					}
					if (sonar.url.isPresent) {
						property("sonar.host.url", sonar.url.get())
					}
					if (sonar.language.isPresent) {
						property("sonar.language", sonar.language.get())
					}
					if (sonar.exclusions.isPresent) {
						property("sonar.exclusions", sonar.exclusions.get())
					}

					if (sonar.detekt.isPresent) {
						property("sonar.kotlin.detekt.reportPaths", sonar.detekt.get())
					}
					if (sonar.githubSummaryComment.isPresent) {
						property("sonar.pullrequest.github.summary_comment", sonar.githubSummaryComment.get())
					}
					if (sonar.jacoco.isPresent) {
						property("sonar.coverage.jacoco.xmlReportPaths", sonar.jacoco.get())
					}
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
