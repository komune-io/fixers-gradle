package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.model.Kt2Ts
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

internal const val NODE_MODULES = "node_modules"
internal const val JS_DISTRIBUTION_SUFFIX = "ProductionLibraryDistribution"
internal const val TS_GEN_CHECK_TASK = "checkTsGen"

private val NODE_MODULES_PATTERNS = listOf("**/$NODE_MODULES", "**/$NODE_MODULES/**")

/** What the Kotlin/JS build emitted under [source], its test packages left out. */
internal fun Project.emittedTree(source: File): FileTree =
    fileTree(source) { exclude(NODE_MODULES_PATTERNS + "*-test") }

/** A generated package as `tsGen` writes it; Gradle never walks into the linked dependencies. */
internal fun Project.packageTree(output: File): FileTree = fileTree(output) { exclude(NODE_MODULES_PATTERNS) }

internal fun Map<String, List<Pair<Regex, String>>>.fingerprint(): Map<String, List<String>> =
    mapValues { (_, rules) ->
        rules.map { (pattern, replacement) -> "${pattern.pattern}\u0000${pattern.options}\u0000$replacement" }
    }

/**
 * Registers `checkTsGen`, which regenerates the declarations into the build directory and fails
 * when the output directory differs from them, or when a `.d.mts` declares a name twice at its top
 * level: two of its classes, interfaces, functions, constants, variables, enums or type aliases, as
 * named in JavaScript, exported or not (the helpers of `Json`, the emitter's `Nullable`), or two
 * namespaces, the one namespace of a companion and the pair of one type
 * (interface, type alias) and one value (function, constant, variable) being allowed. `check` runs it
 * in a build that emits Kotlin/JS.
 *
 * The compiler's `EXPORTING_JS_NAME_CLASH_ES` is reported when the declaring module compiles:
 * two declarations of one name that come from dependencies reach the linked declarations
 * unreported. This check reads the declarations of the whole program.
 *
 * [contract] gives the Kotlin/JS output, the output directory and the cleaning rules, read when
 * the task is realized.
 */
internal fun Project.registerTsGenCheck(
    config: Kt2Ts,
    jsDistributions: List<Any>,
    contract: () -> Triple<File, File, Map<String, List<Pair<Regex, String>>>>,
): TaskProvider<Task> {
    val staging = layout.buildDirectory.dir("kt2ts-check")
    val checkTsGen = tasks.register(TS_GEN_CHECK_TASK) {
        val (source, output, rules) = contract()
        val shownOutput = config.outputDirectory.get()
        val committed = config.committedFiles
        val additional = config.additionalCleaning
        val stagingDir = staging.get().asFile
        group = "verification"
        description = "Fails when $shownOutput is stale against the Kotlin/JS output."
        dependsOn(jsDistributions)
        mustRunAfter("tsGen")
        // Not skipped when empty: a missing Kotlin output must fail the check, not pass it.
        inputs.files(emittedTree(source)).withPropertyName("emitted").withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(packageTree(output)).withPropertyName("committed").withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("cleaning", rules.fingerprint())
        inputs.property("committedFiles", committed)
        outputs.dir(stagingDir)
        doLast {
            stagingDir.deleteRecursively()
            stagingDir.writeContract(
                source = source,
                rules = rules,
                committed = committed.get(),
                additional = additional.get(),
            )
            if (stagingDir.walkTopDown().none { it.isFile }) {
                throw GradleException("No Kotlin/JS output under $source to compare $shownOutput with.")
            }
            val stale = staleFiles(fresh = stagingDir, committed = output).map { "$shownOutput/$it." }
            val duplicated = duplicatedDeclarations(stagingDir).map { "$shownOutput/$it" }
            if (stale.isNotEmpty() || duplicated.isNotEmpty()) {
                val fixes = listOfNotNull(
                    "Regenerate it with `./gradlew tsGen` and commit the result.".takeIf { stale.isNotEmpty() },
                    ("Rename one of the two Kotlin declarations, or give one a distinct `@JsName`; " +
                        "the emitter has no namespaces.")
                        .takeIf { duplicated.isNotEmpty() },
                )
                throw GradleException(
                    "The TypeScript contract is not what the Kotlin generates:" +
                        (stale + duplicated).joinToString("") { "\n  $it" } +
                        fixes.joinToString("") { "\n$it" }
                )
            }
        }
    }
    // Only a build that emits Kotlin/JS has a contract.
    val isJsEmitted = allprojects.any { project -> project.tasks.names.any { it.endsWith(JS_DISTRIBUTION_SUFFIX) } }
    if (isJsEmitted) {
        tasks.matching { it.name == "check" }.configureEach { dependsOn(checkTsGen) }
    }
    return checkTsGen
}

