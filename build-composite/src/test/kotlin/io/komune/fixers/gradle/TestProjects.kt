package io.komune.fixers.gradle

import io.komune.fixers.gradle.plugin.config.SETTINGS_PLUGIN_MARKER
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

/** A root project as a build that applies the fixers settings plugin hands it to the project plugins. */
fun ProjectBuilder.buildFixersRoot(): Project =
    build().also { it.gradle.extensions.extraProperties.set(SETTINGS_PLUGIN_MARKER, true) }
