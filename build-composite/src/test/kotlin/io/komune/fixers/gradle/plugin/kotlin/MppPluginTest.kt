package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.ConfigExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.junit.jupiter.api.Test

/**
 * Unit tests for MppPlugin and MppJsPlugin.
 */
class MppPluginTest {

    private fun mppProject(configure: (ConfigExtension) -> Unit = {}): Project {
        val root = ProjectBuilder.builder().build()
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        configure(config)
        val project = ProjectBuilder.builder().withParent(root).withName("mpp-lib").build()
        project.plugins.apply(MppPlugin::class.java)
        return project
    }

    @Test
    fun `should apply kotlin multiplatform and js plugins`() {
        val project = mppProject()

        assertThat(project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")).isTrue()
        assertThat(project.plugins.hasPlugin(MppJsPlugin::class.java)).isTrue()
    }

    @Test
    fun `should define jvm and js targets`() {
        val project = mppProject()

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        assertThat(kotlin.targets.names).contains("jvm", "js")
    }

    @Test
    fun `should create common and target source sets`() {
        val project = mppProject()

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        assertThat(kotlin.sourceSets.names).contains("commonMain", "jvmMain", "jsMain", "jsTest")
    }

    @Test
    fun `should configure jvm compilation with default jdk version`() {
        val project = mppProject()

        val compileJvm = project.tasks.getByName("compileKotlinJvm") as KotlinCompile
        assertThat(compileJvm.compilerOptions.jvmTarget.get()).isEqualTo(JvmTarget.JVM_17)
    }

    @Test
    fun `should configure jvm compilation with configured jdk version`() {
        val project = mppProject { config -> config.jdk.version.set(21) }

        val compileJvm = project.tasks.getByName("compileKotlinJvm") as KotlinCompile
        assertThat(compileJvm.compilerOptions.jvmTarget.get()).isEqualTo(JvmTarget.JVM_21)
    }

    @Test
    fun `should configure js compilation for es2015 with export flags`() {
        val project = mppProject()

        val compileJs = project.tasks.getByName("compileKotlinJs") as Kotlin2JsCompile
        assertThat(compileJs.compilerOptions.target.get()).isEqualTo("es2015")
        assertThat(compileJs.compilerOptions.freeCompilerArgs.get())
            .contains("-Xes-long-as-bigint", "-Xenable-suspend-function-exporting")
    }

    @Test
    fun `should add implementation info to jvm jar manifest`() {
        val project = mppProject()

        val jar = project.tasks.getByName("jvmJar") as Jar
        assertThat(jar.manifest.attributes["Implementation-Title"]).isEqualTo("mpp-lib")
    }
}
