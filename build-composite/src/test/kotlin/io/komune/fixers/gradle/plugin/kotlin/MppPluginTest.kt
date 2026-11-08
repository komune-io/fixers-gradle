package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.config.ConfigExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.testing.mocha.KotlinMocha
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
    fun `should configure js compilation for es2015 with bigint longs`() {
        val project = mppProject()

        val compileJs = project.tasks.getByName("compileKotlinJs") as Kotlin2JsCompile
        assertThat(compileJs.compilerOptions.target.get()).isEqualTo("es2015")
        assertThat(compileJs.compilerOptions.freeCompilerArgs.get())
            .contains("-Xes-long-as-bigint")
            .doesNotContain("-Xenable-suspend-function-exporting")
    }

    @Test
    fun `should give the js link tasks the options of the js target`() {
        val project = mppProject()

        val links = project.tasks.withType(KotlinJsIrLink::class.java)
        assertThat(links).isNotEmpty
        links.forEach { link ->
            assertThat(link.compilerOptions.target.get()).isEqualTo("es2015")
            assertThat(link.compilerOptions.freeCompilerArgs.get()).contains("-Xes-long-as-bigint")
        }
    }

    @Test
    fun `should run the js tests on node with mocha`() {
        val project = mppProject()

        val nodeTest = project.tasks.getByName("jsNodeTest") as KotlinJsTest
        assertThat(nodeTest.enabled).isTrue()
        assertThat(nodeTest.testFramework).isInstanceOf(KotlinMocha::class.java)
    }

    @Test
    fun `should not run the js tests in a browser`() {
        val project = mppProject()

        val browserTest = project.tasks.getByName("jsBrowserTest") as KotlinJsTest
        assertThat(browserTest.enabled).isFalse()
    }

    @Test
    fun `should keep the browser library distribution`() {
        val project = mppProject()

        assertThat(project.tasks.names).contains("jsBrowserProductionLibraryDistribution")
    }

    @Test
    fun `should compile the jvm target with strict jsr305 like the jvm plugin`() {
        val project = mppProject()

        val compileJvm = project.tasks.getByName("compileKotlinJvm") as KotlinCompile
        assertThat(compileJvm.compilerOptions.freeCompilerArgs.get()).contains("-Xjsr305=strict")
        assertThat(compileJvm.compilerOptions.languageVersion.orNull).isNull()
    }

    @Test
    fun `should report the extra compiler warnings on every target`() {
        val project = mppProject()

        val compileJvm = project.tasks.getByName("compileKotlinJvm") as KotlinCompile
        val compileJs = project.tasks.getByName("compileKotlinJs") as Kotlin2JsCompile
        assertThat(compileJvm.compilerOptions.freeCompilerArgs.get()).contains("-Wextra")
        assertThat(compileJs.compilerOptions.freeCompilerArgs.get()).contains("-Wextra")
    }

    @Test
    fun `should add the junit platform launcher to the jvm test runtime`() {
        val project = mppProject()

        project.dependencies.add("jvmTestImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:2.4.21")

        val jvmTestRuntime = project.configurations.getByName("jvmTestRuntimeClasspath")
        assertThat(jvmTestRuntime.incoming.dependencies.map { "${it.group}:${it.name}" })
            .contains("org.junit.platform:junit-platform-launcher")
    }

    @Test
    fun `should add implementation info to jvm jar manifest`() {
        val project = mppProject()

        val jar = project.tasks.getByName("jvmJar") as Jar
        assertThat(jar.manifest.attributes["Implementation-Title"]).isEqualTo("mpp-lib")
    }
}
