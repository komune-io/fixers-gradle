package io.komune.fixers.gradle.plugin.kotlin

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

private const val SPACE = """\s*"""
private val IMPORT_ALIAS = Regex("""\bimport\s+kotlin\s*\.\s*js\s*\.\s*JsExport\s+as\s+(\w+)""")
private val IMPORT_STAR = Regex("""\bimport\s+kotlin\s*\.\s*js\s*\.\s*JsExport\s*\.\s*\*""")

/**
 * The lines of a Kotlin source that use `@JsExport.Ignore`: the annotation (qualified or not, under a use-site
 * target, across lines), its import, an alias of `JsExport` followed by `.Ignore`, and a bare `Ignore` annotation
 * once `kotlin.js.JsExport.*` is imported. Strings and comments are left out, their lines kept so that the
 * numbers hold. Names are matched, not resolved: a declaration of the user's own named `JsExport` with an
 * `Ignore` inside is refused too.
 */
internal fun jsExportIgnores(source: String): List<IndexedValue<String>> {
    val code = codeOnly(source)
    val names = listOf("JsExport") + IMPORT_ALIAS.findAll(code).map { it.groupValues[1] }
    val qualified = Regex("""\b(?:${names.joinToString("|")})$SPACE\.$SPACE""" + """Ignore\b""")
    val bare = Regex("""@$SPACE(?:\w+$SPACE:$SPACE)?Ignore\b""").takeIf { IMPORT_STAR.containsMatchIn(code) }
    val patterns = listOfNotNull(qualified, bare)
    val lines = source.lines()
    return patterns.asSequence()
        .flatMap { pattern -> pattern.findAll(code).map { match -> code.lineOf(match.range.first) } }
        .distinct()
        .sorted()
        .map { line -> IndexedValue(line + 1, lines[line].trim()) }
        .toList()
}

private fun String.lineOf(offset: Int): Int = (0 until offset).count { this[it] == '\n' }

