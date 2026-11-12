package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.buildFixersRoot
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
        project = ProjectBuilder.builder().buildFixersRoot()
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

            assertThat(detekt.checkstyleReport.get()).isTrue()
            assertThat(detekt.htmlReport.get()).isTrue()
        }

        @Test
        fun `should merge absent values only`() {
            val source = Detekt(project).apply { htmlReport.set(false) }
            val target = Detekt(project).apply { sarifReport.set(false) }

            target.mergeFrom(source)

            assertThat(target.sarifReport.get()).isFalse()
            assertThat(target.htmlReport.get()).isTrue()
        }
    }

    @Nested
    inner class Kt2TsModelTest {

        @Test
        fun `should commit the declarations only when nothing sets committedFiles`() {
            assertThat(Kt2Ts(project).committedFiles.get()).isEqualTo(Kt2TsFiles.DECLARATIONS)
        }

        @Test
        fun `should default output directory and empty additional cleaning`() {
            val kt2Ts = Kt2Ts(project)

            assertThat(kt2Ts.outputDirectory.get()).isEqualTo("platform/web/kotlin")
            assertThat(kt2Ts.inputDirectory.isPresent).isFalse()
            assertThat(kt2Ts.additionalCleaning.get()).isEmpty()
        }
    }

    @Nested
    inner class JacocoModelTest {

        @Test
        fun `should have reports enabled by default`() {
            val jacoco = Jacoco(project)

            assertThat(jacoco.enabled.get()).isTrue()
            assertThat(jacoco.htmlReport.get()).isTrue()
            assertThat(jacoco.xmlReport.get()).isTrue()
            assertThat(jacoco.xmlReportFilename.get()).isEqualTo(Jacoco.DEFAULT_XML_REPORT_FILENAME)
            assertThat(jacoco.version.get()).isNotBlank()
        }

        @Test
        fun `should not override convention-backed values on merge`() {
            // All properties have conventions, so isPresent is always true and
            // mergeIfNotPresent keeps the target values .
            val source = Jacoco(project).apply {
                enabled.set(false)
                xmlReportFilename.set("source.xml")
            }
            val target = Jacoco(project).apply {
                xmlReportFilename.set("target.xml")
            }

            target.mergeFrom(source)

            assertThat(target.enabled.get()).isTrue()
            assertThat(target.xmlReportFilename.get()).isEqualTo("target.xml")
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
    inner class StrictCompilationModelTest {

        @Test
        fun `should check return values in tests by default`() {
            assertThat(StrictCompilation(project).returnValueCheckerInTests.get()).isTrue()
        }

        @Test
        fun `should read the project property`() {
            project.extensions.extraProperties["fixers.strictCompilation.returnValueCheckerInTests"] = "false"

            assertThat(StrictCompilation(project).returnValueCheckerInTests.get()).isFalse()
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
        fun `should configure kt2Ts npm jdk and publish via DSL`() {
            val config = createConfig()

            config.kt2Ts { outputDirectory.set("custom/out") }
            config.npm { organization.set("custom-org") }
            config.jdk { version.set(21) }
            config.publish { pkgGithubUsername.set("user") }

            assertThat(config.kt2Ts.outputDirectory.get()).isEqualTo("custom/out")
            assertThat(config.npm.organization.get()).isEqualTo("custom-org")
            assertThat(config.jdk.version.get()).isEqualTo(21)
            assertThat(config.publish.pkgGithubUsername.get()).isEqualTo("user")
        }

        @Test
        fun `should store pom action via DSL`() {
            val config = createConfig()

            config.pom { }

            assertThat(config.pom.configure.isPresent).isTrue()
        }
    }
}
