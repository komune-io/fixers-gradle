package io.komune.fixers.gradle.check

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register

fun Project.configureDetekt() {
    val taskName = "detektReportMergeSarif"
    val detektReportMergeSarif: TaskProvider<ReportMergeTask> = tasks.register<ReportMergeTask>(taskName) {
        output = layout.buildDirectory.file("reports/detekt/merge.sarif")
    }
    allprojects {
        plugins.apply("io.gitlab.arturbosch.detekt")
        pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
            extensions.configure(DetektExtension::class.java) {
                file("src")
                    .listFiles()
                    ?.filter {
                        it.isDirectory && it.name.endsWith("main", ignoreCase = true)
                    }?.let {
                        source.from(
                            files(
                                it
                            )
                        )
                    }
                config.from(
                    rootDir.resolve("detekt.yml")
                )
            }
            tasks.withType(Detekt::class.java).configureEach {
                reports {
                    xml.required = true
                    html.required = true
                    sarif.required = true
                    md.required = true

                    txt.required = false
                }
            }
            detektReportMergeSarif.configure {
                input.from(tasks.withType(Detekt::class.java).map { it.reports.sarif.outputLocation })
            }
        }
    }
}
