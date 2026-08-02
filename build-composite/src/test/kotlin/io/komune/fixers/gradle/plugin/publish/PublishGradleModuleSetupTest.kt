package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for PublishGradleModuleSetup.
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
    fun `should configure pluginMaven publication with maven central metadata`() {
        val (project, config, publishing) = projectWithPublishing()
        val pluginMaven = publishing.publications.create("pluginMaven", MavenPublication::class.java)

        PublishGradleModuleSetup(project, config, publishing.publications).configurePluginPublications()

        assertThat(pluginMaven.pom.url.get()).isEqualTo("https://example.com/project")
    }

    @Test
    fun `should configure explicitly listed marker publications`() {
        val (project, config, publishing) = projectWithPublishing()
        config.publish.gradlePlugin.set(listOf("myMarker"))
        val marker = publishing.publications.create("myMarker", MavenPublication::class.java)

        PublishGradleModuleSetup(project, config, publishing.publications).configurePluginPublications()

        assertThat(marker.pom.url.get()).isEqualTo("https://example.com/project")
    }

    @Test
    fun `should configure all PluginMarkerMaven publications`() {
        val (project, config, publishing) = projectWithPublishing()
        val marker = publishing.publications.create("fooPluginMarkerMaven", MavenPublication::class.java)
        val regular = publishing.publications.create("regular", MavenPublication::class.java)

        PublishGradleModuleSetup(project, config, publishing.publications).configurePluginPublications()

        assertThat(marker.pom.url.get()).isEqualTo("https://example.com/project")
        assertThat(regular.pom.url.orNull).isNull()
    }

    @Test
    fun `configureMavenPublications should apply pom metadata to all publications`() {
        val (project, config, publishing) = projectWithPublishing()
        val publication = publishing.publications.create("anything", MavenPublication::class.java)

        publishing.publications.configureMavenPublications(project, config)

        assertThat(publication.pom.url.get()).isEqualTo("https://example.com/project")
    }
}
