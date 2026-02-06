package io.komune.fixers.gradle.check

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A simple test class to demonstrate the testing process.
 * This test doesn't test any real functionality, it's just for demonstration purposes.
 */
class SimpleTest {

    /**
     * A simple test that always passes.
     * This is just to demonstrate how to write and run a test.
     */
    @Test
    fun `simple test that always passes`() {
        // Given
        val input = "Hello"
        
        // When
        val result = input + " World"
        
        // Then
        assertThat(result).isEqualTo("Hello World")
    }

    /**
     * A test that demonstrates string manipulation.
     */
    @Test
    fun `test string manipulation`() {
        // Given
        val input = "fixers-gradle"
        
        // When
        val parts = input.split("-")
        
        // Then
        assertThat(parts).hasSize(2)
        assertThat(parts[0]).isEqualTo("fixers")
        assertThat(parts[1]).isEqualTo("gradle")
    }
}
