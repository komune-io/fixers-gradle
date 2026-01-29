package io.komune.fixers.gradle.plugin.check

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.report.ReportMergeTask
import dev.detekt.gradle.Detekt
import io.komune.fixers.gradle.config.fixers
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

fun Project.getDetektReportMergeXmlFile(): Provider<RegularFile> {
    return rootProject.layout.buildDirectory.file("reports/detekt/merge.xml")
}

fun Project.configureDetekt() {
    val fixersDetekt = rootProject.extensions.fixers?.detekt

    val detektReportMergeSarif = tasks.register<ReportMergeTask>("detektReportMergeSarif") {
        output.set(layout.buildDirectory.file("reports/detekt/merge.sarif"))
    }

    val detektReportMergeXml = rootProject.tasks.register<ReportMergeTask>("detektReportMergeXml") {
        output.set(getDetektReportMergeXmlFile())
    }

    allprojects {
        plugins.apply("dev.detekt")

        pluginManager.withPlugin("dev.detekt") {
            val detectXmlPath = layout.buildDirectory.file("reports/detekt/detekt.xml")
            val detectSarifPath = layout.buildDirectory.file("reports/detekt/detekt.sarif")
            extensions.configure(DetektExtension::class.java) {
                buildUponDefaultConfig.set(fixersDetekt?.buildUponDefaultConfig?.get() ?: true)

                val configFile = rootDir.resolve(fixersDetekt?.config?.get() ?: "detekt.yml")
                if (configFile.exists()) {
                    config.setFrom(configFile)
                }
                // If the config file does not exist, Detekt uses its built-in default config

                fixersDetekt?.baseline?.orNull?.let {
                    baseline.set(file(it))
                }
            }

            tasks.withType<Detekt>().configureEach {
                val checkstyleEnabled = fixersDetekt?.checkstyleReport?.get() ?: true
                val htmlEnabled = fixersDetekt?.htmlReport?.get() ?: true
                val sarifEnabled = fixersDetekt?.sarifReport?.get() ?: true
                val markdownEnabled = fixersDetekt?.markdownReport?.get() ?: true

                reports {
                    checkstyle.required.set(checkstyleEnabled)
                    if (checkstyleEnabled) {
                        checkstyle.outputLocation.set(file(detectXmlPath))
                    }
                    html.required.set(htmlEnabled)
                    sarif.required.set(sarifEnabled)
                    if (sarifEnabled) {
                        sarif.outputLocation.set(file(detectSarifPath))
                    }
                    markdown.required.set(markdownEnabled)
                }

                finalizedBy(detektReportMergeXml, detektReportMergeSarif)
            }

            detektReportMergeSarif.configure {
                input.from(detectSarifPath)
            }

            detektReportMergeXml.configure {
                input.from(detectXmlPath)
            }
        }
    }
}
