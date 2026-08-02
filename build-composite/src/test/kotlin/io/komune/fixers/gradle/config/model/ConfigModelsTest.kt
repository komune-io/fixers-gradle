package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.ConfigExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for config model defaults and mergeFrom behaviour.
 */
class ConfigModelsTest {

    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
    }

    @Nested
    inner class NpmModelTest {

        @Test
        fun `should have publishing enabled with komune defaults`() {
            val npm = Npm(project)

            assertThat(npm.publish.get()).isTrue()
            assertThat(npm.organization.get()).isEqualTo("komune-io")
            assertThat(npm.clean.get()).isTrue()
            assertThat(npm.tag.get()).isEqualTo("next")
            assertThat(npm.version.isPresent).isFalse()
        }

        @Test
        fun `should merge absent values only`() {
            val source = Npm(project).apply {
                version.set("1.0.0")
                tag.set("beta")
            }
            val target = Npm(project).apply {
                tag.set("alpha")
            }

            target.mergeFrom(source)

            assertThat(target.version.get()).isEqualTo("1.0.0")
            assertThat(target.tag.get()).isEqualTo("alpha")
        }
    }

    @Nested
    inner class DetektModelTest {

        @Test
        fun `should have sensible defaults`() {
            val detekt = Detekt(project)

            assertThat(detekt.disable.get()).isFalse()
            assertThat(detekt.config.get()).isEqualTo("detekt.yml")
            assertThat(detekt.buildUponDefaultConfig.get()).isTrue()
            assertThat(detekt.checkstyleReport.get()).isTrue()
            assertThat(detekt.baseline.isPresent).isFalse()
        }

        @Test
        fun `should merge absent values only`() {
            val source = Detekt(project).apply {
                baseline.set("baseline.xml")
                config.set("custom.yml")
            }
            val target = Detekt(project).apply {
                config.set("target.yml")
            }

            target.mergeFrom(source)

            assertThat(target.baseline.get()).isEqualTo("baseline.xml")
            assertThat(target.config.get()).isEqualTo("target.yml")
        }
    }

    @Nested
    inner class Kt2TsModelTest {

        @Test
        fun `should default output directory and empty additional cleaning`() {
            val kt2Ts = Kt2Ts(project)

            assertThat(kt2Ts.outputDirectory.get()).isEqualTo("platform/web/kotlin")
            assertThat(kt2Ts.inputDirectory.isPresent).isFalse()
            assertThat(kt2Ts.additionalCleaning.get()).isEmpty()
        }

        @Test
        fun `should merge absent values only`() {
            val source = Kt2Ts(project).apply {
                inputDirectory.set("build/js/packages")
                outputDirectory.set("source-out")
            }
            val target = Kt2Ts(project).apply {
                outputDirectory.set("target-out")
            }

            target.mergeFrom(source)

            assertThat(target.inputDirectory.get()).isEqualTo("build/js/packages")
            assertThat(target.outputDirectory.get()).isEqualTo("target-out")
        }
    }

    @Nested
    inner class JdkModelTest {

        @Test
        fun `should default to jdk 17`() {
            assertThat(Jdk(project).version.get()).isEqualTo(Jdk.VERSION_DEFAULT)
            assertThat(Jdk.VERSION_DEFAULT).isEqualTo(17)
        }

        @Test
        fun `should keep explicitly set version on merge`() {
            val source = Jdk(project).apply { version.set(11) }
            val target = Jdk(project).apply { version.set(21) }

            target.mergeFrom(source)

            assertThat(target.version.get()).isEqualTo(21)
        }
    }

    @Nested
    inner class RepositoriesModelTest {

        @Test
        fun `should default to maven central only`() {
            val repos = Repositories(project)

            assertThat(repos.mavenLocal.get()).isFalse()
            assertThat(repos.mavenCentral.get()).isTrue()
            assertThat(repos.sonatypeSnapshots.get()).isFalse()
            assertThat(repos.mavenUrls.get()).isEmpty()
        }

        @Test
        fun `should collect custom maven urls`() {
            val repos = Repositories(project)

            repos.maven("https://repo.example.com/a")
            repos.maven("https://repo.example.com/b")

            assertThat(repos.mavenUrls.get())
                .containsExactly("https://repo.example.com/a", "https://repo.example.com/b")
        }

        @Test
        fun `should not override convention-backed flags on merge`() {
            // All flags have conventions, so isPresent is always true and
            // mergeIfNotPresent keeps the target defaults.
            val source = Repositories(project).apply { mavenLocal.set(true) }
            val target = Repositories(project)

            target.mergeFrom(source)

            assertThat(target.mavenLocal.get()).isFalse()
        }
    }

    @Nested
    inner class PublicationModelTest {

        @Test
        fun `should merge configure action when absent`() {
            val source = Publication(project).apply {
                configure.set(org.gradle.api.Action { })
            }
            val target = Publication(project)

            target.mergeFrom(source)

            assertThat(target.configure.isPresent).isTrue()
        }

        @Test
        fun `should keep existing configure action on merge`() {
            val sourceAction = org.gradle.api.Action<org.gradle.api.publish.maven.MavenPom> { }
            val targetAction = org.gradle.api.Action<org.gradle.api.publish.maven.MavenPom> { }
            val source = Publication(project).apply { configure.set(sourceAction) }
            val target = Publication(project).apply { configure.set(targetAction) }

            target.mergeFrom(source)

            assertThat(target.configure.get()).isSameAs(targetAction)
        }
    }

    @Nested
    inner class ConfigExtensionDslTest {

        private fun createConfig(): ConfigExtension =
            project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)

        @Test
        fun `should configure kt2Ts npm jdk publish and repositories via DSL`() {
            val config = createConfig()

            config.kt2Ts { outputDirectory.set("custom/out") }
            config.npm { organization.set("custom-org") }
            config.jdk { version.set(21) }
            config.publish { pkgGithubUsername.set("user") }
            config.repositories { mavenLocal.set(true) }

            assertThat(config.kt2Ts.outputDirectory.get()).isEqualTo("custom/out")
            assertThat(config.npm.organization.get()).isEqualTo("custom-org")
            assertThat(config.jdk.version.get()).isEqualTo(21)
            assertThat(config.publish.pkgGithubUsername.get()).isEqualTo("user")
            assertThat(config.repositories.mavenLocal.get()).isTrue()
        }

        @Test
        fun `should store pom action via DSL`() {
            val config = createConfig()

            config.pom { }

            assertThat(config.pom.configure.isPresent).isTrue()
        }
    }
}
