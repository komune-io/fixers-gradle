package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.Kt2Ts
import io.komune.fixers.gradle.config.model.Kt2TsFiles
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register

/**
 * Registers on the root project `tsGen`, which writes the TypeScript contract of the build from its
 * Kotlin/JS output, `cleanTsGen`, and `checkTsGen` (see [registerTsGenCheck]).
 */
internal fun Project.configureKt2Ts(mainConfig: ConfigExtension?) {
    val target = rootProject
    val config = mainConfig?.kt2Ts ?: return
    // Kotlin/JS writes build/js/packages while it assembles a library distribution.
    val jsDistributions = target.allprojects.map { project ->
        project.tasks.named { it.endsWith(JS_DISTRIBUTION_SUFFIX) }
    }
    // Resolved when a task is realized, not when the plugin is applied.
    val source by lazy {
        val buildDirectory = target.layout.buildDirectory.asFile.get()
        target.file(config.inputDirectory.orNull ?: "${buildDirectory.absolutePath}/js/packages/")
    }
    val output by lazy { target.file(config.outputDirectory.get()) }
    val cleaning by lazy { config.buildCleaningRegex() }
    target.tasks {
        register<Delete>("cleanTsGen") {
            mustRunAfter(jsDistributions)
            delete(config.outputDirectory.get())
        }

        // Not a Copy or a Sync: either tracks its whole destination, and with it the links a JS package
        // manager makes under node_modules — a dangling one fails the task, each install makes it stale.
        register("tsGen") {
            dependsOn(jsDistributions)
            mustRunAfter("cleanTsGen")
            val from = source
            val into = output
            val rules = cleaning
            val committed = config.committedFiles
            val additional = config.additionalCleaning
            inputs.files(target.emittedTree(from)).withPathSensitivity(PathSensitivity.RELATIVE).skipWhenEmpty()
            inputs.property("cleaning", rules.fingerprint())
            inputs.property("committedFiles", committed)
            outputs.files(target.packageTree(into))
            doLast {
                into.writeContract(
                    source = from,
                    rules = rules,
                    committed = committed.get(),
                    additional = additional.get(),
                )
            }
        }
    }
    target.registerTsGenCheck(config, jsDistributions) { Triple(first = source, second = output, third = cleaning) }
}

// Whether this mode writes the file at [path], relative to the packages directory.
private fun Kt2TsFiles.writes(path: String): Boolean = when (this) {
    Kt2TsFiles.ALL -> true
    Kt2TsFiles.DECLARATIONS ->
        path.endsWith(DMTS) || path.substringAfterLast('/') == PACKAGE_JSON
}

/**
 * Makes this directory the contract `tsGen` writes from the Kotlin/JS output under [source]: the
 * files [committed] keeps, cleaned by [rules], each `package.json` at [Kt2TsFiles.CONTRACT_VERSION],
 * and without its `main` entry under [Kt2TsFiles.DECLARATIONS]: they ship no module for it to name.
 * Fails when one of the [additional] rules, the build's own, changes no file of the contract.
 */
internal fun File.writeContract(
    source: File,
    rules: Map<String, List<Pair<Regex, String>>>,
    committed: Kt2TsFiles,
    additional: Map<String, List<Pair<Regex, String>>> = emptyMap(),
) {
    syncFrom(source) { committed.writes(it) }
    val applied = cleanTree(rules)
    val dead = additional.flatMap { (suffix, changes) ->
        changes.filter { (old, new) -> CleaningRule(suffix, old, new) !in applied }
            .map { (old, _) -> "$suffix: $old" }
    }
    if (dead.isNotEmpty()) {
        throw GradleException(
            "kt2Ts additionalCleaning rules matched no file of the contract:\n" + dead.joinToString("\n") +
                "\nThe declarations keep the Kotlin types since fixers 0.47.0 (KtList, KtMap, bigint, enum " +
                "classes), so a rule written against the former output (`[]`, `Record<`, `number`) no longer " +
                "applies: delete it, or rewrite it against the declarations tsGen writes now."
        )
    }
    packageFiles().filterKeys { it.substringAfterLast('/') == PACKAGE_JSON }.values.forEach { file ->
        val versioned = packageVersion.replace(
            input = file.readText(),
            replacement = """  "version": "${Kt2TsFiles.CONTRACT_VERSION}"""",
        )
        val written = if (committed == Kt2TsFiles.DECLARATIONS) {
            packageMain.replace(input = versioned, replacement = "")
        } else {
            versioned
        }
        file.writeText(written)
    }
}

private val packageMain = Regex("""^  "main": "[^"]*",\n""", RegexOption.MULTILINE)

// Each NPM package is cleaned alone, so a rule may rightly change none of one package's files;
// `tsGen` and `checkTsGen`, which see every package, refuse a rule that changes nothing.
fun cleanProjectDir(folder: String, cleaning: Map<String, List<Pair<Regex, String>>>) {
    File(folder).listFiles()?.forEach { file ->
        file.cleanFile(cleaning)
    }
}

