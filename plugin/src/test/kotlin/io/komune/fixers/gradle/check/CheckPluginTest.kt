package io.komune.fixers.gradle.check

import io.komune.fixers.gradle.plugin.check.CheckPlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for CheckPlugin.
 *
 * Note: Tests for Detekt and SonarQube configuration are handled by integration tests
 * (CheckPluginIntegrationTest) because the CheckPlugin uses gradle.projectsEvaluated
 * which is not properly triggered by ProjectBuilder in unit tests.
 */
class CheckPluginTest {

    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        project.plugins.apply(CheckPlugin::class.java)
    }

    @Test
    fun `should apply CheckPlugin`() {
        // Verify that the plugin is applied
        assertThat(project.plugins.hasPlugin(CheckPlugin::class.java)).isTrue()
    }
}
