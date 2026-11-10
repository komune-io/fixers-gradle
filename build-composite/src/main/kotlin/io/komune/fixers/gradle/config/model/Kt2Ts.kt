package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property

/** What `tsGen` writes to the output directory, which the project commits. */
enum class Kt2TsFiles {
    /** Every file of the Kotlin/JS packages: for a front that imports the copy, `.mjs` included. */
    ALL,

    /**
     * The declarations (`.d.mts`, and the legacy `.d.ts`) and the `package.json` of each package
     * only: the TypeScript contract a library commits to review it.
     */
    DECLARATIONS;

    companion object {
        /** The version of every `package.json` `tsGen` writes, so that a release rewrites none of them. */
        const val CONTRACT_VERSION = "0.0.0"
    }
}

/**
 * Configuration for Kotlin to TypeScript generation.
 */
class Kt2Ts(
    project: Project
) {
    /**
     * The directory where TypeScript files will be generated.
     */
    val outputDirectory: Property<String> = project.property(
        envKey = "FIXERS_KT2TS_OUTPUT_DIRECTORY",
        projectKey = "fixers.kt2ts.outputDirectory",
        defaultValue = "platform/web/kotlin"
    )

    /**
     * The directory containing Kotlin JavaScript output to be converted.
     * If not specified, a default value based on the build directory will be used.
     */
    val inputDirectory: Property<String> = project.property(
        envKey = "FIXERS_KT2TS_INPUT_DIRECTORY",
        projectKey = "fixers.kt2ts.inputDirectory"
    )

    /** What the output directory holds. */
    val committedFiles: Property<Kt2TsFiles> =
        project.objects.property<Kt2TsFiles>().convention(Kt2TsFiles.DECLARATIONS)

    /**
     * Additional regex patterns for cleaning generated TypeScript files.
     * The map keys are file extensions, and the values are lists of regex patterns and their replacements.
     */
    val additionalCleaning: MapProperty<String, List<Pair<Regex, String>>> = 
        project.objects.mapProperty<String, List<Pair<Regex, String>>>().apply {
            convention(emptyMap())
        }
}
