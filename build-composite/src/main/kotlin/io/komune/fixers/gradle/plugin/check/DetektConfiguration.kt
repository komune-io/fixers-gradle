package io.komune.fixers.gradle.plugin.check

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import dev.detekt.gradle.report.ReportMergeTask
import io.komune.fixers.gradle.config.fixers
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

private const val DETEKT_TASK = "detekt"
private const val PROJECT_CONFIG = "detekt.yml"
private const val GENERATED_SOURCES = "**/generated/**"
private val JVM_PLATFORMS = setOf(KotlinPlatformType.jvm, KotlinPlatformType.androidJvm)
private val COMPILE_CLASSPATHS = setOf("compileClasspath", "testCompileClasspath")
private const val CHECK_TASK = "check"

// The typed tasks Detekt registers per target of a multiplatform module (today the JVM ones):
// `detektMain<Target>`, `detektTest<Target>`. The per-source-set tasks end in `SourceSet`, the
// JVM module's in nothing.
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

/**
 * Every Detekt rule, the configuration fixers ships ([fixersDetektConfig]) and no baseline, held
 * final on the extension and on every Detekt task: a build cannot switch a rule off, only a
 * reasoned `@Suppress` can. A `detekt.yml` at the root of the build fails it.
 */
fun Project.configureDetekt() {
    val fixersDetekt = rootProject.extensions.fixers?.detekt
    val projectConfig = rootDir.resolve(PROJECT_CONFIG)
    if (projectConfig.exists()) {
        throw GradleException(
            "$projectConfig: fixers ships the Detekt configuration and runs every rule; delete this file, " +
                "and suppress a finding with @Suppress and its reason where a rule does not apply."
        )
    }
    val fixersConfig = rootProject.resources.text.fromString(fixersDetektConfig()).asFile()

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
                allRules.lock(true)
                buildUponDefaultConfig.lock(true)
                disableDefaultRuleSets.lock(false)
                ignoreFailures.lock(false)
                failOnSeverity.lock(FailOnSeverity.Info)
                config.setFrom(fixersConfig)
                config.disallowChanges()
                baseline.disallowChanges()
            }

            tasks.withType<Detekt>().configureEach {
                // Not held final: the Detekt plugin sets some of these after this action. Checked when it runs.
                allRules.set(true)
                buildUponDefaultConfig.set(true)
                disableDefaultRuleSets.set(false)
                ignoreFailures.set(false)
                failOnSeverity.set(FailOnSeverity.Info)
                config.setFrom(fixersConfig)
                // Code a tool generates (KSP, OpenAPI) is not the project's to fix.
                exclude(GENERATED_SOURCES)
                val task = this
                doFirst { task.failOnWeakenedRules(fixersConfig) }

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

private fun Detekt.failOnWeakenedRules(fixersConfig: File) {
    val weakened = listOfNotNull(
        "allRules".takeUnless { allRules.get() },
        "buildUponDefaultConfig".takeUnless { buildUponDefaultConfig.get() },
        "disableDefaultRuleSets".takeIf { disableDefaultRuleSets.get() },
        "ignoreFailures".takeIf { ignoreFailures.get() },
        "failOnSeverity".takeUnless { failOnSeverity.get() == FailOnSeverity.Info },
        "config".takeUnless { config.files == setOf(fixersConfig) },
        "baseline".takeIf { baseline.orNull?.asFile?.exists() == true },
    )
    if (weakened.isNotEmpty()) {
        throw GradleException("$path changes what fixers sets on Detekt: ${weakened.joinToString()}.")
    }
}

private fun <T : Any> Property<T>.lock(value: T) {
    set(value)
    disallowChanges()
}

/**
 * `check` runs `detekt`, which the Detekt plugin registers without a classpath: the rules that
 * need types are then skipped without a word. Multiplatform sources sit outside `src/main`, where
 * `detekt` looks: the typed tasks of every JVM target, which see `commonMain` too, analyse them,
 * and the source set tasks analyse what no JVM compilation compiles (`jsMain`, `jsTest`), without
 * types: Detekt registers no typed task for those targets.
 */
private fun Project.configureTypedDetekt() {
    val compileClasspaths = configurations.matching { configuration -> configuration.name in COMPILE_CLASSPATHS }
    tasks.withType<Detekt>().matching { task -> task.name == DETEKT_TASK }.configureEach {
        classpath.from(compileClasspaths)
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
        tasks.matching { task -> task.name == CHECK_TASK }.configureEach {
            dependsOn(tasks.withType<Detekt>().matching { task -> MPP_TYPED_DETEKT_TASK.matches(task.name) })
            dependsOn(tasks.withType<Detekt>().matching { task -> task.name in kotlin.nonJvmSourceSetTasks() })
        }
    }
}

/** The Detekt task of each source set that only non-JVM compilations compile. */
private fun KotlinMultiplatformExtension.nonJvmSourceSetTasks(): Set<String> {
    val compilations = targets.flatMap { target -> target.compilations.map { target.platformType to it } }
    val jvm = compilations.filter { (platform, _) -> platform in JVM_PLATFORMS }
        .flatMapTo(mutableSetOf()) { (_, compilation) -> compilation.allKotlinSourceSets.map { it.name } }
    return compilations.filter { (platform, _) -> platform != KotlinPlatformType.common && platform !in JVM_PLATFORMS }
        .flatMap { (_, compilation) -> compilation.allKotlinSourceSets.map { it.name } }
        .filter { it !in jvm }
        .mapTo(mutableSetOf()) { "detekt${it.replaceFirstChar(Char::uppercase)}SourceSet" }
}
