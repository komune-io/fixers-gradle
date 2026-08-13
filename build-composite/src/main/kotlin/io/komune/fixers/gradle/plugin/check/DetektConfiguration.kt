package io.komune.fixers.gradle.plugin.check

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.report.ReportMergeTask
import io.komune.fixers.gradle.config.fixers
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

fun Project.getDetektReportMergeXmlFile(): Provider<RegularFile> {
    return rootProject.layout.buildDirectory.file("reports/detekt/merge.xml")
}

/**
 * All Detekt reports of this project with the given extension, excluding the merged report itself
 * (the root project writes `merge.xml`/`merge.sarif` in the very same directory).
 *
 * A project can hold several Detekt tasks - Kotlin Multiplatform registers one per source set and
 * per compilation - so the merge tasks must collect every report, not only the one produced by the
 * default `detekt` task.
 */
internal fun Project.detektReportsWithExtension(extension: String): Provider<FileTree> {
    return layout.buildDirectory.dir("reports/detekt").map { reportsDir ->
        reportsDir.asFileTree.matching {
            include("*.$extension")
            exclude("merge.$extension")
        }
    }
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

                // Reports are named after the task: a project may run several Detekt tasks
                // (Kotlin Multiplatform registers one per source set / compilation) and a shared
                // output location makes them overwrite each other's report.
                val detektXmlPath = layout.buildDirectory.file("reports/detekt/$name.xml")
                val detektSarifPath = layout.buildDirectory.file("reports/detekt/$name.sarif")

                reports {
                    checkstyle.required.set(checkstyleEnabled)
                    if (checkstyleEnabled) {
                        checkstyle.outputLocation.set(file(detektXmlPath))
                    }
                    html.required.set(htmlEnabled)
                    sarif.required.set(sarifEnabled)
                    if (sarifEnabled) {
                        sarif.outputLocation.set(file(detektSarifPath))
                    }
                    markdown.required.set(markdownEnabled)
                }

                finalizedBy(detektReportMergeXml, detektReportMergeSarif)
            }

            detektReportMergeSarif.configure {
                input.from(detektReportsWithExtension("sarif"))
            }

            detektReportMergeXml.configure {
                input.from(detektReportsWithExtension("xml"))
            }
        }
    }
}
