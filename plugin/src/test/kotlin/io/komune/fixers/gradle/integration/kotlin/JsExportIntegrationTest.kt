package io.komune.fixers.gradle.integration.kotlin

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * What the JS export cannot express fails the build instead of turning into `any` or into two
 * TypeScript declarations of one name.
 */
class JsExportIntegrationTest : BaseIntegrationTest() {

    private fun writeMppBuild() {
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.mpp")
            }

            repositories {
                mavenCentral()
            }
        """.trimIndent())
    }

    @Test
    fun `should fail on an exported declaration that uses a type the export cannot express`() {
        writeMppBuild()
        createFile(relativePath = "src/commonMain/kotlin/com/example/Order.kt", content = """
            package com.example

            class Hidden

            @kotlin.js.JsExport
            class Order(val hidden: Hidden)
        """.trimIndent())

        val result = runGradleAndFail("compileKotlinJs")

        assertThat(result.task(":compileKotlinJs")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Exported declaration uses non-exportable")
    }

    @Test
    fun `should fail on two exported declarations of one name in two packages`() {
        writeMppBuild()
        createFile(relativePath = "src/commonMain/kotlin/com/example/a/Same.kt", content = """
            package com.example.a

            @kotlin.js.JsExport
            class Same(val a: String)
        """.trimIndent())
        createFile(relativePath = "src/commonMain/kotlin/com/example/b/Same.kt", content = """
            package com.example.b

            @kotlin.js.JsExport
            class Same(val b: String)
        """.trimIndent())

        val result = runGradleAndFail("compileKotlinJs")

        assertThat(result.task(":compileKotlinJs")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("may clash with exporting name 'Same'")
    }

    @Test
    fun `should fail on JsExport Ignore, naming the file and line`() {
        writeMppBuild()
        createFile(relativePath = "src/commonMain/kotlin/com/example/Order.kt", content = """
            package com.example

            class Hidden

            @kotlin.js.JsExport
            class Order(val id: String) {
                @kotlin.js.JsExport.Ignore
                fun hidden(): Hidden = Hidden()
            }
        """.trimIndent())

        val result = runGradleAndFail("compileKotlinJs")

        assertThat(result.task(":compileKotlinJs")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output)
            .contains(":compileKotlinJs: @JsExport.Ignore leaves JS a Kotlin API with its members taken out")
            .contains("Write the JS form in jsMain")
            .contains("src/commonMain/kotlin/com/example/Order.kt:7: @kotlin.js.JsExport.Ignore")
    }

    @Test
    fun `should compile an export once the type is exported, the member kept out and the name changed`() {
        writeMppBuild()
        createFile(relativePath = "src/commonMain/kotlin/com/example/Order.kt", content = """
            package com.example

            class Hidden

            @kotlin.js.JsExport
            class Shown

            /** Its hidden part is no member: not exported, and no @JsExport.Ignore. */
            @kotlin.js.JsExport
            class Order(val shown: Shown)

            fun Order.hidden(): Hidden = Hidden()
        """.trimIndent())
        createFile(relativePath = "src/commonMain/kotlin/com/example/a/Same.kt", content = """
            package com.example.a

            @kotlin.js.JsExport
            class Same(val a: String)
        """.trimIndent())
        createFile(relativePath = "src/commonMain/kotlin/com/example/b/Same.kt", content = """
            package com.example.b

            @kotlin.js.JsExport
            @kotlin.js.JsName("OtherSame")
            class Same(val b: String)
        """.trimIndent())

        val result = runGradle("compileKotlinJs")

        assertThat(result.task(":compileKotlinJs")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