/**
 * [source] with every string, character literal and comment blanked, newlines kept: what is left is code. Knows
 * `"…"` with escapes, raw `"""…"""`, templates `${…}` holding code (strings and braces included), multi-dollar
 * strings (`$$"…"`, whose templates are `$${…}`), `'…'`, names between backticks, and nested `/* /* */ */`
 * comments.
 */
internal fun codeOnly(source: String): String = CodeOnly(source).run()

private const val TRIPLE_QUOTE = "\"\"\""

private class CodeOnly(private val source: String) {
    private val out = StringBuilder(source.length)

    // The modes left to return to: the code around a string or a comment, the string around a template.
    private val stack = ArrayDeque<Mode>()
    private var mode: Mode = Mode.Code(0)
    private var i = 0

    fun run(): String {
        while (i < source.length) {
            when (val current = mode) {
                is Mode.Code -> if (!opensNonCode(current)) braceOrKeep(current)
                is Mode.Block -> block(current)
                is Mode.Text -> text(current)
            }
        }
        return out.toString()
    }

    private fun startsWith(text: String) = source.startsWith(text, i)

    // A comment, a string, a character literal or a backticked name starting here, entered and blanked.
    private fun opensNonCode(current: Mode.Code): Boolean {
        // `$$"…"` interpolates `$${…}` only: the dollars before a string's opening quote.
        val dollars = maxOf(a = 1, b = (i - 1 downTo 0).takeWhile { at -> source[at] == '$' }.count())
        when {
            startsWith("//") -> blank(source.indexOf('\n', i).let { end -> if (end < 0) source.length else end } - i)
            startsWith("/*") -> enter(Mode.Block(1), current, length = 2)
            startsWith(TRIPLE_QUOTE) -> enter(Mode.Text(dollars, isRaw = true), current, TRIPLE_QUOTE.length)
            startsWith("\"") -> enter(Mode.Text(dollars, isRaw = false), current, length = 1)
            startsWith("'") -> blank(charLiteralLength(source, i))
            startsWith("`") -> backticked()
            else -> return false
        }
        return true
    }

    private fun braceOrKeep(current: Mode.Code) {
        val isInTemplate = stack.lastOrNull() is Mode.Text
        when {
            startsWith("{") -> {
                mode = Mode.Code(current.depth + 1)
                keep()
            }
            startsWith("}") && current.depth == 0 && isInTemplate -> {
                leave(1)
            }
            startsWith("}") -> {
                mode = Mode.Code(maxOf(a = 0, b = current.depth - 1))
                keep()
            }
            else -> {
                keep()
            }
        }
    }

    private fun block(current: Mode.Block) {
        when {
            startsWith("/*") -> {
                mode = Mode.Block(current.depth + 1)
                blank(2)
            }
            startsWith("*/") && current.depth == 1 -> {
                leave(2)
            }
            startsWith("*/") -> {
                mode = Mode.Block(current.depth - 1)
                blank(2)
            }
            else -> {
                blank(1)
            }
        }
    }

    private fun text(current: Mode.Text) {
        val template = "$".repeat(current.dollars) + "{"
        when {
            current.isRaw && startsWith(TRIPLE_QUOTE) ->
                leave(generateSequence(TRIPLE_QUOTE.length) { it + 1 }.first { source.getOrNull(i + it) != '"' })
            !current.isRaw && startsWith("\\") -> blank(minOf(a = 2, b = source.length - i))
            !current.isRaw && startsWith("\"") -> leave(1)
            startsWith(template) -> enter(Mode.Code(0), current, length = template.length)
            else -> blank(1)
        }
    }

    // A name between backticks may hold quotes: its text is kept as code, its backticks blanked.
    private fun backticked() {
        blank(1)
        while (i < source.length && source[i] != '`' && source[i] != '\n') keep()
        if (source.getOrNull(i) == '`') blank(1)
    }

    private fun enter(inner: Mode, outer: Mode, length: Int) {
        stack.addLast(outer)
        mode = inner
        blank(length)
    }

    private fun leave(length: Int) {
        mode = stack.removeLast()
        blank(length)
    }

    private fun keep() {
        out.append(source[i])
        i++
    }

    private fun blank(length: Int) {
        repeat(length) { k -> out.append(if (source[i + k] == '\n') '\n' else ' ') }
        i += length
    }
}

private fun charLiteralLength(source: String, start: Int): Int {
    var end = start + 1
    while (end < source.length && source[end] != '\'' && source[end] != '\n') {
        end += if (source[end] == '\\') 2 else 1
    }
    return minOf(a = end + 1, b = source.length) - start
}

private sealed interface Mode {
    data class Code(val depth: Int) : Mode
    data class Block(val depth: Int) : Mode
    data class Text(val dollars: Int, val isRaw: Boolean) : Mode
}

/**
 * Fails every Kotlin/JS compilation whose sources use `@JsExport.Ignore`. The annotation hides a member of an
 * exported declaration, so the JS surface becomes whatever the Kotlin API leaves once its non-JS members are
 * taken out: an interface with no callable member, a class with no constructor. The JS surface is written
 * instead, in `jsMain`, with JS types only, and the Kotlin API is not exported.
 *
 * `withType(Kotlin2JsCompile)` reaches `compileKotlinJs` and `compileTestKotlinJs`, and the four IR link tasks
 * (`compile{Development,Production}LibraryKotlinJs`, `compileTest{Development,Production}ExecutableKotlinJs`),
 * which run after a compile task has already read the same sources: only `.kt` files are read.
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
                        "declaration. The annotation is found by name, outside strings and comments; an alias of it " +
                        "declared in a dependency is not seen:\n" + found.joinToString("\n")
                )
            }
        }
    }
}

private fun File.jsExportIgnoreLines(): List<String> =
    jsExportIgnores(readText()).map { (line, text) -> "$path:$line: $text" }
