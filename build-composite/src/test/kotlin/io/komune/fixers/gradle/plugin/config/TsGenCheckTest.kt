package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.buildFixersRoot
import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.model.Kt2TsFiles
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The output directory of `tsGen` is often a committed package that a JS package manager has
 * linked into (`node_modules`), and `checkTsGen` tells whether that committed copy is stale.
 */
class TsGenCheckTest {

    @TempDir
    lateinit var dir: File

    private val input get() = File(dir, "packages")
    private val output get() = File(dir, "out")

    private fun project(
        committedFiles: Kt2TsFiles? = null,
        additionalCleaning: Map<String, List<Pair<Regex, String>>> = emptyMap(),
    ): Project {
        val project = ProjectBuilder.builder().withProjectDir(File(dir, "project")).buildFixersRoot()
        val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)
        config.kt2Ts.inputDirectory.set(input.absolutePath)
        config.kt2Ts.outputDirectory.set(output.absolutePath)
        committedFiles?.let { config.kt2Ts.committedFiles.set(it) }
        config.kt2Ts.additionalCleaning.set(additionalCleaning)
        project.configureKt2Ts(config)
        return project
    }

    /** Runs a task after the tasks of the same project it depends on, as Gradle would. */
    private fun Project.run(name: String) {
        val task = tasks.getByName(name)
        task.taskDependencies.getDependencies(task)
            .filter { it.project == this }
            .forEach { dependency -> dependency.execute() }
        task.execute()
    }

    private fun Task.execute() = actions.forEach { it.execute(this) }

    private fun emit(path: String, content: String) =
        File(input, path).apply { parentFile.mkdirs() }.writeText(content)

    private fun commit(path: String, content: String) =
        File(output, path).apply { parentFile.mkdirs() }.writeText(content)

    private fun committed(path: String) = File(output, path)

    @Nested
    inner class Declarations {

        private val packageJson = "{\n  \"name\": \"pkg\",\n  \"version\": \"1.2.3-SNAPSHOT\",\n  \"devDependencies\": {}\n}\n"

        private fun emitPackage() {
            emit("pkg/package.json", packageJson)
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): KtList<string>;\n")
            emit("pkg/kotlin/pkg.mjs", "export function names() {}\n")
            emit("pkg/kotlin/pkg.mjs.map", "{}\n")
            emit("pkg/kotlin/legacy.d.ts", "export declare function legacy(): string;\n")
        }

        private val names = JSON_DECLARATIONS + "export declare function names(): KtList<string>;"

        @Test
        fun `tsGen should write only the declarations and the package json`() {
            emitPackage()
            commit("pkg/kotlin/old.mjs", "left by the full copy\n")

            project(Kt2TsFiles.DECLARATIONS).run("tsGen")

            val written = File(output, "pkg").walkTopDown().filter { it.isFile }
                .map { it.relativeTo(output).invariantSeparatorsPath }.toList()
            assertThat(written)
                .containsExactlyInAnyOrder("pkg/package.json", "pkg/kotlin/pkg.d.mts")
            assertThat(committed("pkg/kotlin/pkg.d.mts")).hasContent(names)
            assertThat(committed("pkg/package.json").readText()).contains("\"version\": \"${Kt2TsFiles.CONTRACT_VERSION}\"")
        }

        @Test
        fun `tsGen should write only the declarations by default`() {
            emitPackage()

            project().run("tsGen")

            assertThat(committed("pkg/kotlin/pkg.mjs")).doesNotExist()
            assertThat(committed("pkg/package.json").readText()).contains("\"version\": \"${Kt2TsFiles.CONTRACT_VERSION}\"")
        }

        @Test
        fun `tsGen should drop the main entry the declarations do not ship`() {
            emitPackage()
            emit("pkg/package.json", packageJson.replace("\"version\"", "\"main\": \"kotlin/pkg.mjs\",\n  \"version\""))

            project().run("tsGen")

            assertThat(committed("pkg/package.json").readText()).doesNotContain("\"main\"")
        }

        @Test
        fun `tsGen should write every file at the contract version with ALL`() {
            emitPackage()

            project(Kt2TsFiles.ALL).run("tsGen")

            assertThat(committed("pkg/kotlin/pkg.mjs")).exists()
            assertThat(committed("pkg/package.json").readText()).contains("\"version\": \"${Kt2TsFiles.CONTRACT_VERSION}\"")
        }

        @Test
        fun `checkTsGen should not compare a JavaScript file`() {
            emitPackage()
            val project = project(Kt2TsFiles.DECLARATIONS)
            project.run("tsGen")
            emit("pkg/kotlin/pkg.mjs", "export function names() { return [] }\n")

            project.run("checkTsGen")
        }

        @Test
        fun `checkTsGen should fail on a changed declaration`() {
            emitPackage()
            val project = project(Kt2TsFiles.DECLARATIONS)
            project.run("tsGen")
            emit("pkg/kotlin/pkg.d.mts", "export declare function other(): string;\n")

            assertThatThrownBy { project.run("checkTsGen") }.hasMessageContaining("pkg/kotlin/pkg.d.mts differs")
        }

        @Test
        fun `checkTsGen should accept a new build version`() {
            emitPackage()
            val project = project(Kt2TsFiles.DECLARATIONS)
            project.run("tsGen")
            emit("pkg/package.json", packageJson.replace("1.2.3-SNAPSHOT", "1.2.4"))

            project.run("checkTsGen")
            assertThat(committed("pkg/package.json").readText()).contains("\"version\": \"${Kt2TsFiles.CONTRACT_VERSION}\"")
        }
    }

    @Nested
    inner class TsGenOutput {

        @Test
        fun `tsGen should keep the node_modules of the output directory`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): KtList<string>;\n")
            commit("pkg/node_modules/dep/index.js", "linked by the package manager\n")
            val project = project()

            project.run("tsGen")

            assertThat(committed("pkg/node_modules/dep/index.js")).exists()
            assertThat(committed("pkg/kotlin/pkg.d.mts"))
                .hasContent(JSON_DECLARATIONS + "export declare function names(): KtList<string>;")
        }

        @Test
        fun `tsGen should remove a file the Kotlin output no longer has`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): string;\n")
            commit("pkg/kotlin/dropped.d.mts", "export declare function gone(): string;\n")
            val project = project()

            project.run("tsGen")

            assertThat(committed("pkg/kotlin/dropped.d.mts")).doesNotExist()
            assertThat(committed("pkg/kotlin/pkg.d.mts")).exists()
        }

        @Test
        fun `tsGen should not rewrite the declarations of a linked dependency`() {
            val linked = "export declare function names(): KtList<string>;\n"
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): string;\n")
            commit("pkg/node_modules/dep/dep.d.mts", linked)
            val project = project()

            project.run("tsGen")

            assertThat(committed("pkg/node_modules/dep/dep.d.mts")).hasContent(linked.trimEnd())
        }

        @Test
        fun `tsGen should keep a dangling link under node_modules`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): string;\n")
            val link = File(output, "pkg/node_modules/gone").toPath()
            java.nio.file.Files.createDirectories(link.parent)
            java.nio.file.Files.createSymbolicLink(link, File(dir, "nowhere").toPath())
            val project = project()

            project.run("tsGen")

            assertThat(java.nio.file.Files.isSymbolicLink(link)).isTrue()
            assertThat(committed("pkg/kotlin/pkg.d.mts")).exists()
        }

        @Test
        fun `tsGen should run after cleanTsGen`() {
            val project = project()
            val tsGen = project.tasks.getByName("tsGen")

            assertThat(tsGen.mustRunAfter.getDependencies(tsGen).map { it.name }).contains("cleanTsGen")
        }

        @Test
        fun `tsGen should not depend on cleanTsGen`() {
            val project = project()
            val tsGen = project.tasks.getByName("tsGen")

            assertThat(tsGen.taskDependencies.getDependencies(tsGen).map { it.name }).doesNotContain("cleanTsGen")
        }
    }

    @Nested
    inner class TsGenCheckTask {

        private fun generate(): Project {
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): KtList<string>;\n")
            emit("pkg/kotlin/pkg.mjs", "export function names() { return []; }\n")
            emit("pkg/kotlin/pkg.mjs.map", "{\"sources\":[\"/home/ci/src/Names.kt\"]}\n")
            emit("pkg/package.json", "{\n  \"name\": \"pkg\",\n  \"version\": \"1.0.0\",\n  \"devDependencies\": {\"x\": \"1\"},\n}\n")
            val project = project(Kt2TsFiles.ALL)
            project.run("tsGen")
            return project
        }

        @Test
        fun `should be registered with tsGen`() {
            assertThat(project().tasks.findByName("checkTsGen")).isNotNull
        }

        @Test
        fun `should be registered on the root project only`() {
            val root = ProjectBuilder.builder().buildFixersRoot()
            val module = ProjectBuilder.builder().withName("module").withParent(root).build()
            val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)

            module.configureKt2Ts(config)

            assertThat(module.tasks.names).doesNotContain("tsGen", "cleanTsGen", "checkTsGen")
        }

        private fun projectEmittingJs(): Project {
            val project = ProjectBuilder.builder().withProjectDir(File(dir, "project")).buildFixersRoot()
            project.pluginManager.apply("base")
            project.tasks.register("jsBrowserProductionLibraryDistribution")
            return project
        }

        private fun Project.checkDependencies(): List<String> {
            val check = tasks.getByName("check")
            return check.taskDependencies.getDependencies(check).map { it.name }
        }

        @Test
        fun `should be wired into check when the build emits Kotlin JS`() {
            val project = projectEmittingJs()
            val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)

            project.configureKt2Ts(config)

            assertThat(project.checkDependencies()).contains("checkTsGen")
        }

        @Test
        fun `should stay out of check when the build emits no Kotlin JS`() {
            val project = ProjectBuilder.builder().withProjectDir(File(dir, "project")).buildFixersRoot()
            project.pluginManager.apply("base")
            val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)

            project.configureKt2Ts(config)

            assertThat(project.checkDependencies()).doesNotContain("checkTsGen")
        }

        @Test
        fun `should wait for the Kotlin JS library distribution of every project`() {
            val root = ProjectBuilder.builder().buildFixersRoot()
            val module = ProjectBuilder.builder().withName("module").withParent(root).build()
            val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
            root.configureKt2Ts(config)
            val distribution = module.tasks.register("jsBrowserProductionLibraryDistribution").get()

            val check = root.tasks.getByName("checkTsGen")
            assertThat(check.taskDependencies.getDependencies(check)).contains(distribution)
        }

        @Test
        fun `should fail when there is no Kotlin output to compare with`() {
            commit("pkg/kotlin/pkg.d.mts", "export declare function names(): string[];\n")
            val project = project()

            assertThatThrownBy { project.run("checkTsGen") }
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining(input.absolutePath)
        }

        @Test
        fun `should pass on the copy tsGen wrote`() {
            val project = generate()

            project.run("checkTsGen")
        }

        @Test
        fun `should leave the committed copy untouched`() {
            val project = generate()
            val before = committed("pkg/kotlin/pkg.d.mts").readText()

            project.run("checkTsGen")

            assertThat(committed("pkg/kotlin/pkg.d.mts")).hasContent(before)
            assertThat(File(input, "pkg/kotlin/pkg.d.mts")).hasContent("export declare function names(): KtList<string>;")
        }

        @Test
        fun `should name a declaration file that differs from the Kotlin output`() {
            val project = generate()
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): KtList<number>;\n")

            assertThatThrownBy { project.run("checkTsGen") }
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining("pkg/kotlin/pkg.d.mts")
                .hasMessageContaining("tsGen")
        }

        @Test
        fun `should name a compiled module that differs from the Kotlin output`() {
            val project = generate()
            emit("pkg/kotlin/pkg.mjs", "export function names() { return ['a']; }\n")

            assertThatThrownBy { project.run("checkTsGen") }.hasMessageContaining("pkg/kotlin/pkg.mjs")
        }

        @Test
        fun `should name a file the committed copy is missing`() {
            val project = generate()
            emit("pkg/kotlin/added.d.mts", "export declare function added(): string;\n")

            assertThatThrownBy { project.run("checkTsGen") }
                .hasMessageContaining("pkg/kotlin/added.d.mts is missing from the committed copy")
        }

        @Test
        fun `should name a file the Kotlin output no longer has`() {
            val project = generate()
            commit("pkg/kotlin/dropped.d.mts", "export declare function gone(): string;\n")

            assertThatThrownBy { project.run("checkTsGen") }
                .hasMessageContaining("pkg/kotlin/dropped.d.mts is no longer generated")
        }

        @Test
        fun `should ignore node_modules, source maps and the package version`() {
            val project = generate()
            commit("pkg/node_modules/dep/index.js", "linked by the package manager\n")
            emit("pkg/kotlin/pkg.mjs.map", "{\"sources\":[\"/Users/dev/src/Names.kt\"]}\n")
            emit("pkg/package.json", "{\n  \"name\": \"pkg\",\n  \"version\": \"2.0.0-SNAPSHOT\",\n  \"devDependencies\": {\"x\": \"1\"},\n}\n")

            project.run("checkTsGen")
        }

        @Test
        fun `should accept a module the emitter only reordered`() {
            val project = generate()
            emit(
                "pkg/kotlin/pkg.mjs",
                "class Receive {}\nclass Send {}\n//region block: post-declaration\n"
                    + "initMetadataForClass(Channel, 'Channel', VOID, VOID, [Receive, Send], [1, 0]);\n//endregion\n"
            )
            project.run("tsGen")
            emit(
                "pkg/kotlin/pkg.mjs",
                "class Send {}\nclass Receive {}\n//region block: post-declaration\n"
                    + "initMetadataForClass(Channel, 'Channel', VOID, VOID, [Send, Receive], [1, 0]);\n//endregion\n"
            )

            project.run("checkTsGen")
        }

        @Test
        fun `should name a declaration made twice`() {
            emit(
                "pkg/kotlin/pkg.d.mts",
                "export declare interface Dto {\n}\nexport declare interface Dto extends Dto {\n}\n"
            )
            val project = project()
            project.run("tsGen")

            assertThatThrownBy { project.run("checkTsGen") }
                .hasMessageContaining("out/pkg/kotlin/pkg.d.mts")
                .hasMessageContaining("interface Dto")
                .hasMessageContaining("Rename")
                .hasMessageNotContaining("Regenerate")
        }

        @Test
        fun `should name a class and an interface of the same name`() {
            emit(
                "pkg/kotlin/pkg.d.mts",
                "export declare class Dto {\n}\nexport declare interface Dto {\n}\n"
            )
            val project = project()
            project.run("tsGen")

            assertThatThrownBy { project.run("checkTsGen") }.hasMessageContaining("`Dto`")
        }

        @Test
        fun `should name any exported declaration made twice`() {
            listOf(
                "export declare function same(): void;\nexport declare function same(): string;\n",
                "export declare const same: string;\nexport declare let same: number;\n",
                "export declare enum same {\n}\nexport declare var same: number;\n",
                "export type same = string;\nexport declare interface same {\n}\n",
                "export declare const enum same {\n}\nexport declare enum same {\n}\n",
                "export declare class same {\n}\nexport declare const same: string;\n",
            ).forEach { declarations ->
                emit("pkg/kotlin/pkg.d.mts", declarations)
                val project = project()
                project.run("tsGen")

                assertThatThrownBy { project.run("checkTsGen") }
                    .`as`(declarations)
                    .hasMessageContaining("out/pkg/kotlin/pkg.d.mts declares `same` twice")
            }
        }

        @Test
        fun `should name a Kotlin declaration that takes the name of Json or KtOpaque`() {
            listOf("export declare interface Json {\n}\n", "export declare class KtOpaque {\n}\n").forEach { declarations ->
                emit("pkg/kotlin/pkg.d.mts", declarations)
                val project = project()
                project.run("tsGen")

                assertThatThrownBy { project.run("checkTsGen") }
                    .`as`(declarations)
                    .hasMessageContaining("out/pkg/kotlin/pkg.d.mts declares `")
                    .hasMessageContaining("Rename")
            }
        }

        @Test
        fun `should name a Kotlin declaration that takes the name of a declaration the module keeps unexported`() {
            val helpers = Regex("""^type (\w+)""", RegexOption.MULTILINE).findAll(JSON_DECLARATIONS)
                .map { it.groupValues[1] }.toList()
            assertThat(helpers).contains("JsonKey", "JsonMap", "JsonField", "JsonFields", "JsonOptional", "JsonObject")
            val emitted = "type Nullable<T> = T | null | undefined\n" +
                "declare function KtSingleton<T>(): T & (abstract new() => any);\n"
            (helpers + "Nullable").forEach { name ->
                listOf("export declare interface $name {\n}\n", "export declare class $name {\n}\n").forEach { kotlin ->
                    emit("pkg/kotlin/pkg.d.mts", emitted + kotlin)
                    val project = project()
                    project.run("tsGen")

                    assertThatThrownBy { project.run("checkTsGen") }
                        .`as`(kotlin)
                        .hasMessageContaining("out/pkg/kotlin/pkg.d.mts declares `$name` twice")
                        .hasMessageContaining("Rename")
                }
            }
            emit("pkg/kotlin/pkg.d.mts", emitted + "export declare class KtSingleton {\n}\n")
            val project = project()
            project.run("tsGen")

            assertThatThrownBy { project.run("checkTsGen") }.hasMessageContaining("declares `KtSingleton` twice")
        }

        @Test
        fun `should read a declaration name to its end, Unicode letters included`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare interface JsonObjectÉ {\n}\nexport declare class Prixé {\n}\n" +
                "export declare class Prix {\n}\n")
            val project = project()
            project.run("tsGen")

            project.run("checkTsGen")
        }

        @Test
        fun `should accept a Kotlin function named like a type the module keeps unexported`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function JsonObject(): void;\n")
            val project = project()
            project.run("tsGen")

            project.run("checkTsGen")
        }

        @Test
        fun `should accept exported declarations of distinct names`() {
            emit(
                "pkg/kotlin/pkg.d.mts",
                "export declare function one(): void;\nexport declare const two: string;\n" +
                    "export type three = string;\nexport declare enum four {\n}\n"
            )
            val project = project()
            project.run("tsGen")

            project.run("checkTsGen")
        }

        @Test
        fun `should accept a type and a value of the same name`() {
            emit(
                "pkg/kotlin/pkg.d.mts",
                "export type Status = \"ON\" | \"OFF\";\nexport declare const Status: {\n    readonly ON: Status;\n};\n"
            )
            val project = project()
            project.run("tsGen")

            project.run("checkTsGen")
        }

        @Test
        fun `should read a const enum under its own name`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare const enum One {\n}\nexport declare const enum Two {\n}\n")
            val project = project()
            project.run("tsGen")

            project.run("checkTsGen")
        }

        @Test
        fun `should accept one name exported by two namespaces`() {
            emit(
                "pkg/kotlin/pkg.d.mts",
                "export declare namespace One {\n    export const id: string;\n}\n" +
                    "export declare namespace Two {\n    export const id: string;\n}\n"
            )
            val project = project()
            project.run("tsGen")

            project.run("checkTsGen")
        }

        @Test
        fun `should accept a dependency's declaration repeated in each package that bundles it`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function shared(): void;\n")
            emit("other/kotlin/other.d.mts", "export declare function shared(): void;\n")
            val project = project()
            project.run("tsGen")

            project.run("checkTsGen")
        }

        @Test
        fun `should run after tsGen`() {
            val project = project()
            val check = project.tasks.getByName("checkTsGen")

            assertThat(check.mustRunAfter.getDependencies(check).map { it.name }).contains("tsGen")
        }

        @Test
        fun `should accept a class and the namespace of its companion`() {
            emit(
                "pkg/kotlin/pkg.d.mts",
                "export declare class Dto {\n}\nexport declare namespace Dto {\n}\n"
            )
            val project = project()
            project.run("tsGen")

            project.run("checkTsGen")
        }

        @Test
        fun `should still compare a dependency named version`() {
            val project = generate()
            emit(
                "pkg/package.json",
                "{\n  \"name\": \"pkg\",\n  \"version\": \"1.0.0\",\n  \"dependencies\": {\n    \"version\": \"2\"\n  },\n}\n"
            )

            assertThatThrownBy { project.run("checkTsGen") }.hasMessageContaining("pkg/package.json")
        }
    }

    @Nested
    inner class AdditionalCleaning {

        private val formerOutput = mapOf(".d.mts" to listOf(Regex("""Record<string, string>""") to "Labels"))

        @Test
        fun `tsGen should fail on a rule that changes no file of the contract`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function labels(): KtMap<string, string>;\n")

            assertThatThrownBy { project(additionalCleaning = formerOutput).run("tsGen") }
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining("additionalCleaning rules matched no file")
                .hasMessageContaining(".d.mts: Record<string, string>")
                .hasMessageContaining("delete it, or rewrite it")
        }

        @Test
        fun `checkTsGen should fail on a rule that changes no file of the contract`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function labels(): KtMap<string, string>;\n")
            project().run("tsGen")

            assertThatThrownBy { project(additionalCleaning = formerOutput).run("checkTsGen") }
                .hasMessageContaining(".d.mts: Record<string, string>")
        }

        @Test
        fun `tsGen should apply a rule that matches`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function labels(): KtMap<string, string>;\n")
            val rule = mapOf(".d.mts" to listOf(Regex("""KtMap<string, string>""") to "Labels"))

            project(additionalCleaning = rule).run("tsGen")

            assertThat(committed("pkg/kotlin/pkg.d.mts").readText())
                .endsWith("export declare function labels(): Labels;\n")
        }

        @Test
        fun `npmTsGen cleaning should accept a rule one package does not need`() {
            val file = File(dir, "npm/pkg.d.mts").apply { parentFile.mkdirs() }
            file.writeText("export declare function names(): KtList<string>;\n")

            cleanProjectDir(file.parent, formerOutput)

            assertThat(file.readText()).isEqualTo("export declare function names(): KtList<string>;\n")
        }
    }
}
