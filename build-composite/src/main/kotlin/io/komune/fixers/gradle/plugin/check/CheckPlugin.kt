package io.komune.fixers.gradle.plugin.check

import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.fixers
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
		target.afterEvaluate {
			val config = target.rootProject.extensions.fixers
			configureSonarQube(target)
			if(config?.detekt?.disable?.orNull != true) {
				configureDetekt()
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
		target.afterEvaluate {
			if (target.plugins.hasPlugin("io.spring.dependency-management")) {
				target.configurations.all {
					resolutionStrategy.eachDependency {
						if (requested.group == "org.jetbrains.kotlin") {
							useVersion(getSupportedKotlinVersion())
						}
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
		target.afterEvaluate {
			target.extensions.configure(SonarExtension::class.java) {
				val fixers = target.extensions.findByType(ConfigExtension::class.java)
				properties {
					fixers?.bundle?.let { property("sonar.projectName", it.name) }

					fixers?.sonar?.let { sonar ->

						property("sonar.sources", "**/src/*Main/kotlin")
						property("sonar.projectKey", sonar.projectKey.get())
						property("sonar.organization", sonar.organization.get())
						property("sonar.host.url", sonar.url.get())
						property("sonar.language", sonar.language.get())
						property("sonar.exclusions", sonar.exclusions.get())

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

					property("detekt.sonar.kotlin.config.path", "${rootDir}/detekt.yml")

					property("sonar.verbose", true)

				}
			}
		}
	}

	private fun Project.configureJacoco() {
		plugins.withType(JavaPlugin::class.java).whenPluginAdded {
			plugins.apply("jacoco")
			extensions.configure(JacocoPluginExtension::class.java) {
				toolVersion = "0.8.7"
			}
			tasks.withType<JacocoReport> {
				isEnabled = true
				reports {
					html.required.set(true)
					xml.required.set(true)
				}
				dependsOn(tasks.named("test"))
			}
			tasks.withType<Test> {
				useJUnitPlatform()
				finalizedBy(tasks.named("jacocoTestReport"))
			}
		}
	}
}
