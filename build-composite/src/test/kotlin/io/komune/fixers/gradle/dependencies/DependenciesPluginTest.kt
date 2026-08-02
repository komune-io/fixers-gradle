package io.komune.fixers.gradle.dependencies

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for the (no-op) DependenciesPlugin variants and catalog extensions.
 */
class DependenciesPluginTest {

    @Test
    fun `should apply dependencies plugin without side effects`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply(DependenciesPlugin::class.java)

        assertThat(project.plugins.hasPlugin(DependenciesPlugin::class.java)).isTrue()
    }

    @Test
    fun `should apply plugin variant from plugin package`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply(io.komune.fixers.gradle.plugin.dependencies.DependenciesPlugin::class.java)

        assertThat(
            project.plugins.hasPlugin(io.komune.fixers.gradle.plugin.dependencies.DependenciesPlugin::class.java)
        ).isTrue()
    }

    @Test
    fun `hasFixersCatalog should be false without version catalogs`() {
        val project = ProjectBuilder.builder().build()

        assertThat(project.hasFixersCatalog()).isFalse()
    }

    @Test
    fun `findFixersCatalog should return null without version catalogs`() {
        val project = ProjectBuilder.builder().build()

        assertThat(project.findFixersCatalog()).isNull()
    }
}
