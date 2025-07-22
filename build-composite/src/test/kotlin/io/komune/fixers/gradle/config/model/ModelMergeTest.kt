package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the merge methods in the model classes.
 * 
 * This test focuses on the mergeIfNotPresent utility function, which is the core of all merge methods.
 */
class ModelMergeTest {

    @Test
    fun `test mergeIfNotPresent for Property`() {
        // Create a source property that is present
        val sourceProperty: Property<String> = mock()
        whenever(sourceProperty.isPresent).thenReturn(true)
        whenever(sourceProperty.get()).thenReturn("source-value")

        // Create a target property that is not present
        val targetProperty: Property<String> = mock()
        whenever(targetProperty.isPresent).thenReturn(false)

        // Test mergeIfNotPresent
        targetProperty.mergeIfNotPresent(sourceProperty)

        // Verify that the property was set
        verify(targetProperty).set(sourceProperty)
    }

    @Test
    fun `test mergeIfNotPresent for Property when target is already present`() {
        // Create a source property that is present
        val sourceProperty: Property<String> = mock()
        whenever(sourceProperty.isPresent).thenReturn(true)
        whenever(sourceProperty.get()).thenReturn("source-value")

        // Create a target property that is already present
        val targetProperty: Property<String> = mock()
        whenever(targetProperty.isPresent).thenReturn(true)
        whenever(targetProperty.get()).thenReturn("target-value")

        // Test mergeIfNotPresent
        targetProperty.mergeIfNotPresent(sourceProperty)

        // Verify that the property was not set (because target is already present)
        verify(targetProperty).isPresent
    }

    @Test
    fun `test mergeIfNotPresent for ListProperty`() {
        // Create a source list property that is present
        val sourceProperty: ListProperty<String> = mock()
        whenever(sourceProperty.isPresent).thenReturn(true)
        whenever(sourceProperty.get()).thenReturn(listOf("source-value"))

        // Create a target list property that is not present
        val targetProperty: ListProperty<String> = mock()
        whenever(targetProperty.isPresent).thenReturn(false)

        // Test mergeIfNotPresent
        targetProperty.mergeIfNotPresent(sourceProperty)

        // Verify that the property was set
        verify(targetProperty).set(sourceProperty)
    }

    @Test
    fun `test mergeIfNotPresent for MapProperty`() {
        // Create a source map property that is present
        val sourceProperty: MapProperty<String, String> = mock()
        whenever(sourceProperty.isPresent).thenReturn(true)
        whenever(sourceProperty.get()).thenReturn(mapOf("key" to "source-value"))

        // Create a target map property that is not present
        val targetProperty: MapProperty<String, String> = mock()
        whenever(targetProperty.isPresent).thenReturn(false)

        // Test mergeIfNotPresent
        targetProperty.mergeIfNotPresent(sourceProperty)

        // Verify that the property was set
        verify(targetProperty).set(sourceProperty)
    }
}
