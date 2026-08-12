package io.komune.fixers.gradle.plugin.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Unit tests for [setupJarInfo].
 */
class JarExtentionTest {

    private fun javaProject(name: String, version: String): Project {
        val project = ProjectBuilder.builder().withName(name).build()
        project.version = version
        project.plugins.apply("java")
        return project
    }

    @Test
    fun `should add implementation title and version to the jar manifest`() {
        val project = javaProject("my-lib", "1.2.3")

        project.setupJarInfo()

        val manifest = (project.tasks.getByName("jar") as Jar).manifest
        assertThat(manifest.attributes["Implementation-Title"]).isEqualTo("my-lib")
        assertThat(manifest.attributes["Implementation-Version"]).isEqualTo("1.2.3")
    }

    @Test
    fun `should configure jar tasks registered after the call`() {
        val project = javaProject("late-lib", "9.9.9")

        project.setupJarInfo()
        val later = project.tasks.register("extraJar", Jar::class.java).get()

        assertThat(later.manifest.attributes["Implementation-Title"]).isEqualTo("late-lib")
        assertThat(later.manifest.attributes["Implementation-Version"]).isEqualTo("9.9.9")
    }
}
