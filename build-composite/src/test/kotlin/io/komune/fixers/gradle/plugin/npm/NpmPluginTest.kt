package io.komune.fixers.gradle.plugin.npm

import dev.petuska.npm.publish.NpmPublishPlugin
import dev.petuska.npm.publish.extension.NpmPublishExtension
import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.plugin.npm.task.NpmTsGenTask
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for NpmPlugin.
 */
class NpmPluginTest {

    private fun evaluatedNpmProject(
        version: String = "1.0.0",
        configure: (ConfigExtension) -> Unit = {}
    ): Project {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        configure(config)
        val project = ProjectBuilder.builder().withParent(root).withName("npm-lib").build()
        project.version = version
        project.plugins.apply(NpmPlugin::class.java)
        (project as ProjectInternal).evaluate()
        return project
    }

    @Test
    fun `should apply npm publish plugin and configure registries`() {
        val project = evaluatedNpmProject()

        assertThat(project.plugins.hasPlugin(NpmPublishPlugin::class.java)).isTrue()
        val npm = project.extensions.getByType(NpmPublishExtension::class.java)
        assertThat(npm.organization.get()).isEqualTo("komune-io")
        assertThat(npm.version.get()).isEqualTo("1.0.0")
        assertThat(npm.registries.names).contains("npmjs", "github")
    }

    @Test
    fun `should prefer configured npm version over project version`() {
        val project = evaluatedNpmProject { config -> config.npm.version.set("2.5.0") }

        val npm = project.extensions.getByType(NpmPublishExtension::class.java)
        assertThat(npm.version.get()).isEqualTo("2.5.0")
    }

    @Test
    fun `should register npmTsGenTask in build group`() {
        val project = evaluatedNpmProject()

        val task = project.tasks.findByName("npmTsGenTask")
        assertThat(task).isNotNull
        assertThat(task!!.group).isEqualTo("build")
        assertThat(task).isInstanceOf(NpmTsGenTask::class.java)
    }

    @Test
    fun `should skip configuration when npm publish is disabled`() {
        val project = evaluatedNpmProject { config -> config.npm.publish.set(false) }

        assertThat(project.plugins.hasPlugin(NpmPublishPlugin::class.java)).isFalse()
        assertThat(project.tasks.findByName("npmTsGenTask")).isNull()
    }

    @Test
    fun `should skip configuration when root has no fixers config`() {
        val root = ProjectBuilder.builder().build()
        val project = ProjectBuilder.builder().withParent(root).build()
        project.plugins.apply(NpmPlugin::class.java)

        (project as ProjectInternal).evaluate()

        assertThat(project.plugins.hasPlugin(NpmPublishPlugin::class.java)).isFalse()
    }

    @Test
    fun `should configure prerelease versions without failure`() {
        val project = evaluatedNpmProject(version = "1.0.0-SNAPSHOT.abc123")

        val npm = project.extensions.getByType(NpmPublishExtension::class.java)
        assertThat(npm.version.get()).isEqualTo("1.0.0-SNAPSHOT.abc123")
    }
}
