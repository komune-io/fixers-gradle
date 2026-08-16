package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Jdk
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * Resolves the JDK version from the root `fixers { jdk { version } }` configuration,
 * falling back to [Jdk.VERSION_DEFAULT] when unset.
 */
internal fun Project.fixersJdkVersion(): Int =
	rootProject.extensions.fixers?.jdk?.version?.orNull ?: Jdk.VERSION_DEFAULT

/**
 * Derives the Kotlin language version from the applied Kotlin plugin version
 * (e.g. `2.2.10` -> `2.2`).
 */
internal fun Project.kotlinLanguageVersion(): KotlinVersion =
	KotlinVersion.fromVersion(getKotlinPluginVersion().substringBeforeLast("."))
