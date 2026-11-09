package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property

/** What `tsGen` writes to the output directory, which the project commits. */
enum class Kt2TsFiles {
    /** Every file of the Kotlin/JS packages: a consumer that imports the copy needs the `.mjs` too. */
    ALL,

    /**
     * The declarations (`.d.mts`, and the legacy `.d.ts`) and the `package.json` of each package
     * only, the TypeScript contract a library reviews; each `package.json` carries version
     * [CONTRACT_VERSION], so a release does not rewrite them.
     */
    DECLARATIONS;

    companion object {
        /** The `package.json` version of a [DECLARATIONS] contract, which does not follow the build's. */
        const val CONTRACT_VERSION = "0.0.0"
    }
}

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

    /**
     * What the output directory holds: [Kt2TsFiles.ALL] when neither this project nor the root
     * sets it. No convention, so that a subproject takes the root's value through [mergeFrom].
     */
    val committedFiles: Property<Kt2TsFiles> = project.objects.property<Kt2TsFiles>()

    /** [committedFiles], or [Kt2TsFiles.ALL] when it is not set. */
    val committedFilesOrDefault get() = committedFiles.orElse(Kt2TsFiles.ALL)

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
        committedFiles.mergeIfNotPresent(source.committedFiles)

        return this
    }
}
