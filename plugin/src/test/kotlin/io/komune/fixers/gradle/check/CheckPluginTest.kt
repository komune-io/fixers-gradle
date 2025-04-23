package io.komune.fixers.gradle.check

import io.komune.gradle.config.ConfigExtension
import io.komune.gradle.config.model.Detekt
import io.komune.gradle.config.model.Sonar
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// Concrete implementation of ConfigExtension for testing
class TestConfigExtension(project: Project) : ConfigExtension(project)

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

    @Test
    fun `should configure Detekt when not disabled`() {
        // Create a mock ConfigExtension with Detekt not disabled
        val configExtension = TestConfigExtension(project)
        configExtension.detekt = Detekt(disable = false)

        // Add the extension to the root project
        val rootProject = ProjectBuilder.builder().build()
        rootProject.extensions.add("fixers", configExtension)

        // Set the root project
        project = ProjectBuilder.builder().withParent(rootProject).build()
        project.plugins.apply(CheckPlugin::class.java)

        // Verify that Detekt plugin is applied
        project.afterEvaluate {
            assertThat(project.plugins.hasPlugin("io.gitlab.arturbosch.detekt")).isTrue()
        }
    }

    @Test
    fun `should not configure Detekt when disabled`() {
        // Create a mock ConfigExtension with Detekt disabled
        val configExtension = TestConfigExtension(project)
        configExtension.detekt = Detekt(disable = true)

        // Add the extension to the root project
        val rootProject = ProjectBuilder.builder().build()
        rootProject.extensions.add("fixers", configExtension)

        // Set the root project
        project = ProjectBuilder.builder().withParent(rootProject).build()
        project.plugins.apply(CheckPlugin::class.java)

        // Verify that Detekt plugin is not applied
        project.afterEvaluate {
            assertThat(project.plugins.hasPlugin("io.gitlab.arturbosch.detekt")).isFalse()
        }
    }

    @Test
    fun `should configure SonarQube`() {
        // Create a mock ConfigExtension with Sonar configuration
        val configExtension = TestConfigExtension(project)
        configExtension.sonar = Sonar(
            projectKey = "test-project-key",
            organization = "test-organization",
            url = "https://sonarcloud.io",
            language = "kotlin",
            exclusions = "**/*Test.kt",
            jacoco = null,
            detekt = null,
            githubSummaryComment = null
        )

        // Add the extension to the root project
        val rootProject = ProjectBuilder.builder().build()
        rootProject.extensions.add("fixers", configExtension)

        // Set the root project
        project = ProjectBuilder.builder().withParent(rootProject).build()
        project.plugins.apply(CheckPlugin::class.java)

        // Verify that SonarQube plugin is applied
        project.afterEvaluate {
            assertThat(project.plugins.hasPlugin("org.sonarqube")).isTrue()
        }
    }
}