// The package's own version, which `tsGen` writes at the contract's. Anchored: a dependency named
// `version` is not it.
internal val packageVersion = Regex("""^  "version": "[^"]*"""", RegexOption.MULTILINE)

// Only [Kt2TsFiles.ALL] writes a module.
private fun File.comparable(): String =
    readText().let { text -> if (name.endsWith(".mjs")) canonicalMjs(text) else text }

/** Every file of a package, by relative path, without the dependencies linked under node_modules. */
internal fun File.packageFiles(): Map<String, File> =
    walkTopDown().onEnter { it.name != NODE_MODULES }
        .filter { it.isFile }
        .associateBy { it.relativeTo(this).invariantSeparatorsPath }

/**
 * Makes this directory hold what the Kotlin/JS build emitted under [source] that [keep] accepts,
 * its test packages left out: copies every such file, deletes the others, and leaves node_modules
 * alone.
 */
internal fun File.syncFrom(source: File, keep: (String) -> Boolean = { true }) {
    val emitted = source.packageFiles().filterKeys { !it.substringBefore('/').endsWith("-test") && keep(it) }
    (packageFiles().keys - emitted.keys).forEach { resolve(it).delete() }
    emitted.forEach { (path, file) -> file.copyTo(resolve(path), overwrite = true) }
    walkBottomUp().onEnter { it.name != NODE_MODULES }
        .filter { it != this && it.isDirectory && it.list().isNullOrEmpty() }
        .forEach { it.delete() }
}

// Source maps carry the paths of the machine that compiled them.
private fun File.comparedFiles(): Map<String, File> = packageFiles().filterKeys { !it.endsWith(".map") }

internal fun staleFiles(fresh: File, committed: File): List<String> {
    val expected = fresh.comparedFiles()
    val actual = committed.comparedFiles()
    return (expected.keys + actual.keys).sorted().mapNotNull { path ->
        val generated = expected[path]
        val current = actual[path]
        when {
            current == null -> "$path is missing from the committed copy"
            generated == null -> "$path is no longer generated from the Kotlin/JS output"
            generated.comparable() != current.comparable() -> "$path differs from the Kotlin/JS output"
            else -> null
        }
    }
}

// Every top-level declaration of a `.d.mts`, exported or not, under the name JavaScript sees, `@JsName`
// applied: a Kotlin export named like a helper the module keeps to itself (`JsonObject`) is the same
// clash. Anchored at the start of a line, where the emitter writes the top-level declarations: one
// nested in a namespace belongs to that namespace. A `const enum` is an enum.
private val declaredName = Regex(
    // `(?U)`: a JavaScript name holds any Unicode letter, `JsonObjectÉ` is not `JsonObject`.
    """(?U)^(?:export )?(?:declare )?(?:abstract )?""" +
        """(interface|class|namespace|function|const enum|const|let|var|enum|type) ([\w$]+)""",
    RegexOption.MULTILINE,
)

// Declarations that name a type only, and those that name a value only; a class or an enum names both.
private val typeOnly = setOf("interface", "type")
private val valueOnly = setOf("function", "const", "let", "var")

// TypeScript keeps one type and one value of a name apart, so a type-only declaration and a
// value-only one of the same name are not a duplicate; any other pair is, a class and an
// interface included, which TypeScript merges into one type. The namespaces are counted apart:
// one namespace of a name is a Kotlin companion.
private val isDuplicate: (List<String>) -> Boolean = { kinds ->
    kinds.size > 1 && !(kinds.size == 2 && kinds.count { it in typeOnly } == 1 && kinds.count { it in valueOnly } == 1)
}

internal fun duplicatedDeclarations(fresh: File): List<String> =
    fresh.comparedFiles().filterKeys { it.endsWith(".d.mts") }.flatMap { (path, file) ->
        declaredName.findAll(file.readText())
            .map { it.groupValues[1].removePrefix("const ") to it.groupValues[2] }
            .groupBy { (kind, name) -> (kind == "namespace") to name }
            .filterValues { declarations -> isDuplicate(declarations.map { it.first }) }
            .map { (key, declarations) ->
                "$path declares `${key.second}` twice: " +
                    declarations.joinToString(", ") { (kind, name) -> "$kind $name" } +
                    "; TypeScript merges them into one, or rejects the pair."
            }
    }

private val metadataInterfaces = Regex("""\[([A-Za-z_$][A-Za-z0-9_$]*(?:, [A-Za-z_$][A-Za-z0-9_$]*)+)\]""")
private val reorderedRegions = setOf("//region block: pre-declaration", "//region block: post-declaration")

/**
 * The Kotlin/JS emitter (2.4) reorders the top-level declarations and the interface lists of
 * `initMetadataFor*` between two compilations of one source. The canonical form sorts both,
 * outside the regions and in the pre- and post-declaration ones; elsewhere a reorder is a change.
 */
internal fun canonicalMjs(text: String): String {
    data class Block(val isSorted: Boolean) {
        val declarations = mutableListOf<StringBuilder>()
    }
    val blocks = mutableListOf(Block(isSorted = true))
    text.lineSequence().forEach { line ->
        val declarations = blocks.last().declarations
        when {
            line.startsWith("//region") -> {
                blocks.add(Block(isSorted = false).apply { declarations.add(StringBuilder(line)) })
                blocks.add(Block(isSorted = line in reorderedRegions))
            }
            line.startsWith("//endregion") -> {
                blocks.add(Block(isSorted = false).apply { declarations.add(StringBuilder(line)) })
                blocks.add(Block(isSorted = true))
            }
            declarations.isEmpty() || line.isNotEmpty() && !line[0].isWhitespace() && line[0] !in "})]" -> {
                declarations.add(StringBuilder(line))
            }
            else -> {
                declarations.last().append('\n').append(line)
            }
        }
    }
    return blocks.joinToString("\n") { block ->
        val declarations = block.declarations.map { it.toString() }
        if (!block.isSorted) {
            declarations.joinToString("\n")
        } else {
            declarations.map { declaration ->
                if (!declaration.startsWith("initMetadataFor")) {
                    declaration
                } else {
                    metadataInterfaces.replace(declaration) { match ->
                        "[" + match.groupValues[1].split(", ").sorted().joinToString(", ") + "]"
                    }
                }
            }.sorted().joinToString("\n")
        }
    }
}
