package io.komune.fixers.gradle.plugin.kotlin

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

private val COMMENT = Regex("""/\*[\s\S]*?\*/|//[^\n]*""")
private val JS_EXPORT_ALIAS = Regex("""^\s*import\s+kotlin\.js\.JsExport\s+as\s+(\w+)""", RegexOption.MULTILINE)

/**
 * The lines of a Kotlin source that use `@JsExport.Ignore`: the annotation, its import, an alias of `JsExport`
 * followed by `.Ignore`. Comments are left out, their lines kept so that the numbers hold.
 */
internal fun jsExportIgnores(source: String): List<IndexedValue<String>> {
    val code = COMMENT.replace(source) { comment -> comment.value.replace(Regex("[^\n]"), " ") }
    val names = listOf("JsExport") + JS_EXPORT_ALIAS.findAll(code).map { it.groupValues[1] }
    val ignore = Regex("""\b(?:${names.joinToString("|")})\s*\.\s*Ignore\b""")
    val lines = source.lines()
    return code.lineSequence().withIndex()
        .filter { (_, line) -> ignore.containsMatchIn(line) }
        .map { (index, _) -> IndexedValue(index + 1, lines[index].trim()) }
        .toList()
}

/**
 * Fails every Kotlin/JS compilation whose sources use `@JsExport.Ignore`. The annotation hides a member of an
 * exported declaration, so the JS surface becomes whatever the Kotlin API leaves once its non-JS members are
 * taken out: an interface with no callable member, a class with no constructor. The JS surface is written
 * instead, in `jsMain`, with JS types only, and the Kotlin API is not exported.
 */
internal fun Project.refuseJsExportIgnore() {
    tasks.withType(Kotlin2JsCompile::class.java).configureEach {
        val kotlinSources = sources
        val task = path
        doFirst {
            val found = kotlinSources.files.filter { file -> file.extension == "kt" }.sorted()
                .flatMap(File::jsExportIgnoreLines)
            if (found.isNotEmpty()) {
                throw GradleException(
                    "$task: @JsExport.Ignore leaves JS a Kotlin API with its members taken out. Write the JS form " +
                        "in jsMain, a declaration exported in its place that takes and returns JS values only " +
                        "(Promise, arrays, JSON), and stop exporting the Kotlin one; or stop exporting the " +
                        "declaration:\n" + found.joinToString("\n")
                )
            }
        }
    }
}

private fun File.jsExportIgnoreLines(): List<String> =
    jsExportIgnores(readText()).map { (line, text) -> "$path:$line: $text" }
