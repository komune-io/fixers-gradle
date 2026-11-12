package io.komune.fixers.gradle.integration.kotlin

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Every compilation fails on an ignored return value, unless a repository exempts its tests.
 */
class ReturnValueCheckerIntegrationTest : BaseIntegrationTest() {

    private val unusedReturnValue = "Unused return value of 'answer'"

    private fun writeBuild(plugin: String, testsChecked: Boolean?, kapt: Boolean = false) {
        val kaptPlugin = if (kapt) """id("org.jetbrains.kotlin.kapt")""" else ""
        val strictCompilation = testsChecked?.let { checked ->
            "strictCompilation {\n        returnValueCheckerInTests = $checked\n    }"
        }.orEmpty()
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.$plugin")
                $kaptPlugin
            }

            repositories {
                mavenCentral()
            }

            fixers {
                $strictCompilation
            }
        """.trimIndent())
        // The JDK running the tests, which the build finds without a download. A project property:
        // the Kotlin plugin reads it when it applies, before the fixers block.
        propertiesFile.appendText("\nfixers.jdk.version=${Runtime.version().feature()}\n")
    }

    private fun writeAnswer(sourceSet: String) {
        createFile(relativePath = "src/$sourceSet/kotlin/com/example/Answer.kt", content = """
            package com.example

            fun answer(): Int = 42

        """.trimIndent())
    }

    private fun writeIgnoredCall(sourceSet: String) {
        createFile(relativePath = "src/$sourceSet/kotlin/com/example/Effect.kt", content = """
            package com.example

            fun callForEffect() {
                answer()
            }

        """.trimIndent())
    }

    @Test
    fun `should fail a jvm test that ignores a return value by default`() {
        writeBuild(plugin = "jvm", testsChecked = null)
        writeAnswer(sourceSet = "main")
        writeIgnoredCall(sourceSet = "test")

        val result = runGradleAndFail("compileTestKotlin")

        assertThat(result.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":compileTestKotlin")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains(unusedReturnValue)
    }

    @Test
    fun `should compile a jvm test that ignores a return value once the repository exempts its tests`() {
        writeBuild(plugin = "jvm", testsChecked = false)
        writeAnswer(sourceSet = "main")
        writeIgnoredCall(sourceSet = "test")

        val result = runGradle("compileTestKotlin")

        assertThat(result.task(":compileTestKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `should still fail jvm main code that ignores a return value when the tests are exempt`() {
        writeBuild(plugin = "jvm", testsChecked = false)
        writeAnswer(sourceSet = "main")
        writeIgnoredCall(sourceSet = "main")

        val result = runGradleAndFail("compileKotlin")

        assertThat(result.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains(unusedReturnValue)
    }

    @Test
    fun `should fail the jvm and js tests that ignore a return value by default`() {
        writeBuild(plugin = "mpp", testsChecked = null)
        writeAnswer(sourceSet = "commonMain")
        writeIgnoredCall(sourceSet = "commonTest")

        val result = runGradleAndFail("compileTestKotlinJvm", "compileTestKotlinJs", "--continue")

        assertThat(result.task(":compileTestKotlinJvm")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.task(":compileTestKotlinJs")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output.windowed(unusedReturnValue.length).count { it == unusedReturnValue })
            .describedAs("one report per compilation")
            .isEqualTo(2)
    }

    @Test
    fun `should compile the jvm and js tests that ignore a return value once the repository exempts its tests`() {
        writeBuild(plugin = "mpp", testsChecked = false)
        writeAnswer(sourceSet = "commonMain")
        writeIgnoredCall(sourceSet = "commonTest")

        val result = runGradle("compileTestKotlinJvm", "compileTestKotlinJs")

        assertThat(result.task(":compileTestKotlinJvm")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":compileTestKotlinJs")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `should still fail multiplatform main code that ignores a return value when the tests are exempt`() {
        writeBuild(plugin = "mpp", testsChecked = false)
        writeAnswer(sourceSet = "commonMain")
        writeIgnoredCall(sourceSet = "commonMain")

        val result = runGradleAndFail(
            "compileKotlinJvm",
            "compileKotlinJs",
            "compileCommonMainKotlinMetadata",
            "--continue",
        )

        assertThat(result.task(":compileKotlinJvm")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.task(":compileKotlinJs")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.task(":compileCommonMainKotlinMetadata")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output.windowed(unusedReturnValue.length).count { it == unusedReturnValue })
            .describedAs("one report per compilation")
            .isEqualTo(3)
    }

    @Test
    fun `should give the kapt stubs the value of their compilation`() {
        writeBuild(plugin = "jvm", testsChecked = false, kapt = true)
        buildFile.appendText(
            "\n" + """
                dependencies {
                    kapt("com.google.auto.service:auto-service:1.1.1")
                    kaptTest("com.google.auto.service:auto-service:1.1.1")
                }

                tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask>().configureEach {
                    val args = compilerOptions.freeCompilerArgs
                    doFirst { println("${'$'}name: ${'$'}{args.get().filter { it.startsWith("-Xreturn") }}") }
                }
            """.trimIndent()
        )
        writeAnswer(sourceSet = "main")
        writeIgnoredCall(sourceSet = "test")

        val result = runGradle("compileTestKotlin")

        assertThat(result.task(":kaptGenerateStubsKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":kaptGenerateStubsTestKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":compileTestKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output)
            .contains("kaptGenerateStubsKotlin: [-Xreturn-value-checker=full]")
            .contains("kaptGenerateStubsTestKotlin: [-Xreturn-value-checker=disable]")
    }

    @Test
    fun `should exempt the tests of a subproject when the root decides it`() {
        settingsFile.appendText("\n" + """include("sub")""")
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
            }

            fixers {
                strictCompilation {
                    returnValueCheckerInTests = false
                }
            }
        """.trimIndent())
        propertiesFile.appendText("\nfixers.jdk.version=${Runtime.version().feature()}\n")
        createFile(relativePath = "sub/build.gradle.kts", content = """
            plugins {
                id("io.komune.fixers.gradle.kotlin.jvm")
            }

            repositories {
                mavenCentral()
            }

        """.trimIndent())
        createFile(relativePath = "sub/src/main/kotlin/com/example/Answer.kt", content = """
            package com.example

            fun answer(): Int = 42

        """.trimIndent())
        createFile(relativePath = "sub/src/test/kotlin/com/example/Effect.kt", content = """
            package com.example

            fun callForEffect() {
                answer()
            }

        """.trimIndent())

        val tests = runGradle(":sub:compileTestKotlin")
        assertThat(tests.task(":sub:compileTestKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        createFile(relativePath = "sub/src/main/kotlin/com/example/Effect.kt", content = """
            package com.example

            fun mainCallForEffect() {
                answer()
            }

        """.trimIndent())
        val main = runGradleAndFail(":sub:compileKotlin")
        assertThat(main.task(":sub:compileKotlin")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(main.output).contains(unusedReturnValue)
    }
}
