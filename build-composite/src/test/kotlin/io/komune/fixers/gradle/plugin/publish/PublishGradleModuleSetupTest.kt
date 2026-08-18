package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for [configureMavenPublications].
 */
class PublishGradleModuleSetupTest {

    private fun projectWithPublishing(): Triple<Project, ConfigExtension, PublishingExtension> {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        config.bundle.url.set("https://example.com/project")
        val project = ProjectBuilder.builder().withParent(root).withName("plugin-lib").build()
        project.plugins.apply("maven-publish")
        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        return Triple(project, config, publishing)
    }

    @Test
    fun `should configure pluginMaven publication`() {
        val (project, config, publishing) = projectWithPublishing()
        val pluginMaven = publishing.publications.create("pluginMaven", MavenPublication::class.java)

        publishing.publications.configureMavenPublications(project, config)

        assertThat(pluginMaven.pom.url.get()).isEqualTo("https://example.com/project")
    }

    @Test
    fun `should configure PluginMarkerMaven publications`() {
        val (project, config, publishing) = projectWithPublishing()
        val marker = publishing.publications.create("fooPluginMarkerMaven", MavenPublication::class.java)

        publishing.publications.configureMavenPublications(project, config)

        assertThat(marker.pom.url.get()).isEqualTo("https://example.com/project")
    }

    @Test
    fun `should apply pom metadata to all publications including later-created ones`() {
        val (project, config, publishing) = projectWithPublishing()
        val existing = publishing.publications.create("existing", MavenPublication::class.java)

        publishing.publications.configureMavenPublications(project, config)

        val late = publishing.publications.create("late", MavenPublication::class.java)
        assertThat(existing.pom.url.get()).isEqualTo("https://example.com/project")
        assertThat(late.pom.url.get()).isEqualTo("https://example.com/project")
    }
}
