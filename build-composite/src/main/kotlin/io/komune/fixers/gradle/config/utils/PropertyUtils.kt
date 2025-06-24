package io.komune.fixers.gradle.config.utils

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property

/**
 * Extension functions for initializing Gradle properties with values from environment variables,
 * project properties, and default values.
 */

/**
 * Initializes a property from environment variables, project properties, and a default value.
 * Priority: 1. kts file dsl (handled by convention() in the calling code)
 *          2. ENV system properties
 *          3. Project properties
 *          4. Default value
 *
 * @param envKey The environment variable key to check for a value
 * @param projectKey The project property key to check for a value
 * @param defaultValue The default value to use if no other value is found
 * @return A Property<T> initialized with the appropriate value
 */
inline fun <reified T> Project.property(
    envKey: String? = null,
    projectKey: String? = null,
    defaultValue: T? = null
): Property<T> where T : Any {
    return objects.property<T>().apply {
        if (envKey != null) {
            System.getenv(envKey)?.let { envValue ->
                convention(envValue as T)
                return@apply
            }
        }

        if (projectKey != null) {
            findProperty(projectKey)?.let { projValue ->
                convention(projValue as T)
                return@apply
            }
        }

        if (defaultValue != null) {
            convention(defaultValue)
        }
    }
}

/**
 * Initializes a list property from environment variables, project properties, and a default value.
 * Priority: 1. kts file dsl (handled by convention() in the calling code)
 *          2. ENV system properties (comma-separated values)
 *          3. Project properties (comma-separated values)
 *          4. Default value
 *
 * @param envKey The environment variable key to check for a value
 * @param projectKey The project property key to check for a value
 * @param defaultValue The default value to use if no other value is found
 * @return A ListProperty<T> initialized with the appropriate values
 */
inline fun <reified T> Project.initListProperty(
    envKey: String? = null,
    projectKey: String? = null,
    defaultValue: List<T>? = null
): ListProperty<T> where T : Any {
    return objects.listProperty<T>().apply {
        if (envKey != null) {
            System.getenv(envKey)?.let { envValue ->
                val list = envValue.split(",").map { it.trim() as T }
                convention(list)
                return@apply
            }
        }

        if (projectKey != null) {
            findProperty(projectKey)?.toString()?.let { projValue ->
                val list = projValue.split(",").map { it.trim() as T }
                convention(list)
                return@apply
            }
        }

        if (defaultValue != null) {
            convention(defaultValue)
        }
    }
}
