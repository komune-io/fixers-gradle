package io.komune.fixers.gradle.plugin.config

private const val ANY_DEPTH = "**/"

/**
 * The `package.json` rules that declare each package [pins] forces at any depth at its pinned
 * version: a consumer installs the package from its `package.json`, which otherwise names the
 * version a Kotlin dependency declares, advisory included. A pin under a parent is left out.
 */
internal fun pinnedDependencyCleaning(
    pins: Map<String, String> = FIXERS_YARN_PINS,
): List<Pair<Regex, String>> = pins.mapNotNull { (path, version) ->
    val name = path.takeIf { it.startsWith(ANY_DEPTH) }?.removePrefix(ANY_DEPTH)
    val separators = if (name?.startsWith("@") == true) 1 else 0
    name?.takeIf { it.count { char -> char == '/' } == separators }?.let {
        Regex("""(?<key>"${Regex.escape(it)}"\s*:\s*")[^"]*(?=")""") to "\${key}" + Regex.escapeReplacement(version)
    }
}
