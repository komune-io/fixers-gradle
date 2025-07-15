package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.mapProperty

/**
 * Configuration for Kotlin to TypeScript generation.
 */
class Kt2Ts(
    private val project: Project
) {
    /**
     * The directory where TypeScript files will be generated.
     */
    val outputDirectory: Property<String> = project.property(
        envKey = "KT2TS_OUTPUT_DIRECTORY",
        projectKey = "kt2ts.outputDirectory",
        defaultValue = "platform/web/kotlin"
    )

    /**
     * The directory containing Kotlin JavaScript output to be converted.
     * If not specified, a default value based on the build directory will be used.
     */
    val inputDirectory: Property<String> = project.property(
        envKey = "KT2TS_INPUT_DIRECTORY",
        projectKey = "kt2ts.inputDirectory"
    )

    /**
     * Additional regex patterns for cleaning generated TypeScript files.
     * The map keys are file extensions, and the values are lists of regex patterns and their replacements.
     */
    val additionalCleaning: MapProperty<String, List<Pair<Regex, String>>> = 
        project.objects.mapProperty<String, List<Pair<Regex, String>>>().apply {
            convention(emptyMap())
        }

    /**
     * Merges properties from the source Kt2Ts into this Kt2Ts.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Kt2Ts to merge from
     * @return This Kt2Ts after merging
     */
    fun mergeFrom(source: Kt2Ts): Kt2Ts {
        outputDirectory.mergeIfNotPresent(source.outputDirectory)
        inputDirectory.mergeIfNotPresent(source.inputDirectory)
        additionalCleaning.mergeIfNotPresent(source.additionalCleaning)

        return this
    }
}
