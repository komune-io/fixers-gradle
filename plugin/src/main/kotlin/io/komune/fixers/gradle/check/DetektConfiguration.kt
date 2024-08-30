package io.komune.fixers.gradle.check

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

fun Project.configureDetekt() {
    val detektReportMergeSarif = tasks.register<ReportMergeTask>("detektReportMergeSarif") {
        output.set(layout.buildDirectory.file("reports/detekt/merge.sarif"))
    }

    val detektReportMergeXml = rootProject.tasks.register<ReportMergeTask>("reportMerge") {
        output.set(rootProject.layout.buildDirectory.file("reports/detekt/merge.xml"))
    }

    allprojects {
        plugins.apply("io.gitlab.arturbosch.detekt")

        pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
            val detectXmlPath = "${layout.buildDirectory.asFile.get()}/reports/detekt/detekt.xml"
            val detectSarifPath = "${layout.buildDirectory.asFile.get()}/reports/detekt/detekt.sarif"
            extensions.configure(DetektExtension::class.java) {
                val sourceDirs = file("src")
                    .listFiles()
                    ?.filter { it.isDirectory && it.name.endsWith("main", ignoreCase = true) }
                    ?.map { it }
                    ?: emptyList()

                source.from(files(sourceDirs))
                config.setFrom(rootDir.resolve("detekt.yml"))
            }

            tasks.withType<Detekt>().configureEach {
                reports {
                    xml.required.set(true)
                    xml.outputLocation.set(file(detectXmlPath))
                    html.required.set(true)
                    sarif.required.set(true)
                    sarif.outputLocation.set(file(detectSarifPath))
                    md.required.set(true)
                    txt.required.set(false)
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
