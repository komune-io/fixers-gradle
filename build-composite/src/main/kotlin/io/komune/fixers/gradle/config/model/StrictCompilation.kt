package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * What the strict Kotlin compilation of fixers lets a repository decide for itself. Every other
 * option of it is final.
 */
class StrictCompilation(
    project: Project
) {
    /**
     * Whether the test compilations fail on an ignored return value, as the main ones always do.
     * `false` compiles them with `-Xreturn-value-checker=disable`: a test that calls a function for
     * its effect then needs no `val _ =`. A repository-wide decision, read from the root project: a
     * `strictCompilation {}` of a subproject is ignored.
     */
    val returnValueCheckerInTests: Property<Boolean> = project.property(
        projectKey = "fixers.strictCompilation.returnValueCheckerInTests",
        defaultValue = true
    )
}
