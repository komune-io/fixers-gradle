package io.komune.fixers.gradle.plugin.check

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import dev.detekt.gradle.report.ReportMergeTask
import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Detekt as FixersDetekt
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

private const val DETEKT_TASK = "detekt"
private const val PROJECT_CONFIG = "detekt.yml"
private const val GENERATED_DIRECTORY = "/generated/"
internal const val WRITE_CONFIG_TASK = "writeFixersDetektConfig"
private val JVM_PLATFORMS = setOf(KotlinPlatformType.jvm, KotlinPlatformType.androidJvm)
private val COMPILE_CLASSPATHS = setOf("compileClasspath", "testCompileClasspath")
private const val CHECK_TASK = "check"

// The typed tasks Detekt registers per target of a multiplatform module (today the JVM ones):
// `detektMain<Target>`, `detektTest<Target>`. The per-source-set tasks end in `SourceSet`, the
// JVM module's in nothing.
private val MPP_TYPED_DETEKT_TASK = Regex("detekt(Main|Test)[A-Z][A-Za-z0-9]*(?<!SourceSet)")

fun Project.getDetektReportMergeXmlFile(): Provider<RegularFile> =
    rootProject.layout.buildDirectory.file("reports/detekt/merge.xml")

/**
 * All Detekt reports of this project with the given extension, excluding the merged report itself
 * (the root project writes `merge.xml`/`merge.sarif` in the very same directory).
 *
 * A project can hold several Detekt tasks - Kotlin Multiplatform registers one per source set and
 * per compilation - so the merge tasks must collect every report, not only the one produced by the
 * default `detekt` task.
 */
internal fun Project.detektReportsWithExtension(extension: String): Provider<FileTree> =
    layout.buildDirectory.dir("reports/detekt").map { reportsDir ->
        reportsDir.asFileTree.matching {
            include("*.$extension")
            exclude("merge.$extension")
        }
    }

/**
 * Every Detekt rule, the configuration fixers ships ([fixersDetektConfig]) and no baseline, held
 * final on the extension and on every Detekt task: a build script cannot switch a rule off.
 *
 * A `detekt.yml` at the root of the build is read after the fixers configuration, by every Detekt
 * task of every project, and wins over it: it holds the exceptions the repository decides for
 * itself or for a path (a rule switched off, paths excluded from a rule, a threshold). An
 * exception local to one site stays a reasoned `@Suppress`.
 */
