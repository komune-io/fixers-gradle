package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.buildFixersRoot
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
        val root = ProjectBuilder.builder().buildFixersRoot()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        val project = ProjectBuilder.builder().withParent(root).withName("platform-lib").build()
        return project to config
    }

    @Test
    fun `should create maven publication for java-platform projects`() {
        val (project, _) = projectWithConfig()
        project.plugins.apply("java-platform")
        project.plugins.apply("maven-publish")

        PublishPlatformSetup.setupPlatformPublish(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.findByName("maven")).isInstanceOf(MavenPublication::class.java)
    }

    @Test
    fun `should publish the platform's constraints, one license, one developer and the project's scm`() {
        val root = ProjectBuilder.builder().buildFixersRoot()
        root.plugins.apply(io.komune.fixers.gradle.plugin.config.ConfigPlugin::class.java)
        val project = ProjectBuilder.builder().withParent(root).withName("platform-lib").build()
        project.plugins.apply("java-platform")
        project.plugins.apply(PublishPlugin::class.java)
        project.dependencies.constraints.add("api", "org.example:probe:1.0.0")
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
        (root.gradle as org.gradle.api.internal.GradleInternal).let { it.buildListenerBroadcaster.projectsEvaluated(it) }

        val generate = project.tasks.getByName("generatePomFileForMavenPublication")
        generate.actions.forEach { it.execute(generate) }

        val pom = project.layout.buildDirectory.file("publications/maven/pom-default.xml").get().asFile.readText()
        assertThat(pom).contains("<dependencyManagement>", "<artifactId>probe</artifactId>")
        assertThat(Regex("<license>").findAll(pom).count()).isEqualTo(1)
        assertThat(Regex("<developer>").findAll(pom).count()).isEqualTo(1)
        assertThat(pom).contains("<connection>scm:git:git://github.com/komune-io/${root.name}.git</connection>")
    }

    @Test
    fun `should write one license and one developer into the POM of a JVM module`() {
        val root = ProjectBuilder.builder().buildFixersRoot()
        root.plugins.apply(io.komune.fixers.gradle.plugin.config.ConfigPlugin::class.java)
        val project = ProjectBuilder.builder().withParent(root).withName("jvm-lib").build()
        project.plugins.apply(io.komune.fixers.gradle.plugin.kotlin.JvmPlugin::class.java)
        project.plugins.apply(PublishPlugin::class.java)
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
        (root.gradle as org.gradle.api.internal.GradleInternal).let { it.buildListenerBroadcaster.projectsEvaluated(it) }

        val generate = project.tasks.getByName("generatePomFileForMavenPublication")
        generate.actions.forEach { it.execute(generate) }

        val pom = project.layout.buildDirectory.file("publications/maven/pom-default.xml").get().asFile.readText()
        assertThat(Regex("<license>").findAll(pom).count()).isEqualTo(1)
        assertThat(Regex("<developer>").findAll(pom).count()).isEqualTo(1)
    }

    @Test
    fun `should write one license and one developer into the POMs of a multiplatform module`() {
        val root = ProjectBuilder.builder().buildFixersRoot()
        root.plugins.apply(io.komune.fixers.gradle.plugin.config.ConfigPlugin::class.java)
        val project = ProjectBuilder.builder().withParent(root).withName("mpp-lib").build()
        project.plugins.apply(io.komune.fixers.gradle.plugin.kotlin.MppPlugin::class.java)
        project.plugins.apply(PublishPlugin::class.java)
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
        (root.gradle as org.gradle.api.internal.GradleInternal).let { it.buildListenerBroadcaster.projectsEvaluated(it) }

        val generate = project.tasks.getByName("generatePomFileForKotlinMultiplatformPublication")
        generate.actions.forEach { it.execute(generate) }

        val pom = project.layout.buildDirectory.file("publications/kotlinMultiplatform/pom-default.xml").get().asFile.readText()
        assertThat(Regex("<license>").findAll(pom).count()).isEqualTo(1)
        assertThat(Regex("<developer>").findAll(pom).count()).isEqualTo(1)
    }

    @Test
    fun `should not create publication when java-platform plugin is missing`() {
        val (project, _) = projectWithConfig()
        project.plugins.apply("maven-publish")

        PublishPlatformSetup.setupPlatformPublish(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.findByName("maven")).isNull()
    }

    @Test
    fun `should not duplicate platform publication when called twice`() {
        val (project, _) = projectWithConfig()
        project.plugins.apply("java-platform")
        project.plugins.apply("maven-publish")

        PublishPlatformSetup.setupPlatformPublish(project)
        PublishPlatformSetup.setupPlatformPublish(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.filter { it.name == "maven" }).hasSize(1)
    }

    @Test
    fun `should create maven publication for version-catalog projects`() {
        val (project, _) = projectWithConfig()
        project.plugins.apply("version-catalog")
        project.plugins.apply("maven-publish")

        PublishCatalogSetup.setupCatalogPublish(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.findByName("maven")).isInstanceOf(MavenPublication::class.java)
    }

    @Test
    fun `should not create publication when version-catalog plugin is missing`() {
        val (project, _) = projectWithConfig()
        project.plugins.apply("maven-publish")

        PublishCatalogSetup.setupCatalogPublish(project)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        assertThat(publishing.publications.findByName("maven")).isNull()
    }
}
