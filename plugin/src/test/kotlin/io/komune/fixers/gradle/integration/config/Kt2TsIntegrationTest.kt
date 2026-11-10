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
        assertThat(testProjectDir.resolve("contract/pkg/kotlin/pkg.d.mts").toFile())
            .hasContent("export declare function names(): string[];")
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
}
