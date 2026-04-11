package io.komune.fixers.gradle.plugin.check

import io.komune.fixers.gradle.config.fixers
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

class CheckPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        bridgeSonarToken()
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

    /**
     * Bridges the `FIXERS_SONAR_TOKEN` env var (the `FIXERS_*` namespace) to the
     * `sonar.token` system property that the `org.sonarqube` gradle plugin reads
     * at task-execution time.
     *
     * This is the only way to let users export `FIXERS_SONAR_TOKEN` locally or in
     * CI instead of the legacy `SONAR_TOKEN` env var name — the sonarqube plugin's
     * env var name is hard-coded and cannot be remapped.
     *
     * Only sets the system property if it is not already set, so explicit
     * `-Dsonar.token=...` always wins. Does NOT affect the `sonarqube-scan-action`
     * used in `sec-workflow.yml`, which runs in a separate process and reads
     * `SONAR_TOKEN` directly — that workflow still maps
     * `SONAR_TOKEN: ${{ secrets.FIXERS_SONAR_TOKEN }}` at step level.
     */
    private fun bridgeSonarToken() {
        val sonarToken = System.getenv("FIXERS_SONAR_TOKEN")
        if (!sonarToken.isNullOrEmpty() && System.getProperty("sonar.token") == null) {
            System.setProperty("sonar.token", sonarToken)
        }
    }
}

/**
 * Task to generate sonar-project.properties file from fixers configuration.
 * Configuration cache compatible - all values captured during configuration time.
 */
@DisableCachingByDefault(because = "Generates a properties file that depends on project structure")
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
                appendLine("#")
                appendLine("# Or in gradle.properties:")
                appendLine("# fixers.sonar.organization=your-org")
                appendLine("# fixers.sonar.projectKey=your-project-key")
                appendLine("#")
                appendLine("# Or via environment variables:")
                appendLine("# FIXERS_SONAR_ORGANIZATION=your-org")
                appendLine("# FIXERS_SONAR_PROJECT_KEY=your-project-key")
            }
        }

        file.writeText(properties)
        logger.lifecycle("Generated sonar-project.properties at: ${file.absolutePath}")
    }
}
