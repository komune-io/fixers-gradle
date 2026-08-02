package io.komune.fixers.gradle.check

import io.komune.fixers.gradle.config.model.Jacoco
import io.komune.fixers.gradle.plugin.check.JacocoConfigurator
import io.komune.fixers.gradle.plugin.kotlin.MppPlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.junit.jupiter.api.Test

/**
 * Unit tests for JacocoConfigurator on multiplatform projects and version overrides.
 */
class JacocoConfiguratorMppTest {

    private fun mppProject(): Project {
        val root = ProjectBuilder.builder().build()
        val project = ProjectBuilder.builder().withParent(root).withName("mpp-lib").build()
        project.plugins.apply(MppPlugin::class.java)
        return project
    }

    @Test
    fun `should register jacocoJvmTestReport for multiplatform projects`() {
        val project = mppProject()
        val configurator = JacocoConfigurator(project)

        configurator.configure(Jacoco(project))

        assertThat(project.plugins.hasPlugin("jacoco")).isTrue()
        val report = project.tasks.findByName("jacocoJvmTestReport") as JacocoReport?
        assertThat(report).isNotNull
        assertThat(report!!.reports.xml.outputLocation.get().asFile.path)
            .endsWith("reports/jacoco/jvmTest/${Jacoco.DEFAULT_XML_REPORT_FILENAME}")
    }

    @Test
    fun `should use custom xml report filename for multiplatform report`() {
        val project = mppProject()
        val configurator = JacocoConfigurator(project)
        val jacoco = Jacoco(project).apply {
            xmlReportFilename.set("custom.xml")
        }

        configurator.configure(jacoco)

        val report = project.tasks.findByName("jacocoJvmTestReport") as JacocoReport
        assertThat(report.reports.xml.outputLocation.get().asFile.path).endsWith("jvmTest/custom.xml")
    }

    @Test
    fun `should do nothing for multiplatform when jacoco is disabled`() {
        val project = mppProject()
        val configurator = JacocoConfigurator(project)
        val jacoco = Jacoco(project).apply { enabled.set(false) }

        configurator.configure(jacoco)

        assertThat(project.plugins.hasPlugin("jacoco")).isFalse()
        assertThat(project.tasks.findByName("jacocoJvmTestReport")).isNull()
    }

    @Test
    fun `should return early when project has no multiplatform extension`() {
        val project = ProjectBuilder.builder().build()
        val configurator = JacocoConfigurator(project)

        configurator.configureJacocoForMultiplatform(Jacoco(project))

        assertThat(project.tasks.findByName("jacocoJvmTestReport")).isNull()
    }

    @Test
    fun `should configure tool version from config`() {
        val project = ProjectBuilder.builder().build()
        val configurator = JacocoConfigurator(project)
        val jacoco = Jacoco(project).apply { version.set("0.8.11") }

        configurator.applyJacocoPlugin(jacoco)

        val extension = project.extensions.getByType(JacocoPluginExtension::class.java)
        assertThat(extension.toolVersion).isEqualTo("0.8.11")
    }

    @Test
    fun `should finalize test tasks with jacoco report on jvm projects`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        val configurator = JacocoConfigurator(project)

        configurator.configure(Jacoco(project))

        val test = project.tasks.getByName("test")
        val finalizers = test.finalizedBy.getDependencies(test).map { it.name }
        assertThat(finalizers).contains("jacocoTestReport")
    }
}
