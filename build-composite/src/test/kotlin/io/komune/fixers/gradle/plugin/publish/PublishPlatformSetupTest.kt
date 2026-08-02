package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for PublishPlatformSetup and PublishCatalogSetup.
 */
class PublishPlatformSetupTest {

    private fun projectWithConfig(): Pair<Project, ConfigExtension> {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        val project = ProjectBuilder.builder().withParent(root).withName("platform-lib").build()
        return project to config
    }

    @Test
    fun `should create maven publication for java-platform projects`() {
        val (project, config) = projectWithConfig()
        project.plugins.apply("java-platform")
        project.plugins.apply("maven-publish")

        PublishPlatformSetup.setupPlatformPublish(project, config)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.findByName("maven")).isInstanceOf(MavenPublication::class.java)
    }

    @Test
    fun `should not create publication when java-platform plugin is missing`() {
        val (project, config) = projectWithConfig()
        project.plugins.apply("maven-publish")

        PublishPlatformSetup.setupPlatformPublish(project, config)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.findByName("maven")).isNull()
    }

    @Test
    fun `should not duplicate platform publication when called twice`() {
        val (project, config) = projectWithConfig()
        project.plugins.apply("java-platform")
        project.plugins.apply("maven-publish")

        PublishPlatformSetup.setupPlatformPublish(project, config)
        PublishPlatformSetup.setupPlatformPublish(project, config)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.filter { it.name == "maven" }).hasSize(1)
    }

    @Test
    fun `should create maven publication for version-catalog projects`() {
        val (project, config) = projectWithConfig()
        project.plugins.apply("version-catalog")
        project.plugins.apply("maven-publish")

        PublishCatalogSetup.setupCatalogPublish(project, config)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.findByName("maven")).isInstanceOf(MavenPublication::class.java)
    }

    @Test
    fun `should not create publication when version-catalog plugin is missing`() {
        val (project, config) = projectWithConfig()
        project.plugins.apply("maven-publish")

        PublishCatalogSetup.setupCatalogPublish(project, config)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.findByName("maven")).isNull()
    }
}
