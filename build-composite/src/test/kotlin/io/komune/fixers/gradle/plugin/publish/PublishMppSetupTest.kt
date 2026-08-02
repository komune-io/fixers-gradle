package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.plugin.kotlin.MppPlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for PublishMppSetup.
 */
class PublishMppSetupTest {

    private fun mppProjectWithConfig(): Pair<Project, ConfigExtension> {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        val project = ProjectBuilder.builder().withParent(root).withName("mpp-lib").build()
        project.plugins.apply(MppPlugin::class.java)
        project.plugins.apply("maven-publish")
        return project to config
    }

    @Test
    fun `should register javadocJar task for mpp projects`() {
        val (project, config) = mppProjectWithConfig()

        PublishMppSetup.setupMppPublish(project, config)

        assertThat(project.tasks.findByName("javadocJar")).isNotNull
    }

    @Test
    fun `should suffix artifactId with publication name for target publications`() {
        val (project, config) = mppProjectWithConfig()
        PublishMppSetup.setupMppPublish(project, config)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        val publication = publishing.publications.getByName("jvm") as MavenPublication

        assertThat(publication.artifactId).isEqualTo("mpp-lib-jvm")
    }

    @Test
    fun `should keep plain project name for kotlinMultiplatform publication`() {
        val (project, config) = mppProjectWithConfig()
        PublishMppSetup.setupMppPublish(project, config)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        val publication = publishing.publications.getByName("kotlinMultiplatform") as MavenPublication

        assertThat(publication.artifactId).isEqualTo("mpp-lib")
    }

    @Test
    fun `should attach javadoc artifact to publications`() {
        val (project, config) = mppProjectWithConfig()
        PublishMppSetup.setupMppPublish(project, config)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        val publication = publishing.publications.getByName("jvm") as MavenPublication

        assertThat(publication.artifacts.map { it.classifier }).contains("javadoc")
    }

    @Test
    fun `should do nothing when MppPlugin is not applied`() {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        val project = ProjectBuilder.builder().withParent(root).build()
        project.plugins.apply("maven-publish")

        PublishMppSetup.setupMppPublish(project, config)

        assertThat(project.tasks.findByName("javadocJar")).isNull()
    }
}
