package io.komune.fixers.gradle.check

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.plugin.check.CheckPlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for the CheckPlugin projectsEvaluated behaviour.
 */
class CheckPluginLifecycleTest {

    private fun fireProjectsEvaluated(project: Project) {
        val gradle = project.gradle as GradleInternal
        gradle.buildListenerBroadcaster.projectsEvaluated(gradle)
    }

    @Test
    fun `should configure sonar detekt and subproject jacoco after evaluation`() {
        val root = ProjectBuilder.builder().build()
        root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        val child = ProjectBuilder.builder().withParent(root).build()
        child.plugins.apply("java")
        root.plugins.apply(CheckPlugin::class.java)

        fireProjectsEvaluated(root)

        assertThat(root.plugins.hasPlugin("org.sonarqube")).isTrue()
        assertThat(root.tasks.findByName("generateSonarProperties")).isNotNull
        assertThat(root.tasks.findByName("detektReportMergeXml")).isNotNull
        assertThat(child.plugins.hasPlugin("jacoco")).isTrue()
    }

    @Test
    fun `should skip detekt configuration when disabled`() {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        config.detekt.disable.set(true)
        root.plugins.apply(CheckPlugin::class.java)

        fireProjectsEvaluated(root)

        assertThat(root.plugins.hasPlugin("org.sonarqube")).isTrue()
        assertThat(root.tasks.findByName("detektReportMergeXml")).isNull()
    }

    @Test
    fun `should work without fixers configuration`() {
        val root = ProjectBuilder.builder().build()
        root.plugins.apply(CheckPlugin::class.java)

        fireProjectsEvaluated(root)

        assertThat(root.plugins.hasPlugin("org.sonarqube")).isTrue()
    }
}
