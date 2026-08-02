package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.Kt2Ts
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for the kt2Ts task registration and TypeScript cleaning rules.
 */
class TsGenTaskTest {

    private fun projectWithConfig(): Pair<Project, ConfigExtension> {
        val project = ProjectBuilder.builder().build()
        val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)
        return project to config
    }

    @Nested
    inner class ConfigureKt2TsTest {

        @Test
        fun `should register cleanTsGen and tsGen tasks`() {
            val (project, config) = projectWithConfig()

            project.configureKt2Ts(config)

            assertThat(project.tasks.findByName("cleanTsGen")).isNotNull
            val tsGen = project.tasks.findByName("tsGen")
            assertThat(tsGen).isNotNull
            assertThat(tsGen!!.dependsOn).contains("cleanTsGen")
        }

        @Test
        fun `should use explicit input directory when configured`(@TempDir dir: File) {
            val (project, config) = projectWithConfig()
            config.kt2Ts.inputDirectory.set(dir.absolutePath)

            project.configureKt2Ts(config)

            assertThat(project.tasks.findByName("tsGen")).isNotNull
        }

        @Test
        fun `should register no tasks when config is null`() {
            val (project, _) = projectWithConfig()

            project.configureKt2Ts(null)

            assertThat(project.tasks.findByName("tsGen")).isNull()
            assertThat(project.tasks.findByName("cleanTsGen")).isNull()
        }
    }

    @Nested
    inner class BuildCleaningRegexTest {

        private fun kt2Ts(): Kt2Ts = Kt2Ts(ProjectBuilder.builder().build())

        @Test
        fun `should provide cleaning rules for dts dmts and package json`() {
            val cleaning = kt2Ts().buildCleaningRegex()

            assertThat(cleaning.keys).contains(".d.ts", ".d.mts", "package.json")
            assertThat(cleaning[".d.ts"]).isNotEmpty
            assertThat(cleaning[".d.mts"]).isNotEmpty
        }

        @Test
        fun `should append additional cleaning rules to existing suffix`() {
            val kt2Ts = kt2Ts()
            kt2Ts.additionalCleaning.set(mapOf(".d.ts" to listOf(Regex("FOO") to "BAR")))

            val cleaning = kt2Ts.buildCleaningRegex()

            assertThat(cleaning[".d.ts"]!!.map { it.first.pattern }).contains("FOO")
        }

        @Test
        fun `should add additional cleaning rules for new suffixes`() {
            val kt2Ts = kt2Ts()
            kt2Ts.additionalCleaning.set(mapOf(".custom" to listOf(Regex("A") to "B")))

            val cleaning = kt2Ts.buildCleaningRegex()

            assertThat(cleaning).containsKey(".custom")
        }
    }

    @Nested
    inner class CleanProjectDirTest {

        @TempDir
        lateinit var dir: File

        private fun clean(fileName: String, content: String): String {
            val file = File(dir, fileName)
            file.writeText(content)
            val cleaning = Kt2Ts(ProjectBuilder.builder().build()).buildCleaningRegex()
            cleanProjectDir(dir.absolutePath, cleaning)
            return file.readText()
        }

        @Test
        fun `should strip kotlin namespaces in dts files`() {
            val result = clean(
                "api.d.ts",
                "type A = kotlin.js.PromiseLike;\nreadonly items: kotlin.collections.List<string>;\n"
            )

            assertThat(result).contains("type A = PromiseLike;")
            assertThat(result).contains("readonly items: string[];")
        }

        @Test
        fun `should map kotlin collections and numbers in dts files`() {
            val result = clean(
                "model.d.ts",
                "value: kotlin.Long;\nprops: kotlin.collections.Map<string, string>;\n"
            )

            assertThat(result).contains("value: number;")
            assertThat(result).contains("props: Record<string, string>;")
        }

        @Test
        fun `should remove doNotImplementIt markers in dts files`() {
            val result = clean(
                "marker.d.ts",
                "interface A {\n    readonly __doNotUseOrImplementIt: unique symbol;\n    name: string;\n}\n"
            )

            assertThat(result).doesNotContain("__doNotUseOrImplementIt")
            assertThat(result).contains("name: string;")
        }

        @Test
        fun `should clean KtList KtMap and bigint in dmts files`() {
            val result = clean(
                "api.d.mts",
                "items: KtList<string>;\nprops: KtMap<string, string>;\ncount: bigint;\n"
            )

            assertThat(result).contains("items: string[];")
            assertThat(result).contains("props: Record<string, string>;")
            assertThat(result).contains("count: number;")
        }

        @Test
        fun `should rewrite nullable properties in dmts files`() {
            val result = clean(
                "nullable.d.mts",
                "interface A {\n    readonly label: Nullable<string>;\n}\n"
            )

            assertThat(result).contains("readonly label?: string;")
        }

        @Test
        fun `should empty devDependencies in package json`() {
            val result = clean(
                "package.json",
                """{"name": "pkg", "devDependencies": {"typescript": "^5.0.0"}, "version": "1.0.0"}"""
            )

            assertThat(result).contains(""""devDependencies": {},""")
            assertThat(result).doesNotContain("typescript")
        }

        @Test
        fun `should leave unmatched files untouched`() {
            val content = "kotlin.Long should stay"
            val result = clean("readme.txt", content)

            assertThat(result).isEqualTo(content)
        }

        @Test
        fun `should tolerate missing directories`() {
            val cleaning = Kt2Ts(ProjectBuilder.builder().build()).buildCleaningRegex()

            cleanProjectDir(File(dir, "does-not-exist").absolutePath, cleaning)
        }
    }
}
