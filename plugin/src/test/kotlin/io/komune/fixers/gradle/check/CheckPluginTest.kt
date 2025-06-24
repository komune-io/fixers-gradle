package io.komune.fixers.gradle.check

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.Detekt
import io.komune.fixers.gradle.config.model.Sonar
import io.komune.fixers.gradle.plugin.check.CheckPlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Concrete implementation of ConfigExtension for testing purposes.
 * 
 * This class is needed because ConfigExtension is an abstract class designed to be
 * subclassed by Gradle at runtime for property convention mapping and extension instantiation.
 * In tests, we need a concrete implementation that we can instantiate directly to configure
 * test-specific values without relying on Gradle's runtime extension mechanism.
 */
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
        // Create a mock ConfigExtension with Detekt enabled
        val configExtension = TestConfigExtension(project)
        configExtension.detekt = Detekt(project).apply { enabled.set(true) }

        // Add the extension to the root project
        val rootProject = ProjectBuilder.builder().build()
        rootProject.extensions.add("fixers", configExtension)

        // Set the root project
        project = ProjectBuilder.builder().withParent(rootProject).build()
        project.plugins.apply(CheckPlugin::class.java)

        // Register afterEvaluate action before evaluating the project
        var detektPluginApplied = false
        project.afterEvaluate {
            detektPluginApplied = project.plugins.hasPlugin("io.gitlab.arturbosch.detekt")
        }

        // Evaluate the project to trigger afterEvaluate actions
        (project as ProjectInternal).evaluate()

        // Verify that Detekt plugin is applied
        assertThat(detektPluginApplied).isTrue()
    }

    @Test
    fun `should not configure Detekt when disabled`() {
        // Create a mock ConfigExtension with Detekt disabled
        val configExtension = TestConfigExtension(project)
        configExtension.detekt = Detekt(project).apply { enabled.set(false) }

        // Add the extension to the root project
        val rootProject = ProjectBuilder.builder().build()
        rootProject.extensions.add("fixers", configExtension)

        // Set the root project
        project = ProjectBuilder.builder().withParent(rootProject).build()
        project.plugins.apply(CheckPlugin::class.java)

        // Register afterEvaluate action before evaluating the project
        var detektPluginApplied = false
        project.afterEvaluate {
            detektPluginApplied = project.plugins.hasPlugin("io.gitlab.arturbosch.detekt")
        }

        // Evaluate the project to trigger afterEvaluate actions
        (project as ProjectInternal).evaluate()

        // Verify that Detekt plugin is not applied
        assertThat(detektPluginApplied).isFalse()
    }

    @Test
    fun `should configure SonarQube`() {
        // Create a mock ConfigExtension with Sonar configuration
        val configExtension = TestConfigExtension(project)
        configExtension.sonar = Sonar(project).apply {
            projectKey.set("test-project-key")
            organization.set("test-organization")
            url.set("https://sonarcloud.io")
            language.set("kotlin")
            exclusions.set("**/*Test.kt")
        }

        // Add the extension to the root project
        val rootProject = ProjectBuilder.builder().build()
        rootProject.extensions.add("fixers", configExtension)

        // Set the root project
        project = ProjectBuilder.builder().withParent(rootProject).build()
        project.plugins.apply(CheckPlugin::class.java)

        // Register afterEvaluate action before evaluating the project
        var sonarQubePluginApplied = false
        project.afterEvaluate {
            sonarQubePluginApplied = project.plugins.hasPlugin("org.sonarqube")
        }

        // Evaluate the project to trigger afterEvaluate actions
        (project as ProjectInternal).evaluate()

        // Verify that SonarQube plugin is applied
        assertThat(sonarQubePluginApplied).isTrue()
    }
}
