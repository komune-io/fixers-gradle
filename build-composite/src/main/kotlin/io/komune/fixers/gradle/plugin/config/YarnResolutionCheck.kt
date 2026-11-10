package io.komune.fixers.gradle.plugin.config

import groovy.json.JsonException
import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.targets.js.npm.LockCopyTask
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.RootPackageJsonTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

/**
 * Versions fixers forces on the Kotlin/JS lock of every build: transitive packages of the Kotlin
 * and ktor JS dependencies whose declared versions carry an advisory. `ws` 8.20.1 is pinned by
 * ktor-client-core (GHSA-96hv-2xvq-fx4p); `ajv` before 8.18.0 (CVE-2025-69873). Remove a pin once
 * the dependency declares a fixed version.
 */
internal val FIXERS_YARN_PINS: Map<String, String> = mapOf(
    "**/ws" to "8.22.2",
    "**/ajv" to "8.20.0",
)

internal const val YARN_RESOLUTION_CHECK_TASK = "checkYarnResolutions"

private const val ANY_DEPTH = "**/"
private const val NPM_ALIAS = "npm:"

private val EXACT_VERSION = Regex("""\d+\.\d+\.\d+([-+][0-9A-Za-z.-]+)?""")

// A peer dependency is not installed by yarn v1 for the package that declares it: it asks for nothing.
private val DEPENDENCY_FIELDS = listOf("dependencies", "devDependencies", "optionalDependencies")

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

    /** The resolutions of [FIXERS_YARN_PINS], path to forced version. */
    @get:Input
    abstract val fixersPins: MapProperty<String, String>

    /** The lock, which may be missing: a missing lock is a failure of this check, not of Gradle's. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val lockFile: ConfigurableFileCollection

    /**
     * The root `package.json` that `rootPackageJson` writes, whose `workspaces` are the Kotlin/JS
     * modules: yarn keeps them out of the lock, so their dependencies are read from their own
     * `package.json`.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rootPackageJson: RegularFileProperty

    @TaskAction
    fun check() {
        val lock = lockFile.files.firstOrNull { it.exists() }?.readText()
        val root = rootPackageJson.orNull?.asFile
        val problems = yarnResolutionProblems(
            resolutions.get(),
            lock,
            root?.let(::workspaceDependencies).orEmpty(),
            fixersPins.get(),
        )
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
        FIXERS_YARN_PINS.forEach { (path, version) -> yarn.resolution(path, version) }
        val check = tasks.register(YARN_RESOLUTION_CHECK_TASK, YarnResolutionCheckTask::class.java) {
            group = "verification"
            description = "Fails on a yarn resolution that is not scoped with **/ or does not reach the lock."
            resolutions.set(
                yarn.resolutionsProperty.map { list ->
                    list.groupBy({ it.path }, { it.includedVersions.toList() })
                        .mapValues { (_, versions) -> versions.flatten() }
                }
            )
            fixersPins.set(FIXERS_YARN_PINS)
            lockFile.from(yarn.lockFileDirectoryProperty.file(yarn.lockFileNameProperty))
            // Not every RootPackageJsonTask: wasmRootPackageJson writes the Wasm one.
            rootPackageJson.set(
                tasks.named(RootPackageJsonTask.NAME, RootPackageJsonTask::class.java)
                    .flatMap { task -> task.rootPackageJsonFile }
            )
            // Every task that writes a lock: kotlinStoreYarnLock and kotlinUpgradeYarnLock write the one read here.
            mustRunAfter(tasks.withType(LockCopyTask::class.java))
        }
        tasks.named("check") { dependsOn(check) }
    }
}

/** What the workspaces listed in [rootPackageJson] ask for, from each one's own `package.json`. */
internal fun workspaceDependencies(rootPackageJson: File): Set<Spec> {
    val workspaces = readJson(rootPackageJson)["workspaces"]
    if (workspaces != null && (workspaces !is List<*> || workspaces.any { it !is String })) {
        refuse(rootPackageJson, "`workspaces` is not a list of paths: $workspaces")
    }
    return workspaces.orEmpty().filterIsInstance<String>().asSequence().map { workspace ->
        if ('*' in workspace) {
            refuse(rootPackageJson, "workspace pattern `$workspace` is not read; list the paths")
        }
        rootPackageJson.resolveSibling(workspace).resolve(PACKAGE_JSON).also {
            if (!it.exists()) refuse(rootPackageJson, "workspace `$workspace` has no $it")
        }
    }
        .flatMapTo(mutableSetOf()) { file ->
            val json = readJson(file)
            DEPENDENCY_FIELDS.flatMap { field ->
                (json[field] as? Map<*, *>).orEmpty().map { (name, range) ->
                    Spec(name.toString(), range.toString())
                }
            }
        }
}

private fun refuse(file: File, problem: String, cause: Throwable? = null): Nothing =
    throw GradleException("$file: $problem", cause)

private fun readJson(file: File): Map<*, *> = try {
    JsonSlurper().parse(file) as? Map<*, *> ?: refuse(file, "not a JSON object")
} catch (e: JsonException) {
    refuse(file, e.message.orEmpty(), e)
}

