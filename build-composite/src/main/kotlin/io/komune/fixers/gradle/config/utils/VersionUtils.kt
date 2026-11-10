package io.komune.fixers.gradle.config.utils

import org.gradle.api.Project

fun Project.versionFromFile(): String? {
    val versionFile = project.rootProject.file("VERSION")
    return if (versionFile.exists()) versionFile.readText().trim() else null
}

/**
 * The version a CI hands over in the `VERSION` environment variable, else the `VERSION` file.
 */
fun Project.resolveVersion(
    ciVersion: String? = providers.environmentVariable("VERSION").orNull
): String? = ciVersion?.run { trim().takeIf { it.isNotEmpty() } } ?: versionFromFile()
