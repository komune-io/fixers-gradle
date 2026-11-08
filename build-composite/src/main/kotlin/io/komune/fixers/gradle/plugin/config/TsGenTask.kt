package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.Kt2Ts
import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register


fun Project.configureKt2Ts(mainConfig: ConfigExtension?) {
    val target = this
    mainConfig?.kt2Ts?.let { config ->
        // Kotlin/JS writes build/js/packages while it assembles a library distribution.
        val jsDistributions = target.allprojects.map { project ->
            project.tasks.named { it.endsWith("ProductionLibraryDistribution") }
        }
        target.tasks {
            register<Delete>("cleanTsGen") {
                mustRunAfter(jsDistributions)
                delete(config.outputDirectory.get())
            }

            register<Copy>("tsGen") {
                dependsOn("cleanTsGen")
                dependsOn(jsDistributions)

                // Resolve all Project-dependent values at configuration time for configuration cache compatibility
                val inputDir = if(config.inputDirectory.isPresent) {
                    config.inputDirectory.get()
                } else {
                    "${target.layout.buildDirectory.asFile.get().absolutePath}/js/packages/".also {
                        target.logger.info("fixers.kt2Ts.inputDirectory is not set. Default value [$it] will be used.")
                    }
                }
                val outputDir = target.file(config.outputDirectory.get())
                from(inputDir) {
                    exclude("*-test")
                }
                into(outputDir)
                includeEmptyDirs = false

                val cleaning = config.buildCleaningRegex()
                doLast {
                    outputDir.walkTopDown().filter { it.isFile }.forEach { it.cleanFile(cleaning) }
                }
            }
        }
    }
}

fun cleanProjectDir(folder: String, cleaning: Map<String, List<Pair<Regex, String>>>) {
    File(folder).listFiles()?.forEach { file ->
        file.cleanFile(cleaning)
    }
}

fun Kt2Ts.buildCleaningRegex(): Map<String, List<Pair<Regex, String>>> {
    val additionalCleaningMap = additionalCleaning.get()
    val cleaning = mutableMapOf(
        ".d.ts" to listOf(
            Regex("""(?m).*__doNotImplementIt.*\n""") to "",
            Regex(""".*readonly __doNotUseOrImplementIt.*;\n""") to "",
            Regex(""".*__doNotUseOrImplementIt:*[\s\S].*\n.*\n.*;""") to "",
            Regex("""kotlin.js.""") to "",
            Regex("""org.w3c.dom.url.""") to "",
            Regex("""org.w3c.dom.""") to "",
            Regex(""" (?:any|Nullable<any>)/\* ([^*/]*) \*/""") to " $1",
            Regex("""type Nullable<T> = T \| null \| undefined\n""") to "",
            Regex("""(?<=\(|, |readonly )(\w*)(\?)?: Nullable<([\w\.<>, \[\]]*)>(?=\)|, |;|/*)""") to "$1?: $3",
            Regex("""kotlin.collections.Map""") to "Record",
            Regex(""", kotlin\.collections\.List<(.*?)>""") to ", $1[]", // handles Record<string, List<T>>,
            Regex("""kotlin\.collections\.List<(.*?>?)>""") to "$1[]",
            Regex("""kotlin\.collections\.List<(.*?>?)>""") to "$1[]", // in case of List<List<T>>
            Regex("""kotlin.Long""") to "number",
            Regex("""static get Companion(.*\n)*?(\s)*}( &.*)?;""") to "",
            Regex("""abstract class (\w+)(?: implements [\w.]*?)? \{[\s\S]*?(?:\1)"""
                    + """;[\s]*get name\(\): ((?:\"\w+\" \| )*\"(\w+)\")[\s\S]*?\}""") to "type $1 = $2;",
        ) + (additionalCleaningMap[".d.ts"] ?: emptyList()),
        // Modern Kotlin/JS ESM exporter output (KtList / KtMap / bigint), emitted as `.d.mts`.
        // The `.d.ts` block above targets the legacy exporter names and never matches these files
        // (a name ending in `.d.mts` does not end with `.d.ts`), so the whole set is restated here.
        ".d.mts" to dmtsCleaning()
            + (additionalCleaningMap[".d.mts"] ?: emptyList()),
        "package.json" to listOf(
            Regex("""("devDependencies": \{)(.|\n)*?(},)""") to "$1$3"
        ) + (additionalCleaningMap["package.json"] ?: emptyList())
    )

    // Add any additional cleaning patterns not already included
    additionalCleaningMap.filterKeys { it !in cleaning.keys }.forEach { (key, value) ->
        cleaning[key] = value
    }

    return cleaning
}

private fun File.cleanFile(cleaning: Map<String, List<Pair<Regex, String>>>) {
    cleaning.forEach { (suffix, changes) ->
        if (name.endsWith(suffix)) {
            val content = readText()

            val newContent = changes.fold(content) { acc, (old, new) ->
                acc.replace(old, new)
            }

            if (newContent != content) {
                writeText(newContent)
            }
        }
    }
}

private const val TRAILING_NULLABLE_PASSES = 4

private const val LIST_PASSES = 3

// `name?: type/* comment */ | null`, from the name, type and comment groups of the rules below.
private const val OPTIONAL_OR_NULL = "$1?: $2$3 | null"

// A type nested at most three levels deep. `=>` is matched first so that the `>` of an arrow
// never closes a type argument.
private const val TYPE = """(?:=>|[^<>\n]|<(?:=>|[^<>\n]|<(?:=>|[^<>\n]|<(?:=>|[^<>\n])*>)*>)*>)*"""