fun Kt2Ts.buildCleaningRegex(): Map<String, List<Pair<Regex, String>>> {
    val additionalCleaningMap = additionalCleaning.get()
    val cleaning = mutableMapOf(
        // The ES module exporter writes `.d.mts`; fixers compiles no other JS module kind.
        DMTS to dmtsCleaning()
            + additionalCleaningMap[DMTS].orEmpty(),
        PACKAGE_JSON to listOf(
            Regex("""("devDependencies": \{)(.|\n)*?(},)""") to "$1$3"
        ) + additionalCleaningMap[PACKAGE_JSON].orEmpty()
    )

    // Add any additional cleaning patterns not already included
    additionalCleaningMap.filterKeys { it !in cleaning.keys }.forEach { (key, value) ->
        cleaning[key] = value
    }

    return cleaning
}

// A rule by value: the configuration cache hands a task its own copies of the regexes.
internal data class CleaningRule(
    val suffix: String,
    val pattern: String,
    val options: Set<RegexOption>,
    val replacement: String,
) {
    constructor(suffix: String, regex: Regex, replacement: String) :
        this(suffix = suffix, pattern = regex.pattern, options = regex.options, replacement = replacement)
}

/** Cleans every file of this tree with [cleaning], and answers the rules that changed one. */
internal fun File.cleanTree(cleaning: Map<String, List<Pair<Regex, String>>>): Set<CleaningRule> =
    walkTopDown().onEnter { it.name != NODE_MODULES }.filter { it.isFile }
        .flatMap { it.cleanFile(cleaning) }
        .toSet()

private fun File.cleanFile(cleaning: Map<String, List<Pair<Regex, String>>>): Set<CleaningRule> =
    cleaning.filterKeys { suffix -> name.endsWith(suffix) }
        .flatMap { (suffix, changes) -> cleanFile(suffix, changes) }
        .toSet()

// Cleans this file with the [changes] of its [suffix], and answers those that matched it.
private fun File.cleanFile(suffix: String, changes: List<Pair<Regex, String>>): List<CleaningRule> {
    val content = readText()
    val applied = mutableListOf<CleaningRule>()
    val cleaned = changes.fold(content) { acc, (old, new) ->
        if (old.containsMatchIn(acc)) applied += CleaningRule(suffix, old, new)
        acc.replace(old, new)
    }
    val isDeclarations = suffix == DMTS
    val newContent = if (isDeclarations) cleaned.withUnknownForAny() else cleaned
    if (isDeclarations) {
        checkDeclarationsCleaned(newContent)
    }
    if (newContent != content) {
        writeText(newContent)
    }
    return applied
}

private const val DMTS = ".d.mts"

internal const val PACKAGE_JSON = "package.json"

// `any` where a type goes: after `:`, `<`, `,`, `|`, `&`, `(`, `=>` or `extends`; not a name, which
// `(`, `.` or `:` follows.
private const val TYPE_POSITION = """(?<=(?:[:<,|&(]|=>|\bextends)\s?)\bany\b(?![\w"(.:?])"""
private val UNTYPED = Regex(TYPE_POSITION)

// A Kotlin `Any`, or a type the emitter types `any` (KtSingleton, a ktor hook), outside a comment
// and a string literal type: what the export cannot express is followed by its Kotlin name.
private val ANY = Regex("""$TYPE_POSITION(?!/\*)""")

// `any` lets every value through: a Kotlin `Any` is typed `unknown`, which TypeScript makes callers narrow.
private fun String.withUnknownForAny(): String {
    val comments = COMMENT.findAll(this).map { it.range }.toList()
    return ANY.replace(this) { match -> if (comments.any { match.range.first in it }) match.value else "unknown" }
}
private val COMMENT = Regex("""/\*[\s\S]*?\*/""")

// Fails when a type is left `any`, which lets every value through. Every task that cleans
// declarations runs it: `tsGen`, `checkTsGen`, and `npmTsGen` before an NPM publication.
private fun File.checkDeclarationsCleaned(content: String) {
    val code = COMMENT.replace(content) { comment -> comment.value.replace(Regex("[^\n]"), " ") }
    val lines = content.lines()
    val untyped = code.lineSequence().withIndex()
        .filter { (_, line) -> UNTYPED.containsMatchIn(line) }
        .map { (index, _) -> "$path:${index + 1}: ${lines[index].trim()}" }
        .toList()
    if (untyped.isNotEmpty()) {
        throw GradleException(
            "The declarations type as any what the JS export cannot express; export the type, or stop "
                + "exporting the declaration and write its JS form in jsMain:\n" + untyped.joinToString("\n")
        )
    }
}

