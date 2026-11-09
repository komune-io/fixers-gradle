package io.komune.fixers.gradle.plugin.config

import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.targets.js.npm.LockCopyTask
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.RootPackageJsonTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

internal const val YARN_RESOLUTION_CHECK_TASK = "checkYarnResolutions"

private const val ANY_DEPTH = "**/"
private const val NPM_ALIAS = "npm:"

private val EXACT_VERSION = Regex("""\d+\.\d+\.\d+([-+][0-9A-Za-z.-]+)?""")

private val DEPENDENCY_FIELDS = listOf("dependencies", "devDependencies", "optionalDependencies", "peerDependencies")

/**
 * Fails when a yarn resolution forced on the Kotlin/JS lock is not scoped to any depth (`**`
 * then `/`), or has no effect on `kotlin-js-store/yarn.lock`. Yarn ignores a path it cannot
 * match without a word, and a pin whose parent left the tree keeps a dead entry in the lock, so
 * neither shows up otherwise.
 */
@DisableCachingByDefault(because = "A verification that produces no output")
abstract class YarnResolutionCheckTask : DefaultTask() {

    /** Resolution path to the versions it forces, as declared with `resolution(path, version)`. */
    @get:Input
    abstract val resolutions: MapProperty<String, List<String>>

    /** The lock, which may be missing: a missing lock is a failure of this check, not of Gradle's. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: ConfigurableFileCollection

    /**
     * The outputs of `rootPackageJson`, among them the root `package.json` Kotlin/JS writes, whose
     * `workspaces` are the Kotlin/JS modules: yarn keeps them out of the lock, so their dependencies
     * are read from their own `package.json`.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rootPackageJson: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val lock = lockFile.files.firstOrNull { it.exists() }?.readText()
        val root = rootPackageJson.files.firstOrNull { it.name == "package.json" && it.exists() }
        val problems = yarnResolutionProblems(resolutions.get(), lock, root?.let(::workspaceDependencies).orEmpty())
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
                    list.associate { it.path to it.includedVersions.toList() }
                }
            )
            lockFile.from(yarn.lockFileDirectoryProperty.file(yarn.lockFileNameProperty))
            rootPackageJson.from(tasks.withType(RootPackageJsonTask::class.java))
            // Every task that writes a lock: kotlinStoreYarnLock and kotlinUpgradeYarnLock write the one read here.
            mustRunAfter(tasks.withType(LockCopyTask::class.java))
        }
        tasks.named("check") { dependsOn(check) }
    }
}

/** What the workspaces listed in [rootPackageJson] ask for, from each one's own `package.json`. */
internal fun workspaceDependencies(rootPackageJson: File): Set<Spec> {
    val root = readJson(rootPackageJson)
    val workspaces = (root["workspaces"] as? List<*>).orEmpty().filterIsInstance<String>()
    return workspaces.map { rootPackageJson.resolveSibling(it).resolve("package.json") }
        .filter { it.exists() }
        .flatMapTo(mutableSetOf()) { file ->
            val json = readJson(file)
            DEPENDENCY_FIELDS.flatMap { field ->
                (json[field] as? Map<*, *>).orEmpty().map { (name, range) -> Spec(name.toString(), range.toString()) }
            }
        }
}

private fun readJson(file: File): Map<*, *> = (JsonSlurper().parse(file) as? Map<*, *>).orEmpty()

/**
 * One message per resolution in [resolutions] (path to forced version) that is not scoped to any
 * depth, or that [lock] (a yarn v1 lock, null when absent) shows without effect: the parent the
 * path names, or any package for an unparented path, must depend on the package, and the lock
 * must hold the package at the forced version. The workspaces (the Kotlin/JS modules) have no
 * entry in the lock: an unparented path is also depended on when one of [workspaces], what the
 * workspaces ask for, names the package.
 */
internal fun yarnResolutionProblems(
    resolutions: Map<String, List<String>>,
    lock: String?,
    workspaces: Set<Spec> = emptySet(),
): List<String> = when {
    resolutions.isEmpty() -> emptyList()
    lock == null -> listOf("no yarn.lock to check ${resolutions.keys.joinToString()} against")
    else -> {
        val entries = parseYarnLock(lock)
        val requested = entries.flatMapTo(mutableSetOf()) { it.dependencies } + workspaces
        resolutions.mapNotNull { (path, versions) -> resolutionProblem(path, versions, entries, requested) }
    }
}

private fun resolutionProblem(
    path: String,
    versions: List<String>,
    entries: List<LockEntry>,
    requested: Set<Spec>,
): String? {
    val version = versions.singleOrNull()?.takeIf { EXACT_VERSION.matches(it) }
    return when {
        !path.startsWith(ANY_DEPTH) ->
            "$path: not scoped with $ANY_DEPTH, so it either misses its target or hits every tree"
        version == null -> "$path: forces ${versions.joinToString(" ")}, not one exact version"
        else -> lockProblem(path, version, entries, requested)
    }
}

private fun lockProblem(path: String, version: String, entries: List<LockEntry>, requested: Set<Spec>): String? {
    val names = packageNames(path.removePrefix(ANY_DEPTH))
    val pkg = names.last()
    val parent = names.getOrNull(names.size - 2)
    val dependedOn = if (parent == null) {
        requested.any { it.isOf(pkg) }
    } else {
        entries.any { entry -> entry.isOf(parent) && entry.dependencies.any { it.isOf(pkg) } }
    }
    val atVersion = entries.any { it.isOf(pkg) && it.version == version }
    val unrequested = entries.filter { it.isOf(pkg) }.flatMap { it.specs }
        .filter { it.isOf(pkg) && it !in requested && it != Spec(pkg, version) }
    return when {
        !dependedOn && parent == null && unrequested.isNotEmpty() ->
            "$path: the lock holds ${unrequested.joinToString { "${it.name}@${it.range}" }}, " +
                "which neither a lock entry nor a workspace asks for"
        !dependedOn && parent == null -> "$path: no package depends on $pkg in the lock"
        !dependedOn -> "$path: $parent does not depend on $pkg in the lock"
        !atVersion -> "$path: the lock does not hold $pkg at $version"
        else -> null
    }
}

/** A package as a dependency asks for it: its name there, and its range, `npm:` alias included. */
internal data class Spec(val name: String, val range: String) {
    /** The package installed: the alias target for `npm:other@range`, else [name]. */
    private val target: String
        get() = if (range.startsWith(NPM_ALIAS)) spec(range.removePrefix(NPM_ALIAS)).name else name

