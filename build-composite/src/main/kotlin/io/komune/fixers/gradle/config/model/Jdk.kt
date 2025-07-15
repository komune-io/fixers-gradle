package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration for JDK version.
 */
class Jdk(
    private val project: Project
) {
    /**
     * The JDK version to use.
     */
    val version: Property<Int> = project.property(
        envKey = "JDK_VERSION",
        projectKey = "jdk.version",
        defaultValue = VERSION_DEFAULT
    )

    /**
     * Merges properties from the source Jdk into this Jdk.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source Jdk to merge from
     * @return This Jdk after merging
     */
    fun mergeFrom(source: Jdk): Jdk {
        version.mergeIfNotPresent(source.version)

        return this
    }

    companion object {
        /**
         * The default JDK version.
         */
        const val VERSION_DEFAULT = 17
    }
}
