package io.komune.fixers.gradle.plugin.check

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Jacoco
import io.komune.fixers.gradle.dependencies.FixersPluginVersions
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
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
					property("sonar.sources", sonar.sources.get())
					property("sonar.projectKey", sonar.projectKey.get())
					property("sonar.organization", sonar.organization.get())
					property("sonar.host.url", sonar.url.get())
					property("sonar.language", sonar.language.get())
					property("sonar.exclusions", sonar.exclusions.get())
					property("sonar.inclusions", sonar.inclusions.get())
					property("sonar.kotlin.detekt.reportPaths", sonar.detekt.get())
					property("sonar.pullrequest.github.summary_comment", sonar.githubSummaryComment.get())
					property("sonar.coverage.jacoco.xmlReportPaths", sonar.jacoco.get())
					property("detekt.sonar.kotlin.config.path", "${target.rootDir}/${sonar.detektConfigPath.get()}")
					property("sonar.verbose", sonar.verbose.get())
				}
			}
		}

		// Register task to generate sonar-project.properties
		val sonarConfig = target.rootProject.extensions.fixers?.sonar
		target.tasks.register<GenerateSonarPropertiesTask>("generateSonarProperties") {
			group = "verification"
			description = "Generates sonar-project.properties file in build directory"
			outputFile.set(target.layout.buildDirectory.file("sonar-project.properties"))
			sonarConfig?.let { sonar ->
				organization.set(sonar.organization)
				projectKey.set(sonar.projectKey)
				sources.set(sonar.sources)
				inclusions.set(sonar.inclusions)
				exclusions.set(sonar.exclusions)
				jacoco.set(sonar.jacoco)
				detekt.set(sonar.detekt)
			}
		}

		// Run generateSonarProperties before detekt or assemble
		target.tasks.matching { it.name == "detekt" || it.name == "assemble" }.configureEach {
			dependsOn(target.tasks.named("generateSonarProperties"))
		}
	}

	private fun Project.configureJacoco() {
		val jacocoConfig = rootProject.extensions.fixers?.jacoco
		val jacocoEnabled = jacocoConfig?.enabled?.getOrElse(true) ?: true

		// Configure JaCoCo for standard JVM projects (with JavaPlugin)
		plugins.withType(JavaPlugin::class.java) {
			if (jacocoEnabled) {
				applyJacocoPlugin()
				configureJacocoReportTasks(jacocoConfig)
				tasks.withType<Test>().configureEach {
					finalizedBy(tasks.named("jacocoTestReport"))
				}
			}
		}

		// Configure JaCoCo for Kotlin Multiplatform projects (JVM target)
		plugins.withId("org.jetbrains.kotlin.multiplatform") {
			if (jacocoEnabled) {
				applyJacocoPlugin()
				configureJacocoForMultiplatform(jacocoConfig)
			}
		}
	}

	private fun Project.applyJacocoPlugin() {
		plugins.apply("jacoco")
		extensions.configure(JacocoPluginExtension::class.java) {
			toolVersion = FixersPluginVersions.jacoco
		}
	}

	private fun Project.configureJacocoReportTasks(jacocoConfig: Jacoco?) {
		tasks.withType<JacocoReport>().configureEach {
			isEnabled = jacocoConfig?.enabled?.getOrElse(true) ?: true
			reports {
				html.required.set(jacocoConfig?.htmlReport?.getOrElse(true) ?: true)
				xml.required.set(jacocoConfig?.xmlReport?.getOrElse(true) ?: true)
			}
		}
	}

	private fun Project.configureJacocoForMultiplatform(jacocoConfig: Jacoco?) {
		val kmpExtension = extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
		val jvmTarget = kmpExtension.targets.findByName("jvm") ?: return

		// Configure JaCoCo for jvmTest task
		tasks.matching { it.name == "jvmTest" }.configureEach {
			if (this is Test) {
				extensions.configure(JacocoTaskExtension::class.java) {
					isEnabled = jacocoConfig?.enabled?.getOrElse(true) ?: true
				}
			}
		}

		// Register JaCoCo report task for JVM tests
		(tasks.findByName("jvmTest") as? Test)?.let { jvmTestTask ->
			tasks.register<JacocoReport>("jacocoJvmTestReport") {
				dependsOn(jvmTestTask)
				isEnabled = jacocoConfig?.enabled?.getOrElse(true) ?: true

				val jvmCompilation = jvmTarget.compilations.getByName("main")
				classDirectories.setFrom(jvmCompilation.output.classesDirs)
				sourceDirectories.setFrom(jvmCompilation.allKotlinSourceSets.map { it.kotlin.sourceDirectories })
				executionData.setFrom(layout.buildDirectory.file("jacoco/jvmTest.exec"))

				val xmlFilename = jacocoConfig?.xmlReportFilename?.getOrElse(Jacoco.DEFAULT_XML_REPORT_FILENAME)
					?: Jacoco.DEFAULT_XML_REPORT_FILENAME
				reports {
					html.required.set(jacocoConfig?.htmlReport?.getOrElse(true) ?: true)
					xml.required.set(jacocoConfig?.xmlReport?.getOrElse(true) ?: true)
					html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jvmTest/html"))
					xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jvmTest/$xmlFilename"))
				}
			}

			// Finalize jvmTest with JaCoCo report
			tasks.matching { it.name == "jvmTest" }.configureEach {
				finalizedBy(tasks.named("jacocoJvmTestReport"))
			}
		}
	}
}

