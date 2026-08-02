package io.komune.fixers.gradle.config.utils

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for the property initialization helpers.
 */
class PropertyUtilsTest {

    private fun projectWithProperties(vararg pairs: Pair<String, Any>): Project {
        val project = ProjectBuilder.builder().build()
        pairs.forEach { (key, value) -> project.extensions.extraProperties.set(key, value) }
        return project
    }

    @Nested
    inner class StringPropertyTest {

        @Test
        fun `should use default value when nothing is configured`() {
            val project = projectWithProperties()

            val property = project.property<String>(
                projectKey = "fixers.test.value",
                defaultValue = "default"
            )

            assertThat(property.get()).isEqualTo("default")
        }

        @Test
        fun `should read value from project property`() {
            val project = projectWithProperties("fixers.test.value" to "from-project")

            val property = project.property<String>(
                projectKey = "fixers.test.value",
                defaultValue = "default"
            )

            assertThat(property.get()).isEqualTo("from-project")
        }

        @Test
        fun `should be absent when nothing is configured and no default given`() {
            val project = projectWithProperties()

            val property = project.property<String>(projectKey = "fixers.test.value")

            assertThat(property.isPresent).isFalse()
        }

        @Test
        fun `should allow dsl value to override convention`() {
            val project = projectWithProperties("fixers.test.value" to "from-project")

            val property = project.property<String>(projectKey = "fixers.test.value")
            property.set("from-dsl")

            assertThat(property.get()).isEqualTo("from-dsl")
        }
    }

    @Nested
    inner class TypedPropertyTest {

        @Test
        fun `should convert project property to Int`() {
            val project = projectWithProperties("fixers.test.int" to "42")

            val property = project.property<Int>(projectKey = "fixers.test.int")

            assertThat(property.get()).isEqualTo(42)
        }

        @Test
        fun `should convert project property to Boolean`() {
            val project = projectWithProperties("fixers.test.bool" to "true")

            val property = project.property<Boolean>(projectKey = "fixers.test.bool", defaultValue = false)

            assertThat(property.get()).isTrue()
        }

        @Test
        fun `should keep Int default when no property is set`() {
            val project = projectWithProperties()

            val property = project.property<Int>(projectKey = "fixers.test.int", defaultValue = 7)

            assertThat(property.get()).isEqualTo(7)
        }
    }

    @Nested
    inner class ListPropertyTest {

        @Test
        fun `should split comma separated project property`() {
            val project = projectWithProperties("fixers.test.list" to "a, b ,c")

            val property = project.initListProperty<String>(projectKey = "fixers.test.list")

            assertThat(property.get()).containsExactly("a", "b", "c")
        }

        @Test
        fun `should use default list when nothing is configured`() {
            val project = projectWithProperties()

            val property = project.initListProperty(
                projectKey = "fixers.test.list",
                defaultValue = listOf("x", "y")
            )

            assertThat(property.get()).containsExactly("x", "y")
        }

        @Test
        fun `should be empty when nothing is configured and no default given`() {
            val project = projectWithProperties()

            val property = project.initListProperty<String>(projectKey = "fixers.test.list")

            assertThat(property.get()).isEmpty()
        }
    }
}
