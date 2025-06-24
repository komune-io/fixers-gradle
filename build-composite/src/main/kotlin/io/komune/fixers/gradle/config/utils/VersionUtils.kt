package io.komune.fixers.gradle.config.utils

import org.gradle.api.Project

fun Project.versionFromFile(): String? {
    val versionFile = project.rootProject.file("VERSION")
    return if (versionFile.exists()) {
        versionFile.readText().trim()
    } else null
}
