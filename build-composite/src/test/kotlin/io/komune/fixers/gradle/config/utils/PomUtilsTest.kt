package io.komune.fixers.gradle.config.utils

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.Bundle
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for [pom] POM configuration action.
 */
class PomUtilsTest {

    private fun projectWithPublishing(root: Project? = null): Pair<Project, MavenPublication> {
        val project = if (root == null) {
            ProjectBuilder.builder().build()
        } else {
            ProjectBuilder.builder().withParent(root).build()
        }
        project.plugins.apply("maven-publish")
        val publication = project.extensions.getByType(PublishingExtension::class.java)
            .publications.create("test", MavenPublication::class.java)
        return project to publication
    }

    @Test
    fun `should populate pom from bundle values`() {
        val (project, publication) = projectWithPublishing()
        val bundle = Bundle(project, "my-bundle").apply {
            description.set("My description")
            url.set("https://example.com/project")
        }

        publication.pom(project.pom(bundle))

        assertThat(publication.pom.name.get()).isEqualTo("my-bundle")
        assertThat(publication.pom.description.get()).isEqualTo("My description")
        assertThat(publication.pom.url.get()).isEqualTo("https://example.com/project")
    }

    @Test
    fun `should leave optional values absent when not configured anywhere`() {
        val (project, publication) = projectWithPublishing()
        val bundle = Bundle(project, "my-bundle")

        publication.pom(project.pom(bundle))

        assertThat(publication.pom.description.orNull).isNull()
        assertThat(publication.pom.url.orNull).isNull()
    }

    @Test
    fun `should fall back to root project bundle for missing values`() {
        val root = ProjectBuilder.builder().build()
        val rootConfig = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        rootConfig.bundle.description.set("Root description")
        rootConfig.bundle.url.set("https://root.example.com")

        val (project, publication) = projectWithPublishing(root)
        val bundle = Bundle(project, "sub-bundle")

        publication.pom(project.pom(bundle))

        assertThat(publication.pom.name.get()).isEqualTo("sub-bundle")
        assertThat(publication.pom.description.get()).isEqualTo("Root description")
        assertThat(publication.pom.url.get()).isEqualTo("https://root.example.com")
    }

    @Test
    fun `should prefer subproject bundle values over root fallback`() {
        val root = ProjectBuilder.builder().build()
        val rootConfig = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        rootConfig.bundle.description.set("Root description")

        val (project, publication) = projectWithPublishing(root)
        val bundle = Bundle(project, "sub-bundle").apply {
            description.set("Sub description")
        }

        publication.pom(project.pom(bundle))

        assertThat(publication.pom.description.get()).isEqualTo("Sub description")
    }

    @Test
    fun `should execute license developer and scm defaults without error`() {
        val (project, publication) = projectWithPublishing()
        val bundle = Bundle(project, "my-bundle").apply {
            url.set("https://example.com/project")
        }

        // Defaults exist for license, developer and scm fields; the action must apply them.
        publication.pom(project.pom(bundle))

        // MavenPom does not expose getters for nested sections; assert via generated XML actions
        // being registered without failure and top-level values applied.
        assertThat(publication.pom.url.get()).isEqualTo("https://example.com/project")
        assertThat(bundle.licenseName.get()).isEqualTo("The Apache Software License, Version 2.0")
        assertThat(bundle.developerId.get()).isEqualTo("Komune")
        assertThat(bundle.scmConnection.get()).contains("scm:git")
    }
}