/**
 * Task to generate sonar-project.properties file from fixers configuration.
 * Configuration cache compatible - all values captured during configuration time.
 */
abstract class GenerateSonarPropertiesTask : DefaultTask() {

	@get:org.gradle.api.tasks.Input
	@get:org.gradle.api.tasks.Optional
	abstract val organization: Property<String>

	@get:org.gradle.api.tasks.Input
	@get:org.gradle.api.tasks.Optional
	abstract val projectKey: Property<String>

	@get:org.gradle.api.tasks.Input
	@get:org.gradle.api.tasks.Optional
	abstract val sources: Property<String>

	@get:org.gradle.api.tasks.Input
	@get:org.gradle.api.tasks.Optional
	abstract val inclusions: Property<String>

	@get:org.gradle.api.tasks.Input
	@get:org.gradle.api.tasks.Optional
	abstract val exclusions: Property<String>

	@get:org.gradle.api.tasks.Input
	@get:org.gradle.api.tasks.Optional
	abstract val jacoco: Property<String>

	@get:org.gradle.api.tasks.Input
	@get:org.gradle.api.tasks.Optional
	abstract val detekt: Property<String>

	@get:org.gradle.api.tasks.OutputFile
	abstract val outputFile: RegularFileProperty

	@TaskAction
	fun generate() {
		val file = outputFile.get().asFile
		file.parentFile.mkdirs()

		val properties = buildString {
			val hasConfig = organization.orNull?.isNotBlank() == true && projectKey.orNull?.isNotBlank() == true
			if (hasConfig) {
				appendLine("sonar.organization=${organization.get()}")
				appendLine("sonar.projectKey=${projectKey.get()}")
				appendLine()
				appendLine("# Source directories")
				appendLine("sonar.sources=${sources.getOrElse(".")}")
				appendLine("sonar.inclusions=${inclusions.getOrElse("")}")
				appendLine("sonar.exclusions=${exclusions.getOrElse("")}")
				appendLine()
				appendLine("# JaCoCo coverage reports (JVM: test/, Multiplatform: jvmTest/)")
				appendLine("sonar.coverage.jacoco.xmlReportPaths=${jacoco.getOrElse("")}")
				appendLine()
				appendLine("# Detekt report (merged from all modules)")
				appendLine("sonar.kotlin.detekt.reportPaths=${detekt.getOrElse("")}")
			} else {
				appendLine("# No fixers sonar configuration found")
				appendLine("# Configure sonar in your build.gradle.kts:")
				appendLine("# fixers {")
				appendLine("#     sonar {")
				appendLine("#         organization = \"your-org\"")
				appendLine("#         projectKey = \"your-project-key\"")
				appendLine("#     }")
				appendLine("# }")
			}
		}

		file.writeText(properties)
		logger.lifecycle("Generated sonar-project.properties at: ${file.absolutePath}")
	}
}
