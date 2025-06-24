package io.komune.fixers.gradle.config.model

import org.gradle.api.Project
import org.gradle.api.provider.Property
import io.komune.fixers.gradle.config.utils.property

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

    companion object {
        /**
         * The default JDK version.
         */
        const val VERSION_DEFAULT = 17
    }
}
