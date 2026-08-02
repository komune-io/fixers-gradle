package io.komune.fixers.gradle.config

import org.assertj.core.api.Assertions.assertThat
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for the fixers extension accessors and PublishConfig helpers.
 */
class FixersExtensionTest {

    @Test
    fun `fixers accessor should return null when extension is absent`() {
        val project = ProjectBuilder.builder().build()

        assertThat(project.extensions.fixers).isNull()
    }

    @Test
    fun `fixers accessor should return the registered extension`() {
        val project = ProjectBuilder.builder().build()
        val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)

        assertThat(project.extensions.fixers).isSameAs(config)
    }

    @Test
    fun `Project fixers should configure the root extension from a subproject`() {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        val child = ProjectBuilder.builder().withParent(root).build()

        child.fixers { bundle.description.set("configured-from-child") }

        assertThat(config.bundle.description.get()).isEqualTo("configured-from-child")
    }

    @Test
    fun `fixersIfExists should run the action only when the extension exists`() {
        val project = ProjectBuilder.builder().build()
        var executed = false

        project.extensions.fixersIfExists { executed = true }
        assertThat(executed).isFalse()

        project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)
        project.extensions.fixersIfExists { executed = true }
        assertThat(executed).isTrue()
    }

    @Test
    fun `PluginDependenciesSpec fixers should prefix the komune plugin id`() {
        val requestedIds = mutableListOf<String>()
        val spec = object : PluginDependenciesSpec {
            override fun id(id: String): PluginDependencySpec {
                requestedIds.add(id)
                return object : PluginDependencySpec {
                    override fun version(version: String?): PluginDependencySpec = this
                    override fun apply(apply: Boolean): PluginDependencySpec = this
                }
            }
        }

        spec.fixers("kotlin.jvm")

        assertThat(requestedIds).containsExactly("io.komune.fixers.gradle.kotlin.jvm")
    }

    @Test
    fun `PublishConfig getStagingRepositoryPath should resolve into build directory`() {
        val project = ProjectBuilder.builder().build()
        val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)

        val path = config.publish.getStagingRepositoryPath(project)

        assertThat(path).endsWith("staging-deploy")
        assertThat(path).contains(project.layout.buildDirectory.get().asFile.name)
    }

    @Test
    fun `PublishConfig toString should mask secrets`() {
        val project = ProjectBuilder.builder().build()
        val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)
        config.publish.pkgGithubToken.set("secret-token")
        config.publish.npmjsToken.set("npm-secret")

        val str = config.publish.toString()

        assertThat(str).doesNotContain("secret-token")
        assertThat(str).doesNotContain("npm-secret")
        assertThat(str).contains("pkgGithubToken=******")
        assertThat(str).contains("npmjsToken=******")
    }
}
