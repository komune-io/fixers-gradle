package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.plugin.config.ConfigPlugin
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.jvm.tasks.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.junit.jupiter.api.Test

/**
 * Unit tests for JvmPlugin (and the setupJarInfo/configureJUnitPlatform helpers it applies).
 */
class JvmPluginTest {

    private fun jvmProject(configure: (ConfigExtension) -> Unit = {}): Project {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        configure(config)
        val project = ProjectBuilder.builder().withParent(root).withName("jvm-lib").build()
        project.plugins.apply(JvmPlugin::class.java)
        return project
    }

    @Test
    fun `should apply java kotlin and config plugins`() {
        val project = jvmProject()

        assertThat(project.plugins.hasPlugin("java")).isTrue()
        assertThat(project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")).isTrue()
        assertThat(project.plugins.hasPlugin(ConfigPlugin::class.java)).isTrue()
    }

    @Test
    fun `should configure java toolchain with default jdk version`() {
        val project = jvmProject()

        val java = project.extensions.getByType(JavaPluginExtension::class.java)
        assertThat(java.toolchain.languageVersion.get().asInt()).isEqualTo(17)
    }

    @Test
    fun `should configure java toolchain with configured jdk version`() {
        val project = jvmProject { config -> config.jdk.version.set(21) }

        val java = project.extensions.getByType(JavaPluginExtension::class.java)
        assertThat(java.toolchain.languageVersion.get().asInt()).isEqualTo(21)
    }

    @Test
    fun `should configure kotlin compiler options`() {
        val project = jvmProject()

        val kotlin = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
        assertThat(kotlin.compilerOptions.jvmTarget.get()).isEqualTo(JvmTarget.JVM_17)
        assertThat(kotlin.compilerOptions.freeCompilerArgs.get()).contains("-Xjsr305=strict")
        assertThat(kotlin.compilerOptions.languageVersion.orNull).isNotNull
    }

    @Test
    fun `should add kotlin reflect dependency`() {
        val project = jvmProject()

        val implementation = project.configurations.getByName("implementation")
        assertThat(implementation.dependencies.map { it.name }).contains("kotlin-reflect")
    }

    @Test
    fun `should configure test tasks to use junit platform`() {
        val project = jvmProject()

        val test = project.tasks.getByName("test") as TestTask
        assertThat(test.options).isInstanceOf(JUnitPlatformOptions::class.java)
    }

    @Test
    fun `should add implementation info to jar manifest`() {
        val project = jvmProject()
        project.version = "1.2.3"

        val jar = project.tasks.getByName("jar") as Jar
        assertThat(jar.manifest.attributes["Implementation-Title"]).isEqualTo("jvm-lib")
        assertThat(jar.manifest.attributes["Implementation-Version"]).isEqualTo(project.version)
    }
}