    fun isOf(pkg: String) = name == pkg || target == pkg
}

/** One block of the lock: every spec its header lists, the version they resolve to, its dependencies. */
private class LockEntry(val specs: Set<Spec>, val version: String?, val dependencies: Set<Spec>) {
    fun isOf(pkg: String) = specs.any { it.isOf(pkg) }
}

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
    var specs = emptySet<Spec>()
    var version: String? = null
    val dependencies = mutableSetOf<Spec>()
    var inDependencies = false
    fun flush() {
        if (specs.isNotEmpty()) entries += LockEntry(specs, version, dependencies.toSet())
    }
    lock.lineSequence().forEach { line ->
        when {
            line.isBlank() || line.startsWith("#") -> Unit
            !line.startsWith(" ") -> {
                flush()
                specs = line.trim().removeSuffix(":").split(",").mapTo(mutableSetOf()) { spec(it.trim()) }
                version = null
                dependencies.clear()
                inDependencies = false
            }
            line.startsWith("    ") && inDependencies -> {
                val field = line.trim()
                dependencies += Spec(field.substringBefore(" ").trim('"'), field.substringAfter(" ").trim('"'))
            }
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

/** `"@scope/name@^1.0.0"` or `name@1.0.0` to its package name and range. */
private fun spec(spec: String): Spec {
    val unquoted = spec.trim('"')
    val at = unquoted.indexOf('@', startIndex = 1)
    return if (at < 0) Spec(unquoted, "") else Spec(unquoted.substring(0, at), unquoted.substring(at + 1))
}