/**
 * One message per resolution in [resolutions] (path to forced version) that is not scoped to any
 * depth, or that [lock] (a yarn v1 lock, null when absent) shows without effect: the parent the
 * path names, or any package for an unparented path, must depend on the package, and the lock
 * must hold the package at the forced version. The workspaces (the Kotlin/JS modules) have no
 * entry in the lock: an unparented path is also depended on when one of [workspaces], what the
 * workspaces ask for, names the package. A path of [fixersPins] is the build's to leave alone: a
 * resolution of its own there is refused, and the pin passes when the lock does not hold its package.
 */
internal fun yarnResolutionProblems(
    resolutions: Map<String, List<String>>,
    lock: String?,
    workspaces: Set<Spec> = emptySet(),
    fixersPins: Map<String, String> = emptyMap(),
): List<String> {
    val declared = resolutions.filter { (path, versions) -> versions != listOfNotNull(fixersPins[path]) }
    return when {
        resolutions.isEmpty() -> {
            emptyList()
        }
        lock == null -> {
            listOf("no yarn.lock to check ${resolutions.keys.joinToString()} against")
        }
        else -> {
            val entries = parseYarnLock(lock)
            val requested = entries.flatMapTo(mutableSetOf()) { it.dependencies } + workspaces
            resolutions.mapNotNull { (path, versions) ->
                val pinned = fixersPins[path]
                val pkg = packageNames(path.removePrefix(ANY_DEPTH)).last()
                val version = versions.singleOrNull()?.takeIf { EXACT_VERSION.matches(it) }
                when {
                    pinned != null && path in declared ->
                        "$path: fixers already forces $pkg to $pinned; remove this resolution"
                    // A pin of fixers holds for every build: in one that does not need the package, the
                    // lock holds it only at the spec yarn adds for the resolution.
                    pinned != null && entries.filter { it.isOf(pkg) }.flatMap { it.specs }
                        .all { !it.isOf(pkg) || it == Spec(pkg, pinned) } -> null
                    !path.startsWith(ANY_DEPTH) ->
                        "$path: not scoped with $ANY_DEPTH, so it either misses its target or hits every tree"
                    version == null -> "$path: forces ${versions.joinToString(" ")}, not one exact version"
                    else -> lockProblem(path, version, entries, requested)
                }
            }
        }
    }
}

private fun lockProblem(path: String, version: String, entries: List<LockEntry>, requested: Set<Spec>): String? {
    val names = packageNames(path.removePrefix(ANY_DEPTH))
    val pkg = names.last()
    val parent = names.getOrNull(names.size - 2)
    val isDependedOn = if (parent == null) {
        requested.any { it.isOf(pkg) }
    } else {
        entries.any { entry -> entry.isOf(parent) && entry.dependencies.any { it.isOf(pkg) } }
    }
    val isAtVersion = entries.any { it.isOf(pkg) && it.version == version }
    return when {
        !isDependedOn && parent == null -> unrequestedProblem(path, pkg, version, entries, requested)
        !isDependedOn -> "$path: ${parent.orEmpty()} does not depend on $pkg in the lock"
        !isAtVersion -> "$path: the lock does not hold $pkg at $version"
        else -> null
    }
}

/** For an unparented [path] nobody depends on: the lock specs of [pkg] nobody asks for, if any. */
private fun unrequestedProblem(
    path: String,
    pkg: String,
    version: String,
    entries: List<LockEntry>,
    requested: Set<Spec>,
): String {
    val unrequested = entries.asSequence().filter { it.isOf(pkg) }.flatMap { it.specs }
        .filter { it.isOf(pkg) && it !in requested && it != Spec(pkg, version) }
        .toList()
    return if (unrequested.isEmpty()) {
        "$path: no package depends on $pkg in the lock"
    } else {
        "$path: the lock holds ${unrequested.joinToString { "${it.name}@${it.range}" }}, " +
            "which neither a lock entry nor a workspace asks for"
    }
}

/** A package as a dependency asks for it: its name there, and its range, `npm:` alias included. */
internal data class Spec(val name: String, val range: String)

/** The package installed: the alias target for `npm:other@range`, else [Spec.name]. */
private val Spec.target: String
    get() = if (range.startsWith(NPM_ALIAS)) spec(range.removePrefix(NPM_ALIAS)).name else name

private fun Spec.isOf(pkg: String) = name == pkg || target == pkg

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
    var isInDependencies = false
    fun flush() {
        if (specs.isNotEmpty()) entries += LockEntry(specs, version, dependencies.toSet())
    }
    lock.lineSequence().forEach { line ->
        when {
            line.isBlank() || line.startsWith("#") -> {
                // Blank lines and comments carry nothing.
            }
            !line.startsWith(" ") -> {
                flush()
                specs = line.trim().removeSuffix(":").split(",").mapTo(mutableSetOf()) { spec(it.trim()) }
                version = null
                dependencies.clear()
                isInDependencies = false
            }
            line.startsWith("    ") && isInDependencies -> {
                val field = line.trim()
                dependencies += Spec(field.substringBefore(" ").trim('"'), field.substringAfter(" ").trim('"'))
            }
            else -> {
                val field = line.trim()
                isInDependencies = field == "dependencies:" || field == "optionalDependencies:"
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