/**
 * Rules for the declarations of the Kotlin/JS ES module exporter. A Kotlin `T?` is typed
 * `T | null`: Kotlin and its JSON serializers send `null`, not an absent value.
 */
private fun dmtsCleaning(): List<Pair<Regex, String>> {
    return listOf(
        // The marker body is matched exactly: a lazy body would run on to the next `};` of the file.
        Regex("""(?m)^ *readonly __doNotUseOrImplementIt: \{\n(?: +readonly [^:\n]+: unique symbol;\n)+ *\}"""
                + """(?:\s*&\s*[\w.<>, ]*\["__doNotUseOrImplementIt"\])*;\n""") to "",
        Regex("""(?m)^ *readonly __doNotUseOrImplementIt: [\w.<>, ]*\["__doNotUseOrImplementIt"\];\n""") to "",
        Regex("""type Nullable<T> = T \| null \| undefined\n""") to "",
        // The emitter declares the collections with their variance (`KtList<out E>`) since Kotlin 2.4.
        Regex("""export declare interface (Kt(?:List|Set|Map))<(?:K, )?(?:out )?[EV]>[\s\S]*?\n"""
                + """export declare namespace \1 \{[\s\S]*?\n\}\n""") to "",
        // A function type has to be parenthesised before a union or `[]` is appended to it.
        Regex("""(Nullable|KtList|KtSet)<(\((?:[^()\n]|\([^()\n]*\))*\) => $TYPE)>""") to "$1<($2)>",
        // A nullable element type has to become a union inside the array; unwrap it before the
        // collapse below, otherwise the property rule further down turns it into an optional
        // property instead (`x?: T[]`, which says the property may be absent, not the elements).
        Regex("""Kt(List|Set)<Nullable<($TYPE)>>""") to "Kt$1<($2 | null)>",
        // Collapses one level of nesting per pass, the outermost first: three passes cover
        // `KtList<KtList<KtList<T>>>`, as deep as TYPE reaches.
        *Array(LIST_PASSES) { Regex("""Kt(?:List|Set)<($TYPE)>""") to "$1[]" },
        Regex("""KtMap<""") to "Record<",
        // Coupled to the exact enum layout the Kotlin/JS emitter produces, so a Kotlin bump can
        // break it. Keep the inner static-get block matched precisely: a lazy `[\s\S]*?` next to
        // the `\1` backref makes a failed match backtrack exponentially and pegs tsGen at 100% CPU.
        // An enum that implements an interface or declares members is left a class: its consumers
        // read its entries and members, which the .mjs still exports.
        Regex("""export declare abstract class (\w+) \{\n    private constructor\(\);\n"""
                + """(?:    static get \w+\(\): \1 & \{\n        get name\(\): "\w+";\n"""
                + """        get ordinal\(\): \d+;\n    \};\n)*"""
                + """    static values\(\): \[[^\]]*\];\n    static valueOf\(value: string\): \1;\n"""
                + """    get name\(\): ((?:"\w+" \| )*"\w+");\n    get ordinal\(\): [\d \|]+;\n\}\n"""
        ) to "export type $1 = $2;\n",
        // kotlinx-datetime Instant / LocalDate serialize as ISO strings over the wire.
        Regex("""Nullable<any>/\* Nullable<(?:Instant|LocalDate)> \*/""") to "Nullable<string>",
        Regex("""any/\* (?:Instant|LocalDate) \*/""") to "string",
        // A property the JSON may omit is optional; a parameter keeps the arity Kotlin gave it.
        Regex("""(?<=readonly )(\w+)\??: Nullable<($TYPE)>((?:/\*[^*]*\*/)?)(?=;)""") to
                OPTIONAL_OR_NULL,
        Regex("""(?<=\(|, )(\w+)\?: Nullable<($TYPE)>((?:/\*[^*]*\*/)?)(?=\)|, )""") to
                OPTIONAL_OR_NULL,
        // A parameter with a default value arrives as `x: Nullable<T> | undefined`.
        Regex("""(?<=\(|, )(\w+): Nullable<($TYPE)>((?:/\*[^*]*\*/)?) \| undefined(?=\)|, )""") to
                "$1: $2$3 | null | undefined",
        // A setter parameter can never be optional (TS1051).
        Regex("""(set \w+\()(\w+): Nullable<($TYPE)>((?:/\*[^*]*\*/)?)(?=\))""") to "$1$2: $3$4 | null",
        // A trailing nullable parameter stays optional; one that a required parameter follows
        // cannot be (TS1016). One pass per trailing parameter, from the last.
        *Array(TRAILING_NULLABLE_PASSES) {
            Regex("""(?<=\(|, )(\w+): Nullable<($TYPE)>((?:/\*[^*]*\*/)?)(?=\)|, \w+\?: )""") to OPTIONAL_OR_NULL
        },
        Regex("""(?<=\(|, )(\w+): Nullable<($TYPE)>((?:/\*[^*]*\*/)?)(?=\)|, )""") to "$1: $2$3 | null",
        Regex("""get (\w+)\(\): Nullable<($TYPE)>((?:/\*[^*]*\*/)?)(?=;)""") to "get $1(): $2$3 | null",
        Regex("""\): Nullable<($TYPE)>((?:/\*[^*]*\*/)?)(?=;)""") to "): $1$2 | null",
        // What is left sits inside another type, such as the values of a map.
        Regex("""(?<!/\* )Nullable<($TYPE)>""") to "($1 | null)",
        Regex("""\bbigint\b(?!\??:)""") to "number",
        Regex("""(?<=: )any \| (?:null|undefined)(?=[;,)])""") to "any",
    )
}
