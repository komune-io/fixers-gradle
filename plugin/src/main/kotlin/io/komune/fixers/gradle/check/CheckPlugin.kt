package io.komune.fixers.gradle.check

import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import io.komune.gradle.config.ConfigExtension
import io.komune.gradle.config.fixers
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.sonarqube.gradle.SonarQubeExtension

class CheckPlugin : Plugin<Project> {
	override fun apply(target: Project) {
		target.afterEvaluate {
			val config = target.rootProject.extensions.fixers
			configureSonarQube(target)
			if(config?.detekt?.disable != true) {
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
			target.extensions.configure(SonarQubeExtension::class.java) {
				val fixers = target.extensions.findByType(ConfigExtension::class.java)
				properties {
					property("sonar.projectKey", fixers?.bundle?.id as Any)
					property("sonar.projectName", fixers.bundle.name)
					property("sonar.host.url", fixers.sonar.url)
					property("sonar.login", fixers.sonar.login)
					property ("detekt.sonar.kotlin.config.path", "${rootDir}/detekt.yml")

					property("sonar.verbose", true)
					property("sonar.coverage.jacoco.xmlReportPaths",
						"${target.rootDir}/**/build/reports/jacoco/test/jacocoTestReport.xml")
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
