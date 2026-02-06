package io.komune.fixers.gradle.plugin.check

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Jacoco
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

class CheckPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.gradle.projectsEvaluated {
            val config = target.rootProject.extensions.fixers

            // Use SonarQubeConfigurator for SonarQube setup
            val sonarConfigurator = SonarQubeConfigurator(target)
            sonarConfigurator.configure(config?.sonar, config?.bundle)

            // Configure Detekt if not disabled
            if (config?.detekt?.disable?.orNull != true) {
                target.configureDetekt()
            }

            // Configure JaCoCo for subprojects
            target.subprojects {
                val jacocoConfigurator = JacocoConfigurator(this)
                jacocoConfigurator.configure(config?.jacoco)
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

    @get:org.gradle.api.tasks.Input
    @get:org.gradle.api.tasks.Optional
    abstract val customProperties: MapProperty<String, String>

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

                // Add custom properties
                val custom = customProperties.getOrElse(emptyMap())
                if (custom.isNotEmpty()) {
                    appendLine()
                    appendLine("# Custom properties")
                    custom.forEach { (key, value) ->
                        appendLine("$key=$value")
                    }
                }
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
