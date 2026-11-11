package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.buildFixersRoot
import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.Kt2Ts
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
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
        val project = ProjectBuilder.builder().buildFixersRoot()
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
            val root = ProjectBuilder.builder().buildFixersRoot()
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
            assertThat(File(output, "pkg/kotlin/pkg.d.mts").readText()).isEqualTo(JSON_DECLARATIONS + raw)
        }

        @Test
        fun `tsGen should not rewrite the npm packages of subprojects`(@TempDir dir: File) {
            val root = ProjectBuilder.builder().withProjectDir(dir).buildFixersRoot()
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

        private fun kt2Ts(): Kt2Ts = Kt2Ts(ProjectBuilder.builder().buildFixersRoot())

        @Test
        fun `should provide cleaning rules for dmts and package json only`() {
            val cleaning = kt2Ts().buildCleaningRegex()

            assertThat(cleaning.keys).containsExactlyInAnyOrder(".d.mts", "package.json")
            assertThat(cleaning[".d.mts"]).isNotEmpty
        }

        @Test
        fun `should append additional cleaning rules to existing suffix`() {
            val kt2Ts = kt2Ts()
            kt2Ts.additionalCleaning.set(mapOf(".d.mts" to listOf(Regex("FOO") to "BAR")))

            val cleaning = kt2Ts.buildCleaningRegex()

            assertThat(cleaning[".d.mts"]!!.map { it.first.pattern }).contains("FOO")
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
    inner class PinnedDependencyCleaningTest {

        @Test
        fun `should rewrite the packages pinned at any depth only`() {
            val rules = pinnedDependencyCleaning(
                mapOf("**/ws" to "8.22.2", "**/@scope/pkg" to "1.0.0", "**/mocha/glob" to "14.0.4", "@other/x" to "2.0.0")
            )
            val json = """{"ws":"8.20.1", "@scope/pkg": "^0.9.0", "glob": "10.4.5", "@other/x": "1.0.0"}"""

            assertThat(rules.fold(json) { acc, (regex, replacement) -> acc.replace(regex, replacement) })
                .isEqualTo("""{"ws":"8.22.2", "@scope/pkg": "1.0.0", "glob": "10.4.5", "@other/x": "1.0.0"}""")
        }
    }

    @Nested
    inner class CleanProjectDirTest {

        @TempDir
        lateinit var dir: File

        private fun clean(fileName: String, content: String): String {
            val file = File(dir, fileName)
            file.writeText(content)
            val cleaning = Kt2Ts(ProjectBuilder.builder().buildFixersRoot()).buildCleaningRegex()
            cleanProjectDir(dir.absolutePath, cleaning)
            return file.readText()
        }

        @Test
        fun `should leave a legacy dts file untouched`() {
            val content = "readonly items: kotlin.collections.List<string>;\nvalue: kotlin.Long;\n"

            assertThat(clean("api.d.ts", content)).isEqualTo(content)
        }

        @Test
        fun `should keep KtList KtMap and bigint in dmts files`() {
            val body = "items: KtList<string>;\nprops: KtMap<string, string>;\ncount: bigint;\n"

            assertThat(clean("api.d.mts", body)).isEqualTo(JSON_DECLARATIONS + body)
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
        fun `should declare a dependency fixers pins at its pin in package json`() {
            val result = clean(
                "package.json",
                """{"name": "pkg", "devDependencies": {}, "dependencies": {"ws": "8.20.1", "@js-joda/core": "3.2.0"}}"""
            )

            assertThat(result).contains(""""ws": "${FIXERS_YARN_PINS.getValue("**/ws")}"""")
            assertThat(result).contains(""""@js-joda/core": "3.2.0"""")
        }

        @Test
        fun `should leave a package that only shares a pinned name's suffix`() {
            val content = """{"name": "pkg", "devDependencies": {}, "dependencies": {"isows": "1.0.7"}}"""

            assertThat(clean("package.json", content)).isEqualTo(content)
        }

        @Test
        fun `should leave unmatched files untouched`() {
            val content = "kotlin.Long should stay"
            val result = clean("readme.txt", content)

            assertThat(result).isEqualTo(content)
        }

        @Test
        fun `should tolerate missing directories`() {
            val cleaning = Kt2Ts(ProjectBuilder.builder().buildFixersRoot()).buildCleaningRegex()

            cleanProjectDir(File(dir, "does-not-exist").absolutePath, cleaning)
        }
    }

    /**
     * `kotlin-2.4.21.d.mts`, `kotlin-2.4.21-mutable.d.mts` and `kotlin-2.4.21-families.d.mts` are the
     * output of Kotlin/JS 2.4.21 (ES modules, `es2015`, `-Xes-long-as-bigint`, production library):
     * a sample of DTOs, enums and functions, the mutable collections, and the sandbox's
     * `object-domain` (one declaration per family whose Kotlin object and JSON differ). The exported
     * types describe the Kotlin objects, so the rules leave every family as the emitter wrote it,
     * add `Json` and `KtOpaque`, and type `any` as `unknown`.
     */
    @Nested
    inner class Kotlin2421DeclarationsTest {

        @TempDir
        lateinit var dir: File

        private fun resource(name: String): String =
            javaClass.getResource("/kt2ts/$name")!!.readText()

        private fun cleanText(content: String, name: String = "api.d.mts"): String {
            val file = File(dir, name)
            file.writeText(content)
            cleanProjectDir(dir.absolutePath, Kt2Ts(ProjectBuilder.builder().buildFixersRoot()).buildCleaningRegex())
            return file.readText()
        }

        private fun clean(resourceName: String = "kotlin-2.4.21.d.mts"): String = cleanText(resource(resourceName))

        @Test
        fun `should match the expected declarations`() {
            assertThat(clean()).isEqualTo(resource("kotlin-2.4.21.expected.d.mts"))
        }

        @Test
        fun `should keep the collection interop with its declarations and marker`() {
            assertThat(clean()).contains(
                "export declare interface KtList<out E> /* extends Collection<E> */ {\n" +
                    "    asJsReadonlyArrayView(): ReadonlyArray<E>;\n" +
                    "    readonly __doNotUseOrImplementIt: {\n" +
                    "        readonly \"kotlin.collections.KtList\": unique symbol;\n",
                "export declare namespace KtSet {\n    function fromJsSet<E>(set: ReadonlySet<E>): KtSet<E>;\n}",
                "export declare interface KtMap<K, out V> {",
            )
        }

        @Test
        fun `should keep a Kotlin collection in a constructor parameter`() {
            assertThat(clean()).contains(
                "constructor(s: string, sNull: Nullable<string>, i: number, iNull: Nullable<number>, d: number, " +
                    "bNull: Nullable<boolean>, listSNull: KtList<Nullable<string>>,"
            )
        }

        @Test
        fun `should keep a Kotlin collection in a copy parameter`() {
            assertThat(clean()).contains("copy(s?: string, sNull?: Nullable<string>,", "mapSS?: KtMap<string, string>,", "setS?: KtSet<string>,")
        }

        @Test
        fun `should keep a Kotlin collection in a function parameter`() {
            assertThat(clean()).contains("export declare function sumLongs(m: KtMap<string, bigint>): bigint;")
        }

        @Test
        fun `should keep a Kotlin collection in a return type`() {
            assertThat(clean()).contains("export declare function listDtos(): KtList<NestedDto>;")
        }

        @Test
        fun `should keep a Kotlin collection in a class getter`() {
            assertThat(clean()).contains("    get setS(): KtSet<string>;", "    get listDtoNull(): Nullable<KtList<NestedDto>>;")
        }

        @Test
        fun `should keep a Kotlin collection in an interface property`() {
            assertThat(clean("kotlin-2.4.21-families.d.mts")).contains(
                "    readonly tags: KtList<string>;",
                "    readonly owners: KtSet<string>;",
                "    readonly counts: KtMap<string, KtList<bigint>>;",
            )
        }

        @Test
        fun `should keep the mutable collections and a nullable map key`() {
            assertThat(clean("kotlin-2.4.21-mutable.d.mts")).contains(
                "export declare interface KtMutableList<E> extends KtList<E>/*, MutableCollection<E> */ {",
                "    get ml(): KtMutableList<string>;",
                "    get mm(): KtMutableMap<string, number>;",
                "    get nullKey(): KtMap<Nullable<string>, number>;",
            )
        }

        @Test
        fun `should keep a Long a bigint`() {
            val result = clean()

            assertThat(result).contains("    get l(): bigint;", "    get lNull(): Nullable<bigint>;", "    get lList(): KtList<bigint>;")
            assertThat(clean("kotlin-2.4.21-families.d.mts")).contains("    readonly total: bigint;")
        }

        @Test
        fun `should keep a simple enum the class the emitter declares`() {
            val result = clean("kotlin-2.4.21-families.d.mts")

            assertThat(result).contains(
                "export declare abstract class Shape {\n    private constructor();\n    static get ROUND(): Shape & {",
                "    get name(): \"ROUND\" | \"SQUARE\";",
                "    readonly shape: Shape;",
            )
            assertThat(result).doesNotContain("export type Shape")
        }

        @Test
        fun `should keep an enum with members the class the emitter declares`() {
            assertThat(clean("kotlin-2.4.21-families.d.mts"))
                .contains("export declare abstract class Level {", "    heavierThan(other: Level): boolean;", "    readonly level: Level;")
            assertThat(clean()).contains("export declare abstract class XState implements S2State {")
        }

        @Test
        fun `should type an Instant and a LocalDate as a named opaque value`() {
            assertThat(clean("kotlin-2.4.21-families.d.mts")).contains(
                "    readonly createdAt: KtOpaque<\"Instant\">;",
                "    get createdAt(): KtOpaque<\"Instant\">;",
                "createdAt: KtOpaque<\"Instant\">, note: Nullable<string>);",
            )
            assertThat(cleanText("    get day(): any/* LocalDate */;\n    get at(): Nullable<any>/* Nullable<Instant> */;\n"))
                .isEqualTo(JSON_DECLARATIONS + "    get day(): KtOpaque<\"LocalDate\">;\n    get at(): Nullable<KtOpaque<\"Instant\">>;\n")
        }

        @Test
        fun `should keep Nullable as the emitter writes it`() {
            val result = clean()

            assertThat(result).startsWith(JSON_DECLARATIONS + "type Nullable<T> = T | null | undefined\n")
            assertThat(result).contains(
                "    get sNull(): Nullable<string>;",
                "    get onChange(): Nullable<(p0: string) => void>;",
                "    get mapSDtoNull(): KtMap<string, Nullable<NestedDto>>;",
                "    readonly name: Nullable<string>;",
            )
            assertThat(cleanText("    set name(value: Nullable<string>);\n"))
                .isEqualTo(JSON_DECLARATIONS + "    set name(value: Nullable<string>);\n")
        }

        @Test
        fun `should keep the marker of interfaces and classes`() {
            assertThat(clean("kotlin-2.4.21-mutable.d.mts")).contains(
                "    readonly __doNotUseOrImplementIt: {\n        readonly \"exp.Base1\": unique symbol;\n    };",
                "    readonly __doNotUseOrImplementIt: Base2[\"__doNotUseOrImplementIt\"] & Base1[\"__doNotUseOrImplementIt\"];",
                "    } & KtList<unknown>[\"__doNotUseOrImplementIt\"];",
            )
        }

        @Test
        fun `should accept a collection nested at any depth`() {
            val body = "export declare interface Deep {\n    readonly cube: KtList<KtList<KtList<KtList<string>>>>;\n}\n"

            assertThat(cleanText(body, "deep.d.mts")).isEqualTo(JSON_DECLARATIONS + body)
        }

        @Test
        fun `should write Json and KtOpaque once at the head of every declarations file`() {
            val once = cleanText("export declare function names(): KtList<string>;\n")
            val file = File(dir, "api.d.mts")

            cleanProjectDir(dir.absolutePath, Kt2Ts(ProjectBuilder.builder().buildFixersRoot()).buildCleaningRegex())

            assertThat(file.readText()).isEqualTo(once)
            assertThat(Regex("""^export type Json<T> =""", RegexOption.MULTILINE).findAll(once).count()).isEqualTo(1)
            assertThat(Regex("""^export declare interface KtOpaque<""", RegexOption.MULTILINE).findAll(once).count()).isEqualTo(1)
        }

        @Test
        fun `should type a Kotlin Any as unknown where a type goes, outside comments and string literals`() {
            val result = cleanText(
                "declare function KtSingleton<T>(): T & (abstract new() => any);\n" +
                    "/** valid in any module */\n" +
                    "export declare function f(other?: any): any;\n" +
                    "export declare interface WithId<ID extends any> {\n    get name(): \"any\";\n}\n" +
                    "export declare interface Names {\n    readonly any: string;\n    any(): boolean;\n    of(x: Set<any>, y: string | any): any[];\n}\n",
                "any.d.mts",
            )

            assertThat(result).isEqualTo(
                JSON_DECLARATIONS +
                    "declare function KtSingleton<T>(): T & (abstract new() => object);\n" +
                    "/** valid in any module */\n" +
                    "export declare function f(other?: unknown): unknown;\n" +
                    "export declare interface WithId<ID extends unknown> {\n    get name(): \"any\";\n}\n" +
                    "export declare interface Names {\n    readonly any: string;\n    any(): boolean;\n    of(x: Set<unknown>, y: string | unknown): unknown[];\n}\n"
            )
        }

        @Test
        fun `should give a companion's singleton an object instance, which a class can extend`() {
            assertThat(clean("kotlin-2.4.21-families.d.mts")).contains(
                "declare function KtSingleton<T>(): T & (abstract new() => object);",
                "    abstract class Companion extends KtSingleton<Companion.${'$'}metadata${'$'}.constructor>() {",
            )
        }

        @Test
        fun `should fail on a type the export left as any`() {
            val file = File(dir, "left.d.mts")
            file.writeText("export declare interface Left {\n    readonly x: any/* Foo.Companion */;\n}\n")
            val cleaning = Kt2Ts(ProjectBuilder.builder().buildFixersRoot()).buildCleaningRegex()
            val line = JSON_DECLARATIONS.lines().size + 1

            assertThatThrownBy { cleanProjectDir(dir.absolutePath, cleaning) }
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining("left.d.mts:$line")
                .hasMessageContaining("any/* Foo.Companion */")
        }
    }
}
