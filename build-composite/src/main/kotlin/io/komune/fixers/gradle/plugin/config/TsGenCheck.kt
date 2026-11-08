package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.model.Kt2Ts
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

internal const val NODE_MODULES = "node_modules"

/**
 * Registers `tsGenCheck`, which regenerates the declarations into the build directory and fails
 * when [outputDir] differs from them, or when a `.d.mts` declares a name twice.
 * It is not wired into `check`: only a project that commits [outputDir] can run it.
 */
internal fun Project.registerTsGenCheck(
    config: Kt2Ts,
    jsDistributions: List<Any>,
    inputDir: String,
    outputDir: File,
): TaskProvider<Sync> {
    val staging = layout.buildDirectory.dir("kt2ts-check").get().asFile
    val cleaning = config.buildCleaningRegex()
    val allowedDuplicates = config.allowedDuplicates
    val shownOutput = config.outputDirectory.get()
    return tasks.register<Sync>("tsGenCheck") {
        group = "verification"
        description = "Fails when ${config.outputDirectory.get()} is stale against the Kotlin/JS output."
        dependsOn(jsDistributions)
        from(inputDir) {
            exclude("*-test")
        }
        into(staging)
        includeEmptyDirs = false
        // Its verdict depends on the committed copy, which is not an input of the copy.
        doNotTrackState("Compares the regenerated declarations with the committed copy")
        doLast {
            staging.cleanTree(cleaning)
            val stale = staleFiles(staging, outputDir).map { "$shownOutput/$it differs from the Kotlin/JS output." }
            val duplicated = duplicatedDeclarations(staging, allowedDuplicates.get()).map { "$shownOutput/$it" }
            if (stale.isNotEmpty() || duplicated.isNotEmpty()) {
                throw GradleException(
                    "The TypeScript contract is not what the Kotlin generates:" +
                        (stale + duplicated).joinToString("") { "\n  $it" } +
                        (if (stale.isEmpty()) "" else "\nRegenerate it with `./gradlew tsGen` and commit the result.") +
                        (if (duplicated.isEmpty()) "" else "\nRename one of the two Kotlin declarations; the emitter has no namespaces.")
                )
            }
        }
    }
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

/** Source maps carry the paths of the machine that compiled them. */
private fun File.comparedFiles(): Map<String, File> =
    walkTopDown().onEnter { it.name != NODE_MODULES }
        .filter { it.isFile && !it.name.endsWith(".map") }
        .associateBy { it.relativeTo(this).invariantSeparatorsPath }

internal fun staleFiles(fresh: File, committed: File): List<String> {
    val expected = fresh.comparedFiles()
    val actual = committed.comparedFiles()
    return (expected.keys + actual.keys).sorted().filter { path ->
        val generated = expected[path]
        val current = actual[path]
        generated == null || current == null || generated.comparable() != current.comparable()
    }
}

// A class or interface and a namespace of the same name are a Kotlin companion. Unanchored, because
// the emitter writes no trailing newline.
private val declaredName = Regex("""export declare (?:abstract )?(interface|class|namespace) ([A-Za-z0-9_$]+)""")

internal fun duplicatedDeclarations(fresh: File, allowed: Set<String>): List<String> =
    fresh.comparedFiles().filterKeys { it.endsWith(".d.mts") }.flatMap { (path, file) ->
        val duplicated = declaredName.findAll(file.readText())
            .map { "${it.groupValues[1]} ${it.groupValues[2]}" }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        (duplicated - allowed).map {
            "$path declares `$it` twice; TypeScript merges the two into one type."
        } + (allowed - duplicated).map {
            "$path no longer declares `$it` twice; drop it from kt2Ts.allowedDuplicates."
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
