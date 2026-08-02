package io.komune.fixers.gradle

import io.komune.fixers.gradle.plugin.check.CheckPlugin
import io.komune.fixers.gradle.plugin.config.ConfigPlugin
import io.komune.fixers.gradle.plugin.publish.PublishPlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for the composite precompiled script plugins.
 */
class CompositePluginsTest {

    @Test
    fun `composite config should apply check and config plugins`() {
        val project = ProjectBuilder.builder().build()

        project.plugins.apply("composite.config")

        assertThat(project.plugins.hasPlugin(CheckPlugin::class.java)).isTrue()
        assertThat(project.plugins.hasPlugin(ConfigPlugin::class.java)).isTrue()
    }

    @Test
    fun `composite publishing should apply publish plugin`() {
        val root = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().withParent(root).build()

        child.plugins.apply("composite.publishing")

        assertThat(child.plugins.hasPlugin(PublishPlugin::class.java)).isTrue()
        assertThat(child.plugins.hasPlugin("maven-publish")).isTrue()
    }
}
