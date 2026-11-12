package io.komune.fixers.gradle.plugin.publish

import java.io.File

/**
 * The staged files a snapshot needs to resolve: every `.pom`, `.module` and `maven-metadata.xml`,
 * every file a `.module` names, and their `.sha1`.
 *
 * Left out: signatures, `.md5`/`.sha256`/`.sha512`, and the attachments no `.module` names —
 * the placeholder javadoc jars, the sources jars of plain JVM modules, `kotlin-tooling-metadata.json`.
 * A file a `.module` names is never left out: resolvers fetch it by the variant that names it.
 * A version directory without a `.module` keeps every file but its signatures and extra checksums.
 */
internal object SnapshotFileSet {

    private const val KEPT_CHECKSUM = ".sha1"
    private val CHECKSUMS = listOf(".md5", ".sha1", ".sha256", ".sha512")
    private const val SIGNATURE = ".asc"
    private val ALWAYS_KEPT_SUFFIXES = setOf(".pom", ".module")

    // A `.module` file entry's url; the `available-at` urls point to another directory and carry a '/'.
    private val MODULE_FILE_URL = """"url"\s*:\s*"([^"/]+)"""".toRegex()
    private const val SNAPSHOT_TIMESTAMP = """SNAPSHOT|\d{8}\.\d{6}-\d+"""

    /** The [staged] files an upload sends, in a stable order: all of them, or of a snapshot the selection. */
    fun uploaded(stagingDir: File, staged: List<File>, isSnapshot: Boolean): List<File> =
        (if (isSnapshot) select(staged) else staged).sortedBy { it.relativeTo(stagingDir).path }

    fun select(files: List<File>): List<File> =
        files.groupBy { it.parentFile }.flatMap { (dir, inDir) -> selectIn(dir = dir, files = inDir) }

    private fun selectIn(dir: File, files: List<File>): List<File> {
        val mains = files.filter { file -> CHECKSUMS.none { file.name.endsWith(it) } && !file.name.endsWith(SIGNATURE) }
        val moduleFiles = mains.filter { it.name.endsWith(".module") }
        val kept = if (moduleFiles.isEmpty()) {
            mains.toSet()
        } else {
            val coordinates = Coordinates(artifactId = dir.parentFile.name, baseVersion = dir.name)
            val referenced = moduleFiles.flatMap { referencedSuffixes(it, coordinates) }.toSet()
            mains.filter { file -> isKept(file, coordinates, referenced) }.toSet()
        }
        val keptChecksums = files.filter { file ->
            file.name.endsWith(KEPT_CHECKSUM) && File(dir, file.name.removeSuffix(KEPT_CHECKSUM)) in kept
        }
        return kept.toList() + keptChecksums
    }

    private fun isKept(file: File, coordinates: Coordinates, referenced: Set<String>): Boolean {
        val suffix = coordinates.suffixOf(file.name)
        // A name this rule cannot read is kept rather than guessed at.
        return file.name.startsWith(MAVEN_METADATA) || suffix == null ||
            suffix in ALWAYS_KEPT_SUFFIXES || suffix in referenced
    }

    private fun referencedSuffixes(moduleFile: File, coordinates: Coordinates): List<String> =
        MODULE_FILE_URL.findAll(moduleFile.readText())
            .mapNotNull { coordinates.suffixOf(it.groupValues[1]) }
            .toList()

    /** Reads `<artifactId>-<version>[-classifier].<extension>` for one version directory. */
    private class Coordinates(private val artifactId: String, baseVersion: String) {
        private val version: Regex = if (baseVersion.endsWith("-SNAPSHOT")) {
            "^${Regex.escape(baseVersion.removeSuffix("-SNAPSHOT"))}-(?:$SNAPSHOT_TIMESTAMP)".toRegex()
        } else {
            "^${Regex.escape(baseVersion)}".toRegex()
        }

        // The part after the version: ".jar", "-sources.jar", ".module"; null when the name is not this module's.
        fun suffixOf(name: String): String? {
            val rest = name.removePrefix("$artifactId-")
            val match = version.find(rest)?.takeIf { name.startsWith("$artifactId-") }
            return match?.let { rest.substring(it.range.last + 1) }
        }
    }
}