// The exported types describe the Kotlin objects, which is what the compiled module takes and
// returns: a `KtList`, a `bigint`, an enum instance, `T | null | undefined`. The emitter writes them
// so, and the rules leave them so. What the JSON of a type is, `Json<T>` says.
private fun dmtsCleaning(): List<Pair<Regex, String>> = listOf(
    // Written first, once: `npmTsGen` cleans a package in place, and may clean it twice.
    Regex("""\A(?![\s\S]*^export type Json<T> =)""", RegexOption.MULTILINE) to
        Regex.escapeReplacement(JSON_DECLARATIONS),
    // kotlinx-datetime and kotlin.time values reach the export only where a build suppresses
    // NON_EXPORTABLE_TYPE: opaque to TypeScript, named so that `Json` sends them as strings.
    Regex("""Nullable<any>/\* Nullable<(Instant|LocalDate)> \*/""") to "Nullable<KtOpaque<\"$1\">>",
    Regex("""any/\* (Instant|LocalDate) \*/""") to "KtOpaque<\"$1\">",
    // A companion `extends KtSingleton<…>()`, and TypeScript extends an object type only (TS2509).
    Regex("""(declare function KtSingleton<T>\(\): T & \(abstract new\(\) => )any\)""") to "$1object)",
)

/**
 * What `tsGen` writes at the head of every `.d.mts`. Each one declares the whole program of its
 * package, so this is the one copy its readers see; two packages' copies agree, since `Json` matches
 * the Kotlin types by their members, never by the brand the emitter gives each copy.
 */
internal val JSON_DECLARATIONS = """
    /** A Kotlin value the export cannot express, such as an `Instant`: opaque to TypeScript. */
    export declare interface KtOpaque<N extends string> {
        readonly __kotlinOpaque: N;
        readonly __doNotUseOrImplementIt: {
            readonly "kotlin.Opaque": unique symbol;
        };
    }
    /**
     * The JSON a Kotlin value of type `T` is sent as: a list or a set is an array, a map an object
     * (whose keys are optional when they are an enum's names), a `Long` a number, an enum its name,
     * an opaque value a string, and a nullable property may be absent. The exported types describe
     * the Kotlin objects; a body read from or written to the wire is a `Json<T>`, and the compiled
     * code accepts none: an instance of an exported class that implements an exported interface, or
     * of a data class, is refused at any depth.
     *
     * What it does not say: a sealed or polymorphic type carries no discriminant here, so a
     * serializer's `type` field is not typed. A nullable property may be absent: F2's Kotlin client
     * omits nulls (`explicitNulls = false`), Jackson and a default kotlinx `Json` write `null`. A
     * reader that requires the property refuses the JSON that omits it: a default kotlinx `Json`
     * does, Jackson and F2's `Json` read it as `null`. An instance of a class that is neither of the
     * two above is not told apart from a plain object, and a `Long` beyond 2^53 loses its precision
     * as a JavaScript number.
     */
    export type Json<T> =
        T extends { asJsReadonlyArrayView(): ReadonlyArray<infer E> } ? Json<E>[]
        : T extends { asJsReadonlySetView(): ReadonlySet<infer E> } ? Json<E>[]
        : T extends { asJsReadonlyMapView(): ReadonlyMap<infer K, infer V> } ? JsonMap<JsonKey<K>, Json<V>>
        : T extends { readonly __kotlinOpaque: string } ? string
        : T extends bigint ? number
        : T extends string | number | boolean | null | undefined ? T
        : T extends { readonly name: infer N extends string; readonly ordinal: infer O extends number }
            ? string extends N ? JsonObject<T> : number extends O ? JsonObject<T> : N
        : T extends ReadonlyArray<infer E> ? Json<E>[]
        : T extends (...args: never[]) => unknown ? never
        : T extends object ? JsonObject<T>
        : T;
    type JsonKey<K> = Json<K> extends string | number ? Json<K> : string;
    // A map holds the entries it has: keys out of a finite set, such as an enum's names, are optional.
    type JsonMap<K extends string | number, V> =
        string extends K ? Record<string, V> : number extends K ? Record<number, V> : { [P in K]?: V };
    type JsonField<T, P extends keyof T> =
        P extends "__doNotUseOrImplementIt" ? never : T[P] extends (...args: never[]) => unknown ? never : P;
    type JsonFields<T> = { [P in keyof T]-?: JsonField<T, P> }[keyof T];
    type JsonOptional<T> = {
        [P in JsonFields<T>]-?: null extends T[P] ? P : undefined extends T[P] ? P : never
    }[JsonFields<T>];
    // What a Kotlin object has and JSON never does: the brand of an exported interface, the members of a data class.
    type JsonObject<T> =
        { [P in Exclude<JsonFields<T>, JsonOptional<T>>]: Json<T[P]> } & { [P in JsonOptional<T>]?: Json<T[P]> }
        & { readonly [P in "__doNotUseOrImplementIt" | Exclude<"equals" | "hashCode", JsonFields<T>>]?: never };
""".trimIndent() + "\n"
