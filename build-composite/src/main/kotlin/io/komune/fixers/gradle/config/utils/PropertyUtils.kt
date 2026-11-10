package io.komune.fixers.gradle.config.utils

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
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
 * @param T The type of the property value
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
    val property = objects.property<T>()
    val envValue = envKey?.let { providers.environmentVariable(it).orNull }
    val projValue = if (envValue == null) projectKey?.let { findProperty(it) } else null
    when {
        envValue != null -> property.extractEnv<T>(envValue)
        projValue != null -> property.extractProperties<T>(projValue)
        defaultValue != null -> property.convention(defaultValue)
    }
    return property
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
 * Helper function to merge a property from source to target if target is not present and source is present.
 *
 * @param T The type of the property value
 * @param sourceProp The source property to merge from
 */
fun <T : Any> Property<T>.mergeIfNotPresent(sourceProp: Property<T>) {
    if (!this.isPresent && sourceProp.isPresent) {
        this.set(sourceProp)
    }
}

/**
 * Helper function to merge a list property from source to target if target is not present and source is present.
 *
 * @param T The type of the list elements
 * @param sourceProp The source list property to merge from
 */
fun <T : Any> ListProperty<T>.mergeIfNotPresent(sourceProp: ListProperty<T>) {
    if (!this.isPresent && sourceProp.isPresent) {
        this.set(sourceProp)
    }
}

/**
 * Helper function to merge a map property from source to target if target is not present and source is present.
 *
 * @param K The type of the map keys
 * @param V The type of the map values
 * @param sourceProp The source map property to merge from
 */
fun <K : Any, V : Any> MapProperty<K, V>.mergeIfNotPresent(sourceProp: MapProperty<K, V>) {
    if (!this.isPresent && sourceProp.isPresent) {
        this.set(sourceProp)
    }
}

/** The value as a string, `null` when absent. */
internal fun Provider<*>.orNullText(): String = orNull?.toString() ?: "null"
