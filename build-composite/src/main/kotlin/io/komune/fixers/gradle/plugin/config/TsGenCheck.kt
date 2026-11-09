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
 * Registers `tsGenCheck`, which regenerates the declarations into the build directory and fails
 * when the output directory differs from them, or when a `.d.mts` exports a name twice: two of its
 * classes, interfaces, functions, constants, variables, enums or type aliases, as named in
 * JavaScript, or two namespaces, the one namespace of a companion being allowed. `check` runs it
 * in a build that emits Kotlin/JS.
 *
 * The compiler's `EXPORTING_JS_NAME_CLASH_ES` is reported when the declaring module compiles:
 * two declarations of one name that come from dependencies reach the linked declarations
 * unreported. This check reads the declarations of the whole program.
 */
internal fun Project.registerTsGenCheck(
    config: Kt2Ts,
    jsDistributions: List<Any>,
    inputDir: () -> File,
    outputDir: () -> File,
    cleaning: () -> Map<String, List<Pair<Regex, String>>>,
): TaskProvider<Task> {
    val staging = layout.buildDirectory.dir("kt2ts-check")
    val tsGenCheck = tasks.register("tsGenCheck") {
        val source = inputDir()
        val output = outputDir()
        val rules = cleaning()
        val shownOutput = config.outputDirectory.get()
        val stagingDir = staging.get().asFile
        group = "verification"
        description = "Fails when $shownOutput is stale against the Kotlin/JS output."
        dependsOn(jsDistributions)
        mustRunAfter("tsGen")
        // Not skipped when empty: a missing Kotlin output must fail the check, not pass it.
        inputs.files(emittedTree(source)).withPropertyName("emitted").withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(packageTree(output)).withPropertyName("committed").withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("cleaning", rules.fingerprint())
        outputs.dir(stagingDir)
        doLast {
            stagingDir.deleteRecursively()
            stagingDir.syncFrom(source)
            if (stagingDir.walkTopDown().none { it.isFile }) {
                throw GradleException("No Kotlin/JS output under $source to compare $shownOutput with.")
            }
            stagingDir.cleanTree(rules)
            val stale = staleFiles(stagingDir, output).map { "$shownOutput/$it." }
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
    // Every project that applies the config plugin has a kt2Ts block; only one that emits Kotlin/JS has a contract.
    val emitsJs = allprojects.any { project -> project.tasks.names.any { it.endsWith(JS_DISTRIBUTION_SUFFIX) } }
    if (emitsJs) {
        tasks.matching { it.name == "check" }.configureEach { dependsOn(tsGenCheck) }
    }
    return tsGenCheck
}

// The build's version, not the contract's. Anchored, so a dependency named `version` still counts.
private val packageVersion = Regex("^  \"version\": \"[^\"]*\"", RegexOption.MULTILINE)

private fun File.comparable(): String {
    val text = readText()
    return when {
        name == "package.json" -> packageVersion.replace(text, "  \"version\": \"\"")
        name.endsWith(".mjs") -> canonicalMjs(text)
        else -> text
    }
}

/** Every file of a package, by relative path, without the dependencies linked under node_modules. */
private fun File.packageFiles(): Map<String, File> =
    walkTopDown().onEnter { it.name != NODE_MODULES }
        .filter { it.isFile }
        .associateBy { it.relativeTo(this).invariantSeparatorsPath }

/**
 * Makes this directory hold what the Kotlin/JS build emitted under [source], its test packages
 * left out: copies every file, deletes those it no longer emits, and leaves node_modules alone.
 */
internal fun File.syncFrom(source: File) {
    val emitted = source.packageFiles().filterKeys { !it.substringBefore('/').endsWith("-test") }
    (packageFiles().keys - emitted.keys).forEach { resolve(it).delete() }
    emitted.forEach { (path, file) -> file.copyTo(resolve(path), overwrite = true) }
    walkBottomUp().onEnter { it.name != NODE_MODULES }
        .filter { it != this && it.isDirectory && it.list().isNullOrEmpty() }
        .forEach { it.delete() }
}

/** Source maps carry the paths of the machine that compiled them. */
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

// Every top-level export of a `.d.mts`, under the name JavaScript sees, `@JsName` applied. TypeScript
// merges a class and an interface of one name, and rejects any other pair; only a namespace of that
// name is a Kotlin companion. Unanchored, because the emitter writes no trailing newline.
private val declaredName = Regex(
    """export (?:declare )?(?:abstract )?""" +
        """(interface|class|namespace|function|const|let|var|enum|type) ([A-Za-z0-9_$]+)"""
)

internal fun duplicatedDeclarations(fresh: File): List<String> =
    fresh.comparedFiles().filterKeys { it.endsWith(".d.mts") }.flatMap { (path, file) ->
        declaredName.findAll(file.readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .groupBy { (kind, name) -> (kind == "namespace") to name }
            .filterValues { it.size > 1 }
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
    class Block(val sorted: Boolean) {
        val declarations = mutableListOf<StringBuilder>()
    }
    val blocks = mutableListOf(Block(sorted = true))
    text.lineSequence().forEach { line ->
        val declarations = blocks.last().declarations
        when {
            line.startsWith("//region") -> {
                blocks.add(Block(sorted = false).apply { declarations.add(StringBuilder(line)) })
                blocks.add(Block(sorted = line in reorderedRegions))
            }
            line.startsWith("//endregion") -> {
                blocks.add(Block(sorted = false).apply { declarations.add(StringBuilder(line)) })
                blocks.add(Block(sorted = true))
            }
            declarations.isEmpty() || (line.isNotEmpty() && !line[0].isWhitespace() && line[0] !in "})]") ->
                declarations.add(StringBuilder(line))
            else -> declarations.last().append('\n').append(line)
        }
    }
    return blocks.joinToString("\n") { block ->
        val declarations = block.declarations.map { it.toString() }
        if (!block.sorted) {
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
