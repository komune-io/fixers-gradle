package io.komune.fixers.gradle.config.utils

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
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
 * Uses providers.environmentVariable() for configuration cache compatibility.
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
            providers.environmentVariable(envKey).orNull?.let { envValue ->
                extractEnv<T>(envValue)
                return@apply
            }
        }

        if (projectKey != null) {
            findProperty(projectKey)?.let { projValue ->
                extractProperties<T>(projValue)
                return@apply
            }
        }

        if (defaultValue != null) {
            convention(defaultValue)
        }
    }
}

inline fun <reified T> Property<T>.extractProperties(projValue: Any) where T : Any {
    val value: T = when (T::class) {
        Int::class -> projValue.toString().toInt() as T
        Boolean::class -> projValue.toString().toBoolean() as T
        else -> projValue as T
    }
    convention(value)
}

inline fun <reified T> Property<T>.extractEnv(envValue: String) where T : Any {
    val value: T = when (T::class) {
        Int::class -> envValue.toInt() as T
        Boolean::class -> envValue.toBoolean() as T
        else -> envValue as T
    }
    convention(value)
    return
}

/**
 * Initializes a list property from environment variables, project properties, and a default value.
 * Priority: 1. kts file dsl (handled by convention() in the calling code)
 *          2. ENV system properties (comma-separated values)
 *          3. Project properties (comma-separated values)
 *          4. Default value
 *
 * Uses providers.environmentVariable() for configuration cache compatibility.
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
            providers.environmentVariable(envKey).orNull?.let { envValue ->
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

/**
 * Helper function to merge a property from source to target if target is not present and source is present
 * 
 * @param sourceProp The source property to merge from
 * @return The target property (this) after merging
 */
fun <T : Any> Property<T>.mergeIfNotPresent(sourceProp: Property<T>): Property<T> {
    if (!this.isPresent && sourceProp.isPresent) {
        this.set(sourceProp)
    }
    return this
}

/**
 * Helper function to merge a list property from source to target if target is not present and source is present
 * 
 * @param sourceProp The source list property to merge from
 * @return The target list property (this) after merging
 */
fun <T : Any> ListProperty<T>.mergeIfNotPresent(sourceProp: ListProperty<T>): ListProperty<T> {
    if (!this.isPresent && sourceProp.isPresent) {
        this.set(sourceProp)
    }
    return this
}

/**
 * Helper function to merge a map property from source to target if target is not present and source is present
 * 
 * @param sourceProp The source map property to merge from
 * @return The target map property (this) after merging
 */
fun <K : Any, V : Any> MapProperty<K, V>.mergeIfNotPresent(sourceProp: MapProperty<K, V>): MapProperty<K, V> {
    if (!this.isPresent && sourceProp.isPresent) {
        this.set(sourceProp)
    }
    return this
}
