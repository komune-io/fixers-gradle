package io.komune.fixers.gradle.check

import io.komune.fixers.gradle.config.model.Jacoco
import io.komune.fixers.gradle.plugin.check.JacocoConfigurator
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for JacocoConfigurator.
 */
class JacocoConfiguratorTest {

    private lateinit var project: Project
    private lateinit var configurator: JacocoConfigurator

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        configurator = JacocoConfigurator(project)
    }

    @Nested
    inner class IsEnabledTest {

        @Test
        fun `should return true by default when config is null`() {
            assertThat(configurator.isEnabled(null)).isTrue()
        }

        @Test
        fun `should return true when enabled is not set`() {
            val jacoco = Jacoco(project)
            assertThat(configurator.isEnabled(jacoco)).isTrue()
        }

        @Test
        fun `should return false when explicitly disabled`() {
            val jacoco = Jacoco(project).apply {
                enabled.set(false)
            }
            assertThat(configurator.isEnabled(jacoco)).isFalse()
        }

        @Test
        fun `should return true when explicitly enabled`() {
            val jacoco = Jacoco(project).apply {
                enabled.set(true)
            }
            assertThat(configurator.isEnabled(jacoco)).isTrue()
        }
    }

    @Nested
    inner class IsHtmlReportEnabledTest {

        @Test
        fun `should return true by default when config is null`() {
            assertThat(configurator.isHtmlReportEnabled(null)).isTrue()
        }

        @Test
        fun `should return true when htmlReport is not set`() {
            val jacoco = Jacoco(project)
            assertThat(configurator.isHtmlReportEnabled(jacoco)).isTrue()
        }

        @Test
        fun `should return false when htmlReport is disabled`() {
            val jacoco = Jacoco(project).apply {
                htmlReport.set(false)
            }
            assertThat(configurator.isHtmlReportEnabled(jacoco)).isFalse()
        }

        @Test
        fun `should return true when htmlReport is enabled`() {
            val jacoco = Jacoco(project).apply {
                htmlReport.set(true)
            }
            assertThat(configurator.isHtmlReportEnabled(jacoco)).isTrue()
        }
    }

    @Nested
    inner class IsXmlReportEnabledTest {

        @Test
        fun `should return true by default when config is null`() {
            assertThat(configurator.isXmlReportEnabled(null)).isTrue()
        }

        @Test
        fun `should return true when xmlReport is not set`() {
            val jacoco = Jacoco(project)
            assertThat(configurator.isXmlReportEnabled(jacoco)).isTrue()
        }

        @Test
        fun `should return false when xmlReport is disabled`() {
            val jacoco = Jacoco(project).apply {
                xmlReport.set(false)
            }
            assertThat(configurator.isXmlReportEnabled(jacoco)).isFalse()
        }

        @Test
        fun `should return true when xmlReport is enabled`() {
            val jacoco = Jacoco(project).apply {
                xmlReport.set(true)
            }
            assertThat(configurator.isXmlReportEnabled(jacoco)).isTrue()
        }
    }

    @Nested
    inner class GetXmlReportFilenameTest {

        @Test
        fun `should return default filename when config is null`() {
            assertThat(configurator.getXmlReportFilename(null))
                .isEqualTo(Jacoco.DEFAULT_XML_REPORT_FILENAME)
        }

        @Test
        fun `should return default filename when not set`() {
            val jacoco = Jacoco(project)
            assertThat(configurator.getXmlReportFilename(jacoco))
                .isEqualTo(Jacoco.DEFAULT_XML_REPORT_FILENAME)
        }

        @Test
        fun `should return custom filename when set`() {
            val jacoco = Jacoco(project).apply {
                xmlReportFilename.set("custom-report.xml")
            }
            assertThat(configurator.getXmlReportFilename(jacoco))
                .isEqualTo("custom-report.xml")
        }
    }

    @Nested
    inner class ApplyJacocoPluginTest {

        @Test
        fun `should apply jacoco plugin`() {
            configurator.applyJacocoPlugin()

            assertThat(project.plugins.hasPlugin("jacoco")).isTrue()
        }
    }

    @Nested
    inner class ConfigureWithJavaPluginTest {

        @Test
        fun `should configure jacoco when java plugin is applied and enabled`() {
            project.plugins.apply("java")
            val jacoco = Jacoco(project).apply {
                enabled.set(true)
            }

            configurator.configure(jacoco)

            assertThat(project.plugins.hasPlugin("jacoco")).isTrue()
        }

        @Test
        fun `should not configure jacoco when disabled`() {
            project.plugins.apply("java")
            val jacoco = Jacoco(project).apply {
                enabled.set(false)
            }

            configurator.configure(jacoco)

            // JaCoCo plugin should not be applied when disabled
            assertThat(project.plugins.hasPlugin("jacoco")).isFalse()
        }

        @Test
        fun `should configure jacoco with null config and default enabled`() {
            project.plugins.apply("java")

            configurator.configure(null)

            // null config defaults to enabled=true, so JaCoCo should be applied
            assertThat(project.plugins.hasPlugin("jacoco")).isTrue()
        }
    }

    @Nested
    inner class ConfigureReportTasksTest {

        @Test
        fun `should configure report tasks with default settings`() {
            project.plugins.apply("java")
            configurator.applyJacocoPlugin()

            // This should not throw
            configurator.configureJacocoReportTasks(null)
        }

        @Test
        fun `should configure report tasks with custom settings`() {
            project.plugins.apply("java")
            val jacoco = Jacoco(project).apply {
                htmlReport.set(false)
                xmlReport.set(true)
            }
            configurator.applyJacocoPlugin()

            // This should not throw
            configurator.configureJacocoReportTasks(jacoco)
        }
    }
}
