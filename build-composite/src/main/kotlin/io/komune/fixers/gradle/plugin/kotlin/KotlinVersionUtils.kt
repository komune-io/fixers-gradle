package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Jdk
import org.gradle.api.Project

/**
 * Resolves the JDK version from the root `fixers { jdk { version } }` configuration,
 * falling back to [Jdk.VERSION_DEFAULT] when unset.
 */
internal fun Project.fixersJdkVersion(): Int =
    rootProject.extensions.fixers
        ?.run { jdk.version.orNull }
        ?: Jdk.VERSION_DEFAULT
