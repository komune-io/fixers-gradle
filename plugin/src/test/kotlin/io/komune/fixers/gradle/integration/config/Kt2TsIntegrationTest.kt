package io.komune.fixers.gradle.integration.config

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * `tsGen` writes into a committed package that a JS package manager links into: its links must
 * neither break the task nor keep it from being up to date.
 */
class Kt2TsIntegrationTest : BaseIntegrationTest() {

    private fun writeKt2TsBuild() {
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
            }

            fixers {
                kt2Ts {
                    inputDirectory = "emitted"
                    outputDirectory = "contract"
                }
            }
        """.trimIndent())
        createFile(
            relativePath = "emitted/pkg/kotlin/pkg.d.mts",
            content = "export declare function names(): KtList<string>;\n",
        )
    }

    @Test
    fun `tsGen should keep a dangling node_modules link and be up to date on a second run`() {
        writeKt2TsBuild()
        val link = testProjectDir.resolve("contract/pkg/node_modules/gone")
        Files.createDirectories(link.parent)
        Files.createSymbolicLink(link, testProjectDir.resolve("nowhere"))

        val first = runGradle("tsGen")
        val second = runGradle("tsGen")

        assertThat(first.task(":tsGen")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(second.task(":tsGen")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(Files.isSymbolicLink(link)).isTrue()
        assertThat(testProjectDir.resolve("contract/pkg/kotlin/pkg.d.mts").toFile().readText())
            .contains("export type Json<T> =")
            .endsWith("export declare function names(): KtList<string>;\n")
    }

    @Test
    fun `checkTsGen should be up to date on a second run over an unchanged contract`() {
        writeKt2TsBuild()
        runGradle("tsGen")

        val first = runGradle("checkTsGen")
        val second = runGradle("checkTsGen")

        assertThat(first.task(":checkTsGen")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(second.task(":checkTsGen")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `checkTsGen should fail on a Kotlin export named like a helper of Json`() {
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.mpp")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                kt2Ts {
                    outputDirectory = "contract"
                }
            }
        """.trimIndent())
        createFile(relativePath = "src/commonMain/kotlin/com/example/JsonObject.kt", content = """
            package com.example

            @kotlin.js.JsExport
            interface JsonObject {
                val id: String
            }
        """.trimIndent())
        runGradle("tsGen")

        val result = runGradleAndFail("checkTsGen")

        assertThat(result.task(":checkTsGen")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output)
            .contains("declares `JsonObject` twice: type JsonObject, interface JsonObject")
            .contains("Rename one of the two Kotlin declarations")
    }
}
