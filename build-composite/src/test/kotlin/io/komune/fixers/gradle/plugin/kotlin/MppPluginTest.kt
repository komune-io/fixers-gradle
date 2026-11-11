@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalJsTestDsl::class)

package io.komune.fixers.gradle.plugin.kotlin

import io.komune.fixers.gradle.buildFixersRoot
import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.plugin.config.ConfigPlugin
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.junit.jupiter.api.Test

/**
 * Unit tests for MppPlugin and MppJsPlugin.
 */
class MppPluginTest {

    private fun mppProject(configure: (ConfigExtension) -> Unit = {}): Project {
        val root = ProjectBuilder.builder().buildFixersRoot()
        root.plugins.apply(ConfigPlugin::class.java)
        configure(root.extensions.getByType(ConfigExtension::class.java))
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
    fun `should leave no warning level to the js options, every warning being an error`() {
        val project = mppProject()

        val compileJs = project.tasks.getByName("compileKotlinJs") as Kotlin2JsCompile
        val links = project.tasks.withType(KotlinJsIrLink::class.java)
        assertThat(links).isNotEmpty
        (links + compileJs).forEach { task ->
            assertThat(task.compilerOptions.freeCompilerArgs.get()).noneMatch { it.startsWith("-Xwarning-level") }
        }
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
    fun `should run the js tests in chromium and firefox with playwright`() {
        val project = mppProject()

        val browserTest = project.tasks.getByName("jsBrowserTest") as KotlinJsTest
        assertThat(browserTest.enabled).isTrue()
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val js = kotlin.targets.getByName("js") as KotlinJsIrTarget
        assertThat(js.browser.test.allBrowserRunners.get().keys).containsExactlyInAnyOrder("chromium", "firefox")
    }

    @Test
    fun `should not run the js tests on node`() {
        val project = mppProject()

        assertThat(project.tasks.findByName("jsNodeTest")).isNull()
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
    fun `should treat warnings as errors and check ignored return values on every target`() {
        val project = mppProject()

        val compileJvm = project.tasks.getByName("compileKotlinJvm") as KotlinCompile
        val compileJs = project.tasks.getByName("compileKotlinJs") as Kotlin2JsCompile
        listOf(compileJvm.compilerOptions, compileJs.compilerOptions).forEach { options ->
            assertThat(options.allWarningsAsErrors.get()).isTrue()
            assertThat(options.freeCompilerArgs.get()).contains("-Xreturn-value-checker=full", "-Xreport-all-warnings")
        }
    }

    @Test
    fun `should lock warnings as errors on the extension and refuse a compile task without them`() {
        val project = mppProject()

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        assertThatThrownBy { kotlin.compilerOptions.allWarningsAsErrors.set(false) }
            .hasMessageContaining("cannot be changed")
        listOf("compileKotlinJvm", "compileKotlinJs").forEach { name ->
            val compile = project.tasks.getByName(name) as org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>
            compile.compilerOptions.allWarningsAsErrors.set(false)
            assertThatThrownBy { compile.actions.forEach { it.execute(compile) } }
                .hasMessageContaining("allWarningsAsErrors = false")
        }
    }

    @Test
    // The six task names Kotlin/JS 2.4 registers: this test is meant to break on a Kotlin Gradle plugin upgrade
    // that adds, renames or removes one, so that the refusal is checked to reach the new set.
    fun `should check the sources of every Kotlin JS compile and link task, breaking on a KGP upgrade`() {
        val names = mppProject().tasks.withType(Kotlin2JsCompile::class.java).names

        assertThat(names).containsExactlyInAnyOrder(
            "compileKotlinJs",
            "compileTestKotlinJs",
            "compileDevelopmentLibraryKotlinJs",
            "compileProductionLibraryKotlinJs",
            "compileTestDevelopmentExecutableKotlinJs",
            "compileTestProductionExecutableKotlinJs",
        )
    }

    @Test
    fun `should refuse a JS compilation whose sources use JsExport Ignore`() {
        val project = mppProject()
        val source = project.file("src/jsMain/kotlin/Order.kt")
        source.parentFile.mkdirs()
        source.writeText("@JsExport\nclass Order {\n    @JsExport.Ignore\n    fun hidden() = Unit\n}\n")

        val compile = project.tasks.getByName("compileKotlinJs") as Kotlin2JsCompile
        assertThatThrownBy { compile.actions.forEach { it.execute(compile) } }
            .hasMessageContaining(":mpp-lib:compileKotlinJs: @JsExport.Ignore leaves JS a Kotlin API")
            .hasMessageContaining("${source.path}:3: @JsExport.Ignore")
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
