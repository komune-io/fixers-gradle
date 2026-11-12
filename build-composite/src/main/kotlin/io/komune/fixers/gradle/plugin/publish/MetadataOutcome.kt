package io.komune.fixers.gradle.plugin.publish

import java.io.File

/** What the metadata phase of a run did, for the failure report. */
internal data class MetadataOutcome(
    val total: Int,
    val isSent: Boolean,
    val uploaded: Int,
    val isSnapshot: Boolean,
)

internal fun failureReport(
    repoName: String,
    summary: MavenRepositoryUploader.UploadSummary,
    totalFiles: Int,
    metadata: MetadataOutcome,
    exhaustion: String?,
): String = buildString {
    append("$repoName upload failed for ${summary.failed + summary.notAttempted} file(s): ")
    append("${summary.uploaded} of $totalFiles file(s) uploaded, ${summary.skipped} already existed, ")
    append("${summary.failed} failed, ${summary.notAttempted} not attempted.")
    exhaustion?.let { append("\nStopped: $it.") }
    when {
        metadata.total == 0 -> Unit
        !metadata.isSent && metadata.isSnapshot -> append(
            "\nNo maven-metadata.xml was uploaded, so resolvers still get the snapshot this repository " +
                "served before this run: the files uploaded carry this run's timestamp in their names and " +
                "nothing references them yet. Rerun the publication."
        )
        !metadata.isSent -> append(
            "\nNo maven-metadata.xml was uploaded. For a fixed version the files uploaded can already " +
                "be fetched by path; only version discovery does not list the version yet. Rerun the " +
                "publication: the files already there answer 409 and are skipped."
        )
        metadata.uploaded < metadata.total -> append(
            "\nThe repository is partially published: ${metadata.uploaded} of ${metadata.total} " +
                "maven-metadata file(s) uploaded, so some modules resolve to this run and others " +
                "to the previous one. Rerun the publication."
        )
    }
    if (summary.errors.isNotEmpty()) {
        append("\n")
        append(summary.errors.joinToString("\n") { "  - $it" })
    }
}

// Deepest metadata first: a version's own maven-metadata.xml before the artifact's list
// of versions, so the list never names a version whose metadata did not land.
internal fun deepestFirst(metadata: List<File>, stagingDir: File): List<List<File>> =
    metadata.groupBy { it.relativeTo(stagingDir).path.count { c -> c == '/' } }
        .toSortedMap(compareByDescending { it })
        .values
        .toList()
