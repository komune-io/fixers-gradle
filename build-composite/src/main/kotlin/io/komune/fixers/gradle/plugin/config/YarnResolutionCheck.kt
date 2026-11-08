package io.komune.fixers.gradle.plugin.config

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

internal const val YARN_RESOLUTION_CHECK_TASK = "checkYarnResolutions"

private const val ANY_DEPTH = "**/"

/**
 * Fails when a yarn resolution forced on the Kotlin/JS lock is not scoped to any depth (`**`
 * then `/`), or has no effect on `kotlin-js-store/yarn.lock`. Yarn ignores a path it cannot
 * match without a word, and a pin whose parent left the tree keeps a dead entry in the lock, so
 * neither shows up otherwise.
 */
@DisableCachingByDefault(because = "A verification that produces no output")
abstract class YarnResolutionCheckTask : DefaultTask() {

    /** Resolution path to the forced version, as declared with `resolution(path, version)`. */
    @get:Input
    abstract val resolutions: MapProperty<String, String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: RegularFileProperty

    @TaskAction
    fun check() {
        val lock = lockFile.orNull?.asFile?.takeIf { it.exists() }?.readText()
        val problems = yarnResolutionProblems(resolutions.get(), lock)
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Yarn resolutions without effect on the Kotlin/JS lock:\n" +
                    problems.joinToString("\n") { "  - $it" }
            )
        }
    }
}

internal fun Project.registerYarnResolutionCheck() {
    plugins.withType(YarnPlugin::class.java) {
        val yarn = extensions.getByType(YarnRootExtension::class.java)
        val check = tasks.register(YARN_RESOLUTION_CHECK_TASK, YarnResolutionCheckTask::class.java) {
            group = "verification"
            description = "Fails on a yarn resolution that is not scoped with **/ or does not reach the lock."
            resolutions.set(
                yarn.resolutionsProperty.map { list ->
                    list.associate { it.path to it.includedVersions.joinToString(" ") }
                }
            )
            lockFile.set(yarn.lockFileDirectoryProperty.file(yarn.lockFileNameProperty))
            mustRunAfter(tasks.matching { task -> task.name == "kotlinStoreYarnLock" })
        }
        tasks.named("check") { dependsOn(check) }
    }
}

/**
 * One message per resolution in [resolutions] (path to forced version) that is not scoped to any
 * depth, or that [lock] (a yarn v1 lock, null when absent) shows without effect: the parent the
 * path names, or any package for an unparented path, must depend on the package, and the lock
 * must hold the package at the forced version.
 */
internal fun yarnResolutionProblems(resolutions: Map<String, String>, lock: String?): List<String> = when {
    resolutions.isEmpty() -> emptyList()
    lock == null -> listOf("no yarn.lock to check ${resolutions.keys.joinToString()} against")
    else -> {
        val entries = parseYarnLock(lock)
        resolutions.mapNotNull { (path, version) -> resolutionProblem(path, version, entries) }
    }
}

private fun resolutionProblem(path: String, version: String, entries: List<LockEntry>): String? {
    if (!path.startsWith(ANY_DEPTH)) {
        return "$path: not scoped with $ANY_DEPTH, so it either misses its target or hits every tree"
    }
    val names = packageNames(path.removePrefix(ANY_DEPTH))
    val pkg = names.last()
    val parent = names.getOrNull(names.size - 2)
    val dependedOn = entries.any { entry ->
        (parent == null || entry.name == parent) && pkg in entry.dependencies
    }
    val atVersion = entries.any { it.name == pkg && it.version == version }
    return when {
        !dependedOn && parent == null -> "$path: no package depends on $pkg in the lock"
        !dependedOn -> "$path: $parent does not depend on $pkg in the lock"
        !atVersion -> "$path: the lock does not hold $pkg at $version"
        else -> null
    }
}

private class LockEntry(val name: String, val version: String?, val dependencies: Set<String>)

/** Splits `a/@scope/b/c` into `[a, @scope/b, c]`. */
private fun packageNames(path: String): List<String> {
    val names = mutableListOf<String>()
    val segments = path.split("/").iterator()
    while (segments.hasNext()) {
        val segment = segments.next()
        names += if (segment.startsWith("@") && segments.hasNext()) "$segment/${segments.next()}" else segment
    }
    return names
}

private fun parseYarnLock(lock: String): List<LockEntry> {
    val entries = mutableListOf<LockEntry>()
    var name: String? = null
    var version: String? = null
    val dependencies = mutableSetOf<String>()
    var inDependencies = false
    fun flush() {
        name?.let { entries += LockEntry(it, version, dependencies.toSet()) }
    }
    lock.lineSequence().forEach { line ->
        when {
            line.isBlank() || line.startsWith("#") -> Unit
            !line.startsWith(" ") -> {
                flush()
                name = specName(line.substringBefore(",").trim().removeSuffix(":"))
                version = null
                dependencies.clear()
                inDependencies = false
            }
            line.startsWith("    ") && inDependencies ->
                dependencies += line.trim().substringBeforeLast(" ").trim('"')
            else -> {
                val field = line.trim()
                inDependencies = field == "dependencies:" || field == "optionalDependencies:"
                if (field.startsWith("version ")) version = field.removePrefix("version ").trim('"')
            }
        }
    }
    flush()
    return entries
}

/** `"@scope/name@^1.0.0"` or `name@1.0.0` to the package name. */
private fun specName(spec: String): String {
    val unquoted = spec.trim('"')
    val at = unquoted.indexOf('@', startIndex = 1)
    return if (at < 0) unquoted else unquoted.substring(0, at)
}
