package io.komune.fixers.gradle.config.utils

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * Configures all Test tasks in the project to use JUnit Platform.
 * This is idempotent - calling it multiple times has no additional effect.
 */
fun Project.configureJUnitPlatform() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
