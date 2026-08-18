package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.fixers
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.GradleInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for ConfigPlugin and the Project.config() extension.
 */
class ConfigPluginTest {

    private fun fireProjectsEvaluated(project: Project) {
        val gradle = project.gradle as GradleInternal
        gradle.buildListenerBroadcaster.projectsEvaluated(gradle)
    }

    @Nested
    inner class ApplyTest {

        @Test
        fun `should apply base plugin and create fixers extension`() {
            val project = ProjectBuilder.builder().build()

            project.plugins.apply(ConfigPlugin::class.java)

            assertThat(project.plugins.hasPlugin("base")).isTrue()
            assertThat(project.extensions.fixers).isNotNull
        }

        @Test
        fun `should not reapply base plugin when already present`() {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("base")

            project.plugins.apply(ConfigPlugin::class.java)

            assertThat(project.plugins.hasPlugin(ConfigPlugin::class.java)).isTrue()
        }

        @Test
        fun `should set version from VERSION file on root and subprojects`(@TempDir dir: File) {
            File(dir, "VERSION").writeText("9.9.9\n")
            val root = ProjectBuilder.builder().withProjectDir(dir).build()
            val child = ProjectBuilder.builder().withParent(root).build()

            root.plugins.apply(ConfigPlugin::class.java)

            assertThat(root.version.toString()).isEqualTo("9.9.9")
            assertThat(child.version.toString()).isEqualTo("9.9.9")
        }

        @Test
        fun `should keep project version when no VERSION file exists`() {
            val root = ProjectBuilder.builder().build()
            root.version = "1.2.3"

            root.plugins.apply(ConfigPlugin::class.java)

            assertThat(root.version.toString()).isEqualTo("1.2.3")
        }
    }

    @Nested
    inner class ProjectsEvaluatedTest {

        @Test
        fun `should propagate bundle group to root and subprojects`() {
            val root = ProjectBuilder.builder().build()
            val child = ProjectBuilder.builder().withParent(root).build()
            root.plugins.apply(ConfigPlugin::class.java)
            root.extensions.fixers!!.bundle.group.set("io.komune.test")

            fireProjectsEvaluated(root)

            assertThat(root.group).isEqualTo("io.komune.test")
            assertThat(child.group).isEqualTo("io.komune.test")
        }

        @Test
        fun `should merge root config into subprojects`() {
            val root = ProjectBuilder.builder().build()
            val child = ProjectBuilder.builder().withParent(root).build()
            root.plugins.apply(ConfigPlugin::class.java)
            root.extensions.fixers!!.bundle.description.set("Root description")
            root.extensions.fixers!!.publish.pkgGithubUsername.set("root-user")

            fireProjectsEvaluated(root)

            val childConfig = child.extensions.fixers
            assertThat(childConfig).isNotNull
            assertThat(childConfig!!.bundle.description.get()).isEqualTo("Root description")
            assertThat(childConfig.publish.pkgGithubUsername.get()).isEqualTo("root-user")
        }

        @Test
        fun `should keep subproject jacoco overrides when merging root config`() {
            val root = ProjectBuilder.builder().build()
            val child = ProjectBuilder.builder().withParent(root).build()
            root.plugins.apply(ConfigPlugin::class.java)
            root.extensions.fixers!!.jacoco.xmlReportFilename.set("root.xml")
            child.config().jacoco.xmlReportFilename.set("child.xml")

            fireProjectsEvaluated(root)

            assertThat(child.extensions.fixers!!.jacoco.xmlReportFilename.get()).isEqualTo("child.xml")
        }

        @Test
        fun `should configure maven central repository by default`() {
            val root = ProjectBuilder.builder().build()
            root.plugins.apply(ConfigPlugin::class.java)

            fireProjectsEvaluated(root)

            val urls = root.repositories.filterIsInstance<MavenArtifactRepository>().map { it.url.toString() }
            assertThat(urls).anyMatch { it.contains("repo.maven.apache.org") }
        }

        @Test
        fun `should configure custom maven urls and sonatype snapshots when enabled`() {
            val root = ProjectBuilder.builder().build()
            root.plugins.apply(ConfigPlugin::class.java)
            val config = root.extensions.fixers!!
            config.repositories.sonatypeSnapshots.set(true)
            config.repositories.maven("https://repo.example.com/custom")

            fireProjectsEvaluated(root)

            val urls = root.repositories.filterIsInstance<MavenArtifactRepository>().map { it.url.toString() }
            assertThat(urls).anyMatch { it.contains("central.sonatype.com/repository/maven-snapshots") }
            assertThat(urls).contains("https://repo.example.com/custom")
        }

        @Test
        fun `should configure mavenLocal only when enabled`() {
            val root = ProjectBuilder.builder().build()
            root.plugins.apply(ConfigPlugin::class.java)
            root.extensions.fixers!!.repositories.mavenLocal.set(true)

            fireProjectsEvaluated(root)

            assertThat(root.repositories.findByName("MavenLocal")).isNotNull
        }

        @Test
        fun `should register kt2Ts tasks`() {
            val root = ProjectBuilder.builder().build()
            root.plugins.apply(ConfigPlugin::class.java)

            fireProjectsEvaluated(root)

            assertThat(root.tasks.findByName("cleanTsGen")).isNotNull
            assertThat(root.tasks.findByName("tsGen")).isNotNull
        }
    }

    @Nested
    inner class ConfigExtensionFunctionTest {

        @Test
        fun `should create extension on first call and reuse it afterwards`() {
            val project = ProjectBuilder.builder().build()

            val first = project.config()
            val second = project.config()

            assertThat(first).isSameAs(second)
            assertThat(project.extensions.findByType(ConfigExtension::class.java)).isSameAs(first)
        }

        @Test
        fun `should apply configuration block`() {
            val project = ProjectBuilder.builder().build()

            val config = project.config {
                bundle.description.set("configured")
            }

            assertThat(config.bundle.description.get()).isEqualTo("configured")
        }
    }
}
