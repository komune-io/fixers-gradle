package io.komune.fixers.gradle.plugin.check

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.report.ReportMergeTask
import io.komune.fixers.gradle.config.fixers
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

private const val DETEKT_TASK = "detekt"
private const val DETEKT_BASELINE_TASK = "detektBaseline"
private val COMPILE_CLASSPATHS = setOf("compileClasspath", "testCompileClasspath")
private const val CHECK_TASK = "check"

// The typed tasks Detekt registers per JVM target of a multiplatform module: `detektMain<Target>`,
// `detektTest<Target>`. The per-source-set tasks end in `SourceSet`, the JVM module's in nothing.
private val MPP_TYPED_DETEKT_TASK = Regex("detekt(Main|Test)[A-Z][A-Za-z0-9]*(?<!SourceSet)")

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

            configureTypedDetekt()

            detektReportMergeSarif.configure {
                input.from(detektReportsWithExtension("sarif"))
            }

            detektReportMergeXml.configure {
                input.from(detektReportsWithExtension("xml"))
            }
        }
    }
}

/**
 * `check` runs `detekt`, which the Detekt plugin registers without a classpath, like
 * `detektBaseline`: the rules that need types are then skipped without a word. Multiplatform
 * sources sit outside `src/main`, where `detekt` looks: the typed tasks of the JVM targets, which
 * see `commonMain` too, are the ones that analyse them.
 */
private fun Project.configureTypedDetekt() {
    val compileClasspaths = configurations.matching { configuration -> configuration.name in COMPILE_CLASSPATHS }
    tasks.withType<Detekt>().matching { task -> task.name == DETEKT_TASK }.configureEach {
        classpath.from(compileClasspaths)
    }
    tasks.withType<DetektCreateBaselineTask>().matching { task -> task.name == DETEKT_BASELINE_TASK }.configureEach {
        classpath.from(compileClasspaths)
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        tasks.matching { task -> task.name == CHECK_TASK }.configureEach {
            dependsOn(tasks.withType<Detekt>().matching { task -> MPP_TYPED_DETEKT_TASK.matches(task.name) })
        }
    }
}
