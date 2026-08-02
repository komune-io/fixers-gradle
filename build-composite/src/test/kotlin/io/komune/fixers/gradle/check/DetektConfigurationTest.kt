package io.komune.fixers.gradle.check

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.plugin.check.configureDetekt
import io.komune.fixers.gradle.plugin.check.getDetektReportMergeXmlFile
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for the Detekt configuration helpers.
 */
class DetektConfigurationTest {

    private fun rootWithConfig(dir: File? = null): Pair<Project, ConfigExtension> {
        val builder = ProjectBuilder.builder()
        if (dir != null) {
            builder.withProjectDir(dir)
        }
        val root = builder.build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        return root to config
    }

    @Test
    fun `should point merge xml file into root build directory`() {
        val (root, _) = rootWithConfig()

        val mergeFile = root.getDetektReportMergeXmlFile()

        assertThat(mergeFile.get().asFile.path).endsWith("reports/detekt/merge.xml")
    }

    @Test
    fun `should register merge tasks and apply detekt plugin to all projects`() {
        val (root, _) = rootWithConfig()
        val child = ProjectBuilder.builder().withParent(root).build()

        root.configureDetekt()

        assertThat(root.tasks.findByName("detektReportMergeSarif")).isNotNull
        assertThat(root.tasks.findByName("detektReportMergeXml")).isNotNull
        assertThat(root.plugins.hasPlugin("dev.detekt")).isTrue()
        assertThat(child.plugins.hasPlugin("dev.detekt")).isTrue()
    }

    @Test
    fun `should configure detekt extension with defaults`() {
        val (root, _) = rootWithConfig()

        root.configureDetekt()

        val detekt = root.extensions.getByType(DetektExtension::class.java)
        assertThat(detekt.buildUponDefaultConfig.get()).isTrue()
        // No detekt.yml in the project dir: the built-in default config is used
        assertThat(detekt.config.files).isEmpty()
    }

    @Test
    fun `should use detekt config file when present in root dir`(@TempDir dir: File) {
        val configFile = File(dir, "detekt.yml")
        configFile.writeText("build:\n  maxIssues: 0\n")
        val (root, _) = rootWithConfig(dir)

        root.configureDetekt()

        val detekt = root.extensions.getByType(DetektExtension::class.java)
        assertThat(detekt.config.files.map { it.canonicalFile }).containsExactly(configFile.canonicalFile)
    }

    @Test
    fun `should set baseline when configured`(@TempDir dir: File) {
        val (root, config) = rootWithConfig(dir)
        config.detekt.baseline.set("detekt-baseline.xml")

        root.configureDetekt()

        val detekt = root.extensions.getByType(DetektExtension::class.java)
        assertThat(detekt.baseline.get().asFile.name).isEqualTo("detekt-baseline.xml")
    }

    @Test
    fun `should configure report outputs on detekt tasks`() {
        val (root, _) = rootWithConfig()

        root.configureDetekt()

        val detektTasks = root.tasks.withType(Detekt::class.java)
        assertThat(detektTasks).isNotEmpty
        detektTasks.forEach { task ->
            assertThat(task.reports.checkstyle.required.get()).isTrue()
            assertThat(task.reports.checkstyle.outputLocation.get().asFile.path)
                .endsWith("reports/detekt/detekt.xml")
            assertThat(task.reports.sarif.required.get()).isTrue()
            assertThat(task.reports.html.required.get()).isTrue()
            assertThat(task.reports.markdown.required.get()).isTrue()
        }
    }

    @Test
    fun `should disable reports when configured off`() {
        val (root, config) = rootWithConfig()
        config.detekt.checkstyleReport.set(false)
        config.detekt.sarifReport.set(false)
        config.detekt.htmlReport.set(false)
        config.detekt.markdownReport.set(false)

        root.configureDetekt()

        val detektTasks = root.tasks.withType(Detekt::class.java)
        assertThat(detektTasks).isNotEmpty
        detektTasks.forEach { task ->
            assertThat(task.reports.checkstyle.required.get()).isFalse()
            assertThat(task.reports.sarif.required.get()).isFalse()
            assertThat(task.reports.html.required.get()).isFalse()
            assertThat(task.reports.markdown.required.get()).isFalse()
        }
    }
}
