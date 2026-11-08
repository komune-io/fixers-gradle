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

    private fun Project.run(vararg taskNames: String) = taskNames.forEach { name ->
        val task = tasks.getByName(name)
        task.actions.forEach { it.execute(task) }
    }

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

        @Test
        fun `tsGen should wait for the Kotlin JS library distribution of every project`() {
            val root = ProjectBuilder.builder().build()
            val module = ProjectBuilder.builder().withName("module").withParent(root).build()
            val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)

            root.configureKt2Ts(config)
            val distribution = module.tasks.register("jsBrowserProductionLibraryDistribution").get()

            val tsGen = root.tasks.getByName("tsGen")
            assertThat(tsGen.taskDependencies.getDependencies(tsGen)).contains(distribution)
            val cleanTsGen = root.tasks.getByName("cleanTsGen")
            assertThat(cleanTsGen.mustRunAfter.getDependencies(cleanTsGen)).contains(distribution)
        }

        @Test
        fun `tsGen should clean the copy and leave the Kotlin output untouched`(@TempDir dir: File) {
            val input = File(dir, "packages/pkg/kotlin").apply { mkdirs() }
            val raw = "export declare function names(): KtList<string>;\n"
            File(input, "pkg.d.mts").writeText(raw)
            val output = File(dir, "out")
            val (project, config) = projectWithConfig()
            config.kt2Ts.inputDirectory.set(File(dir, "packages").absolutePath)
            config.kt2Ts.outputDirectory.set(output.absolutePath)

            project.configureKt2Ts(config)
            project.run("cleanTsGen", "tsGen")

            assertThat(File(input, "pkg.d.mts").readText()).isEqualTo(raw)
            assertThat(File(output, "pkg/kotlin/pkg.d.mts").readText())
                .isEqualTo("export declare function names(): string[];\n")
        }

        @Test
        fun `tsGen should not rewrite the npm packages of subprojects`(@TempDir dir: File) {
            val root = ProjectBuilder.builder().withProjectDir(dir).build()
            val module = ProjectBuilder.builder().withName("module").withParent(root).build()
            val npmPackage = module.layout.buildDirectory.dir("packages/js").get().asFile.apply { mkdirs() }
            val raw = "export declare function names(): KtList<string>;\n"
            File(npmPackage, "module.d.mts").writeText(raw)
            val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
            config.kt2Ts.inputDirectory.set(File(dir, "empty").absolutePath)
            config.kt2Ts.outputDirectory.set(File(dir, "out").absolutePath)

            root.configureKt2Ts(config)
            root.run("cleanTsGen", "tsGen")

            assertThat(File(npmPackage, "module.d.mts").readText()).isEqualTo(raw)
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

            assertThat(result).contains("readonly label?: string | null;")
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

    /**
     * `kotlin-2.4.21.d.mts` is the unmodified output of Kotlin/JS 2.4.21 (ES modules, `es2015`,
     * `-Xes-long-as-bigint`, production library) for a sample of DTOs, enums and functions;
     * the expected files are what the rules make of it.
     */
    @Nested
    inner class Kotlin2421DeclarationsTest {

        @TempDir
        lateinit var dir: File

        private fun clean(): String {
            val file = File(dir, "api.d.mts")
            file.writeText(resource("kotlin-2.4.21.d.mts"))
            val kt2Ts = Kt2Ts(ProjectBuilder.builder().build())
            cleanProjectDir(dir.absolutePath, kt2Ts.buildCleaningRegex())
            return file.readText()
        }

        private fun resource(name: String): String =
            javaClass.getResource("/kt2ts/$name")!!.readText()

        @Test
        fun `should match the expected declarations`() {
            assertThat(clean()).isEqualTo(resource("kotlin-2.4.21.expected.d.mts"))
        }

        @Test
        fun `should drop the collection interop declared with variance`() {
            val result = clean()

            assertThat(result).doesNotContain("interface out", "Record<K, out V>", "KtList", "KtMap", "KtSet")
        }

        @Test
        fun `should collapse a Set like a List`() {
            assertThat(clean()).contains("get setS(): string[];")
        }

        @Test
        fun `should leave no Nullable outside comments`() {
            val result = clean()

            assertThat(Regex("""(?<!/\* )Nullable<""").findAll(result).toList()).isEmpty()
            assertThat(result).contains("get onChange(): ((p0: string) => void) | null;")
            assertThat(result).contains("mapSDtoNull: Record<string, (NestedDto | null)>")
        }

        @Test
        fun `should keep a nullable constructor parameter required`() {
            assertThat(clean()).contains("constructor(s: string, sNull: string | null, i: number,")
        }

        private fun cleanSnippet(content: String): String {
            val file = File(dir, "snippet.d.mts")
            file.writeText(content)
            cleanProjectDir(dir.absolutePath, Kt2Ts(ProjectBuilder.builder().build()).buildCleaningRegex())
            return file.readText()
        }

        @Test
        fun `should strip the marker of an interface with a two parameter parent and keep what follows`() {
            val result = cleanSnippet(
                "export declare interface Dto extends Two<string, number> {\n"
                    + "    readonly __doNotUseOrImplementIt: {\n"
                    + "        readonly \"exp.Dto\": unique symbol;\n"
                    + "    } & Two<any, any>[\"__doNotUseOrImplementIt\"];\n"
                    + "}\n"
                    + "export declare interface Keep {\n    readonly id: string;\n}\n"
            )

            assertThat(result).isEqualTo(
                "export declare interface Dto extends Two<string, number> {\n}\n"
                    + "export declare interface Keep {\n    readonly id: string;\n}\n"
            )
        }

        @Test
        fun `should keep the parameter of a nullable setter required`() {
            assertThat(cleanSnippet("    set name(value: Nullable<string>);\n"))
                .isEqualTo("    set name(value: string | null);\n")
        }

        @Test
        fun `should collapse a list of functions`() {
            assertThat(cleanSnippet("    get cbs(): KtList<(p0: string) => void>;\n"))
                .isEqualTo("    get cbs(): ((p0: string) => void)[];\n")
        }

        @Test
        fun `should collapse three nested lists`() {
            assertThat(cleanSnippet("    get cube(): KtList<KtList<KtSet<string>>>;\n"))
                .isEqualTo("    get cube(): string[][][];\n")
        }

        @Test
        fun `should map every Long to number`() {
            assertThat(cleanSnippet("    get ids(): KtList<Nullable<bigint>>;\n    readonly bigint: bigint;\n"))
                .isEqualTo("    get ids(): (number | null)[];\n    readonly bigint: number;\n")
        }

        @Test
        fun `should keep the declaration of an enum that declares a function`() {
            val result = cleanSnippet(
                "export declare abstract class Mode {\n"
                    + "    private constructor();\n"
                    + "    static get ON(): Mode & {\n        get name(): \"ON\";\n        get ordinal(): 0;\n    };\n"
                    + "    static values(): [typeof Mode.ON];\n"
                    + "    static valueOf(value: string): Mode;\n"
                    + "    get name(): \"ON\";\n"
                    + "    get ordinal(): 0;\n"
                    + "    describe(): string;\n"
                    + "}\n"
            )

            assertThat(result).contains("export declare abstract class Mode {", "static valueOf(value: string): Mode;", "describe(): string;")
            assertThat(result).doesNotContain("export type Mode")
        }

        @Test
        fun `should keep trailing nullable parameters optional`() {
            val file = File(dir, "fn.d.mts")
            file.writeText("export declare function f(a: Nullable<string>, b: string, c: Nullable<KtMap<string, number>>, d: Nullable<string>): void;\n")
            cleanProjectDir(dir.absolutePath, Kt2Ts(ProjectBuilder.builder().build()).buildCleaningRegex())

            assertThat(file.readText())
                .isEqualTo("export declare function f(a: string | null, b: string, c?: Record<string, number> | null, d?: string | null): void;\n")
        }

        @Test
        fun `should map a Long inside a generic type`() {
            assertThat(clean()).contains("sumLongs(m: Record<string, number>): number;")
        }

        @Test
        fun `should keep the exported values of enums that implement an interface or carry members`() {
            val result = clean()

            // The .mjs still exports these classes: their entries and valueOf stay declared.
            assertThat(result).contains(
                "export declare abstract class XState implements S2State {",
                "    static get CREATED(): XState & {",
                "    static valueOf(value: string): XState;",
                "    get position(): number;",
                "export declare abstract class ProductStatus implements S2State {",
                "    static valueOf(value: string): ProductStatus;",
                "    get label(): string;",
            )
            assertThat(result).doesNotContain("export type XState", "export type ProductStatus")
        }

        @Test
        fun `should still collapse an enum without members`() {
            val result = cleanSnippet(
                "export declare abstract class Mode {\n"
                    + "    private constructor();\n"
                    + "    static get ON(): Mode & {\n        get name(): \"ON\";\n        get ordinal(): 0;\n    };\n"
                    + "    static values(): [typeof Mode.ON];\n"
                    + "    static valueOf(value: string): Mode;\n"
                    + "    get name(): \"ON\";\n"
                    + "    get ordinal(): 0;\n"
                    + "}\n"
            )

            assertThat(result).isEqualTo("export type Mode = \"ON\";\n")
        }

        @Test
        fun `should type nullable values as null`() {
            val result = clean()

            assertThat(result).contains("readonly name?: string | null;")
            assertThat(result).contains("get sNull(): string | null;")
            assertThat(result).contains("listSNull: (string | null)[]")
            assertThat(result).contains("sNull: string | null, i: number")
            assertThat(result).doesNotContain("| undefined")
        }
    }

    @Nested
    inner class NullabilityTest {

        @TempDir
        lateinit var dir: File

        private fun clean(content: String): String {
            val file = File(dir, "api.d.mts")
            file.writeText(content)
            val kt2Ts = Kt2Ts(ProjectBuilder.builder().build())
            cleanProjectDir(dir.absolutePath, kt2Ts.buildCleaningRegex())
            return file.readText()
        }

        @Test
        fun `should keep the comment of a non exported nullable type before null`() {
            val result = clean("export declare interface A {\n    readonly item: Nullable<any>/* Nullable<Graph> */;\n}\n")

            assertThat(result).contains("readonly item?: any/* Nullable<Graph> */ | null;")
        }
    }
}
