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
        target.tasks {
            register<Delete>("cleanTsGen") {
                delete(config.outputDirectory.get())
            }

            register<Copy>("tsGen") {
                dependsOn("cleanTsGen")

                // Resolve all Project-dependent values at configuration time for configuration cache compatibility
                val inputDir = if(config.inputDirectory.isPresent) {
                    config.inputDirectory.get()
                } else {
                    "${target.layout.buildDirectory.asFile.get().absolutePath}/js/packages/".also {
                        target.logger.info("fixers.kt2Ts.inputDirectory is not set. Default value [$it] will be used.")
                    }
                }
                from(inputDir) {
                    exclude("*-test")
                }
                into(config.outputDirectory.get())
                includeEmptyDirs = false

                val cleaning = config.buildCleaningRegex()

                // Capture subproject paths at configuration time (not Project references)
                val subprojectBuildDirs = target.subprojects.map { subproject ->
                    "${subproject.layout.buildDirectory.asFile.get().absolutePath}/packages/js"
                }

                doFirst {
                    cleanSubProjectDirs(subprojectBuildDirs, cleaning)
                }
                eachFile {
                    file.cleanFile(cleaning)
                }
            }
        }
    }
}

private fun cleanSubProjectDirs(buildDirs: List<String>, cleaning: Map<String, List<Pair<Regex, String>>>) {
    buildDirs.forEach { folder ->
        cleanProjectDir(folder, cleaning)
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
        ".d.mts" to listOf(
            Regex("""    readonly __doNotUseOrImplementIt: \{[\s\S]*?\}(?:\s*&\s*[\w.<>]*\["__doNotUseOrImplementIt"\])*;\n""") to "",
            Regex("""    readonly __doNotUseOrImplementIt: [\w.<>]*\["__doNotUseOrImplementIt"\];\n""") to "",
            Regex("""type Nullable<T> = T \| null \| undefined\n""") to "",
            Regex("""export declare interface KtList<E>[\s\S]*?\nexport declare namespace KtList \{[\s\S]*?\n\}\n""") to "",
            Regex("""KtList<([^>]*(?:<[^>]*>)*)>""") to "$1[]",
            Regex("""KtList<([^>]*(?:<[^>]*>)*)>""") to "$1[]",
            Regex("""export declare interface KtMap<K, V>[\s\S]*?\nexport declare namespace KtMap \{[\s\S]*?\n\}\n""") to "",
            Regex("""KtMap""") to "Record",
            // Coupled to the exact enum layout the Kotlin/JS emitter produces, so a Kotlin bump can
            // break it. Keep the inner static-get block matched precisely: a lazy `[\s\S]*?` next to
            // the `\1` backref makes a failed match backtrack exponentially and pegs tsGen at 100% CPU.
            Regex("""export declare abstract class (\w+) \{\n    private constructor\(\);\n(?:    static get \w+\(\): \1 & \{\n        get name\(\): "\w+";\n        get ordinal\(\): \d+;\n    \};\n)*    static values\(\): \[[^\]]*\];\n    static valueOf\(value: string\): \1;\n    get name\(\): ((?:"\w+" \| )*"\w+");\n    get ordinal\(\): [\d \|]+;\n\}\n""") to "export type $1 = $2;\n",
            // kotlinx-datetime Instant / LocalDate serialize as ISO strings over the wire.
            Regex("""Nullable<any>/\* Nullable<(?:Instant|LocalDate)> \*/""") to "Nullable<string>",
            Regex("""any/\* (?:Instant|LocalDate) \*/""") to "string",
            Regex("""(?<=\(|, |readonly )(\w*)(\?)?: Nullable<([\w.<>, \[\]]*)>(?=\)|, |;|/*)""") to "$1?: $3",
            Regex("""get (\w+)\(\): Nullable<([^>]+)>""") to "get $1(): $2 | undefined",
            Regex("""\): Nullable<([\w.<>, \[\]]*)>""") to "): $1 | undefined",
            Regex(""": bigint""") to ": number",
        ) + (additionalCleaningMap[".d.mts"] ?: emptyList()),
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