fun Project.configureDetekt() {
    val fixersDetekt = rootProject.extensions.fixers?.detekt
    val projectConfig = rootProject.layout.projectDirectory.file(PROJECT_CONFIG).asFile
        .takeIf { it.exists() }
    if (projectConfig != null) {
        logger.lifecycle("Detekt reads $projectConfig on top of the fixers configuration.")
    }
    // A task output, so that `clean` and the configuration cache keep it where Detekt reads it.
    val configLocation = rootProject.layout.buildDirectory.file("fixers/detekt.yml")
    val writeConfig = rootProject.tasks.register<WriteDetektConfig>(WRITE_CONFIG_TASK) {
        content.set(fixersDetektConfig())
        outputFile.set(configLocation)
    }
    val fixersConfig = writeConfig.flatMap { it.outputFile }
    // The order matters: Detekt lets a later file win over an earlier one.
    val configs = listOfNotNull(fixersConfig, projectConfig)
    val expectedConfig = configLocation.map { location -> listOfNotNull(location.asFile, projectConfig) }

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
                config.setFrom(configs)
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
                config.setFrom(configs)
                // Code a tool generates (KSP, OpenAPI) is not the project's to fix, whether it sits under a
                // source root or is one.
                exclude { element -> GENERATED_DIRECTORY in element.file.invariantSeparatorsPath }
                val task = this
                doFirst { task.failOnWeakenedRules(expectedConfig.get()) }

                configureReports(fixersDetekt)

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

// Reports are named after the task: a project may run several Detekt tasks (Kotlin Multiplatform
// registers one per source set and compilation), and a shared location makes them overwrite each other.
private fun Detekt.configureReports(chosen: FixersDetekt?) {
    val isCheckstyleEnabled = chosen == null || chosen.checkstyleReport.get()
    val isSarifEnabled = chosen == null || chosen.sarifReport.get()
    val checkstyleFile = project.layout.buildDirectory.file("reports/detekt/$name.xml")
    val sarifFile = project.layout.buildDirectory.file("reports/detekt/$name.sarif")
    reports {
        checkstyle.required.set(isCheckstyleEnabled)
        if (isCheckstyleEnabled) {
            checkstyle.outputLocation.set(checkstyleFile)
        }
        html.required.set(chosen == null || chosen.htmlReport.get())
        sarif.required.set(isSarifEnabled)
        if (isSarifEnabled) {
            sarif.outputLocation.set(sarifFile)
        }
        markdown.required.set(chosen == null || chosen.markdownReport.get())
    }
}

private fun Detekt.failOnWeakenedRules(expectedConfig: List<File>) {
    val baselineFile = baseline.orNull?.asFile
    if (baselineFile != null && baselineFile.exists()) {
        throw GradleException(
            "$path reads the baseline $baselineFile: fixers runs without one; delete it and fix the findings."
        )
    }
    val weakened = listOfNotNull(
        "allRules".takeUnless { allRules.get() },
        "buildUponDefaultConfig".takeUnless { buildUponDefaultConfig.get() },
        "disableDefaultRuleSets".takeIf { disableDefaultRuleSets.get() },
        "ignoreFailures".takeIf { ignoreFailures.get() },
        "failOnSeverity".takeUnless { failOnSeverity.get() == FailOnSeverity.Info },
        "config".takeUnless { config.files.toList() == expectedConfig },
    )
    if (weakened.isNotEmpty()) {
        throw GradleException("$path changes what fixers sets on Detekt: ${weakened.joinToString()}.")
    }
}

private fun <T : Any> Property<T>.lock(value: T) {
    set(value)
    disallowChanges()
}

// `check` runs `detekt`, which the Detekt plugin registers without a classpath: the rules that
// need types are then skipped without a word. Multiplatform sources sit outside `src/main`, where
// `detekt` looks: the typed tasks of every JVM target, which see `commonMain` too, analyse them,
// and the source set tasks analyse what no JVM compilation compiles (`jsMain`, `jsTest`), without
// types: Detekt registers no typed task for those targets.
private fun Project.configureTypedDetekt() {
    val compileClasspaths = configurations.matching { configuration -> configuration.name in COMPILE_CLASSPATHS }
    tasks.withType<Detekt>().matching { task -> task.name == DETEKT_TASK }.configureEach {
        classpath.from(compileClasspaths)
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
        tasks.matching { task -> task.name == CHECK_TASK }.configureEach {
            dependsOn(tasks.withType<Detekt>().matching { task -> MPP_TYPED_DETEKT_TASK.matches(task.name) })
            dependsOn(tasks.withType<Detekt>().matching { task -> task.name in kotlin.nonJvmOnlyDetektTasks() })
        }
    }
}

private fun KotlinMultiplatformExtension.nonJvmOnlyDetektTasks(): Set<String> {
    val compilations = targets.asSequence().flatMap { target -> target.compilations.map { target.platformType to it } }
    val jvm = compilations.filter { (platform, _) -> platform in JVM_PLATFORMS }
        .flatMapTo(mutableSetOf()) { (_, compilation) -> compilation.allKotlinSourceSets.map { it.name } }
    return compilations.filter { (platform, _) -> platform != KotlinPlatformType.common && platform !in JVM_PLATFORMS }
        .flatMap { (_, compilation) -> compilation.allKotlinSourceSets.map { it.name } }
        .filter { it !in jvm }
        .map { "detekt${it.replaceFirstChar(Char::uppercase)}SourceSet" }
        .toSet()
}

/** Writes the configuration fixers ships where every Detekt task of the build reads it. */
@CacheableTask
abstract class WriteDetektConfig : DefaultTask() {
    @get:Input
    abstract val content: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun write() {
        outputFile.get().asFile.apply { parentFile.mkdirs() }.writeText(content.get())
    }
}
