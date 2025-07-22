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

                val inputDir = if(config.inputDirectory.isPresent) {
                    config.inputDirectory.get()
                } else {
                    "${this.project.layout.buildDirectory.asFile.get().absolutePath}/js/packages/".also {
                        target.logger.info("fixers.kt2Ts.inputDirectory is not set. Default value [$it] will be used.")
                    }
                }
                from(inputDir) {
                    exclude("*-test")
                }
                into(config.outputDirectory.get())
                includeEmptyDirs = false

                val cleaning = config.buildCleaningRegex()

                doFirst {
                    cleanSubProjects(cleaning)
                }
                eachFile {
                    file.cleanFile(cleaning)
                }
            }
        }
    }
}

fun Project.cleanSubProjects(cleaning: Map<String, List<Pair<Regex, String>>>) {
    project.logger.info("----------------")
    subprojects.forEach { project ->
        project.cleanProject(cleaning)
    }
}

fun Project.cleanProject(cleaning: Map<String, List<Pair<Regex, String>>>) {
    val folder = "${project.layout.buildDirectory.asFile.get().absolutePath}/packages/js"
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
