package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.plugin.kotlin.JvmPlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for PublishJvmSetup.
 */
class PublishJvmSetupTest {

    private fun jvmProjectWithConfig(): Pair<Project, ConfigExtension> {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        val project = ProjectBuilder.builder().withParent(root).withName("jvm-lib").build()
        project.plugins.apply(JvmPlugin::class.java)
        project.plugins.apply("maven-publish")
        return project to config
    }

    @Nested
    inner class PluginMarkerArtifactIdTest {

        @Test
        fun `should return project name for regular publications`() {
            assertThat(PublishJvmSetup.pluginMarkerArtifactId("my-project", "maven")).isEqualTo("my-project")
        }

        @Test
        fun `should map plugin marker publications to gradle plugin artifact id`() {
            assertThat(PublishJvmSetup.pluginMarkerArtifactId("my-project", "fooPluginMarkerMaven"))
                .isEqualTo("foo.gradle.plugin")
        }
    }

    @Nested
    inner class SetupJvmPublishTest {

        @Test
        fun `should register javadocJar and sourcesJar tasks`() {
            val (project, config) = jvmProjectWithConfig()

            PublishJvmSetup.setupJVMPublish(project, config)

            assertThat(project.tasks.findByName("javadocJar")).isNotNull
            assertThat(project.tasks.findByName("sourcesJar")).isNotNull
        }

        @Test
        fun `should create maven publication from kotlin component`() {
            val (project, config) = jvmProjectWithConfig()

            PublishJvmSetup.setupJVMPublish(project, config)

            val publishing = project.extensions.getByType(PublishingExtension::class.java)
            val publication = publishing.publications.findByName("maven") as MavenPublication?
            assertThat(publication).isNotNull
            assertThat(publication!!.artifactId).isEqualTo("jvm-lib")
        }

        @Test
        fun `should do nothing when JvmPlugin is not applied`() {
            val root = ProjectBuilder.builder().build()
            val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
            val project = ProjectBuilder.builder().withParent(root).build()
            project.plugins.apply("maven-publish")

            PublishJvmSetup.setupJVMPublish(project, config)

            assertThat(project.tasks.findByName("javadocJar")).isNull()
            val publishing = project.extensions.getByType(PublishingExtension::class.java)
            assertThat(publishing.publications.findByName("maven")).isNull()
        }

        @Test
        fun `should attach javadoc and sources artifacts to existing publications`() {
            val (project, config) = jvmProjectWithConfig()

            PublishJvmSetup.setupJVMPublish(project, config)

            val publishing = project.extensions.getByType(PublishingExtension::class.java)
            val publication = publishing.publications.getByName("maven") as MavenPublication
            val classifiers = publication.artifacts.map { it.classifier }
            assertThat(classifiers).contains("javadoc", "sources")
        }
    }
}
