package io.komune.fixers.gradle.plugin.check

import io.komune.fixers.gradle.config.model.Jacoco
import io.komune.fixers.gradle.dependencies.FixersPluginVersions
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configurator for JaCoCo code coverage settings.
 * Extracted from CheckPlugin to enable unit testing.
 */
class JacocoConfigurator(
    private val project: Project
) {
    /**
     * Configures JaCoCo for the project based on the provided settings.
     *
     * @param jacocoConfig The JaCoCo configuration settings
     */
    fun configure(jacocoConfig: Jacoco?) {
        val jacocoEnabled = jacocoConfig?.enabled?.getOrElse(true) ?: true
        if (!jacocoEnabled) {
            return
        }

        // Configure JaCoCo for standard JVM projects (with JavaPlugin)
        project.plugins.withType(JavaPlugin::class.java) {
            applyJacocoPlugin()
            configureJacocoReportTasks(jacocoConfig)
            project.tasks.withType<Test>().configureEach {
                finalizedBy(project.tasks.named("jacocoTestReport"))
            }
        }

        // Configure JaCoCo for Kotlin Multiplatform projects (JVM target)
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            applyJacocoPlugin()
            configureJacocoForMultiplatform(jacocoConfig)
        }
    }

    /**
     * Applies the JaCoCo plugin to the project.
     */
    fun applyJacocoPlugin() {
        project.plugins.apply("jacoco")
        project.extensions.configure(JacocoPluginExtension::class.java) {
            toolVersion = FixersPluginVersions.jacoco
        }
    }

    /**
     * Configures JaCoCo report tasks with the provided settings.
     */
    fun configureJacocoReportTasks(jacocoConfig: Jacoco?) {
        project.tasks.withType<JacocoReport>().configureEach {
            isEnabled = jacocoConfig?.enabled?.getOrElse(true) ?: true
            reports {
                html.required.set(jacocoConfig?.htmlReport?.getOrElse(true) ?: true)
                xml.required.set(jacocoConfig?.xmlReport?.getOrElse(true) ?: true)
            }
        }
    }

    /**
     * Configures JaCoCo for Kotlin Multiplatform projects.
     */
    fun configureJacocoForMultiplatform(jacocoConfig: Jacoco?) {
        val kmpExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
        val jvmTarget = kmpExtension.targets.findByName("jvm") ?: return

        // Configure JaCoCo for jvmTest task
        project.tasks.matching { it.name == "jvmTest" }.configureEach {
            if (this is Test) {
                extensions.configure(JacocoTaskExtension::class.java) {
                    isEnabled = jacocoConfig?.enabled?.getOrElse(true) ?: true
                }
            }
        }

        // Register JaCoCo report task for JVM tests.
        // Note: findByName is safe here because this runs inside CheckPlugin's projectsEvaluated callback,
        // meaning all project configuration (including KMP task registration) has already completed.
        (project.tasks.findByName("jvmTest") as? Test)?.let { jvmTestTask ->
            project.tasks.register<JacocoReport>("jacocoJvmTestReport") {
                dependsOn(jvmTestTask)
                isEnabled = jacocoConfig?.enabled?.getOrElse(true) ?: true

                val jvmCompilation = jvmTarget.compilations.getByName("main")
                classDirectories.setFrom(jvmCompilation.output.classesDirs)
                sourceDirectories.setFrom(
                    jvmCompilation.allKotlinSourceSets.map { it.kotlin.sourceDirectories }
                )
                executionData.setFrom(project.layout.buildDirectory.file("jacoco/jvmTest.exec"))

                val xmlFilename = jacocoConfig?.xmlReportFilename
                    ?.getOrElse(Jacoco.DEFAULT_XML_REPORT_FILENAME)
                    ?: Jacoco.DEFAULT_XML_REPORT_FILENAME
                reports {
                    html.required.set(jacocoConfig?.htmlReport?.getOrElse(true) ?: true)
                    xml.required.set(jacocoConfig?.xmlReport?.getOrElse(true) ?: true)
                    html.outputLocation.set(
                        project.layout.buildDirectory.dir("reports/jacoco/jvmTest/html")
                    )
                    xml.outputLocation.set(
                        project.layout.buildDirectory.file("reports/jacoco/jvmTest/$xmlFilename")
                    )
                }
            }

            // Finalize jvmTest with JaCoCo report
            project.tasks.matching { it.name == "jvmTest" }.configureEach {
                finalizedBy(project.tasks.named("jacocoJvmTestReport"))
            }
        }
    }

    /**
     * Checks if JaCoCo is enabled based on configuration.
     *
     * @param jacocoConfig The JaCoCo configuration
     * @return true if JaCoCo should be enabled
     */
    internal fun isEnabled(jacocoConfig: Jacoco?): Boolean {
        return jacocoConfig?.enabled?.getOrElse(true) ?: true
    }

    /**
     * Checks if HTML reports should be generated.
     *
     * @param jacocoConfig The JaCoCo configuration
     * @return true if HTML reports should be generated
     */
    internal fun isHtmlReportEnabled(jacocoConfig: Jacoco?): Boolean {
        return jacocoConfig?.htmlReport?.getOrElse(true) ?: true
    }

    /**
     * Checks if XML reports should be generated.
     *
     * @param jacocoConfig The JaCoCo configuration
     * @return true if XML reports should be generated
     */
    internal fun isXmlReportEnabled(jacocoConfig: Jacoco?): Boolean {
        return jacocoConfig?.xmlReport?.getOrElse(true) ?: true
    }

    /**
     * Gets the XML report filename.
     *
     * @param jacocoConfig The JaCoCo configuration
     * @return The XML report filename
     */
    internal fun getXmlReportFilename(jacocoConfig: Jacoco?): String {
        return jacocoConfig?.xmlReportFilename?.getOrElse(Jacoco.DEFAULT_XML_REPORT_FILENAME)
            ?: Jacoco.DEFAULT_XML_REPORT_FILENAME
    }
}
