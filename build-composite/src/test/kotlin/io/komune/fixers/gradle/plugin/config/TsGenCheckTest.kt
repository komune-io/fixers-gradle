package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.config.ConfigExtension
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
 * linked into (`node_modules`), and `tsGenCheck` tells whether that committed copy is stale.
 */
class TsGenCheckTest {

    @TempDir
    lateinit var dir: File

    private val input get() = File(dir, "packages")
    private val output get() = File(dir, "out")

    private fun project(): Project {
        val project = ProjectBuilder.builder().withProjectDir(File(dir, "project")).build()
        val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)
        config.kt2Ts.inputDirectory.set(input.absolutePath)
        config.kt2Ts.outputDirectory.set(output.absolutePath)
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
    inner class TsGenOutput {

        @Test
        fun `tsGen should keep the node_modules of the output directory`() {
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): KtList<string>;\n")
            commit("pkg/node_modules/dep/index.js", "linked by the package manager\n")
            val project = project()

            project.run("tsGen")

            assertThat(committed("pkg/node_modules/dep/index.js")).exists()
            assertThat(committed("pkg/kotlin/pkg.d.mts")).hasContent("export declare function names(): string[];")
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
            val project = project()
            project.run("tsGen")
            return project
        }

        @Test
        fun `should be registered with tsGen`() {
            assertThat(project().tasks.findByName("tsGenCheck")).isNotNull
        }

        @Test
        fun `should be registered on the root project only`() {
            val root = ProjectBuilder.builder().build()
            val module = ProjectBuilder.builder().withName("module").withParent(root).build()
            val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)

            module.configureKt2Ts(config)

            assertThat(module.tasks.findByName("tsGen")).isNotNull
            assertThat(module.tasks.findByName("tsGenCheck")).isNull()
        }

        @Test
        fun `should be wired into check`() {
            val project = ProjectBuilder.builder().withProjectDir(File(dir, "project")).build()
            project.pluginManager.apply("base")
            val config = project.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, project)

            project.configureKt2Ts(config)

            val check = project.tasks.getByName("check")
            assertThat(check.taskDependencies.getDependencies(check).map { it.name }).contains("tsGenCheck")
        }

        @Test
        fun `should be wired into a check registered after it`() {
            val project = project()
            project.pluginManager.apply("base")

            val check = project.tasks.getByName("check")
            assertThat(check.taskDependencies.getDependencies(check).map { it.name }).contains("tsGenCheck")
        }

        @Test
        fun `should wait for the Kotlin JS library distribution of every project`() {
            val root = ProjectBuilder.builder().build()
            val module = ProjectBuilder.builder().withName("module").withParent(root).build()
            val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
            root.configureKt2Ts(config)
            val distribution = module.tasks.register("jsBrowserProductionLibraryDistribution").get()

            val check = root.tasks.getByName("tsGenCheck")
            assertThat(check.taskDependencies.getDependencies(check)).contains(distribution)
        }

        @Test
        fun `should fail when there is no Kotlin output to compare with`() {
            commit("pkg/kotlin/pkg.d.mts", "export declare function names(): string[];\n")
            val project = project()

            assertThatThrownBy { project.run("tsGenCheck") }
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining(input.absolutePath)
        }

        @Test
        fun `should pass on the copy tsGen wrote`() {
            val project = generate()

            project.run("tsGenCheck")
        }

        @Test
        fun `should leave the committed copy untouched`() {
            val project = generate()
            val before = committed("pkg/kotlin/pkg.d.mts").readText()

            project.run("tsGenCheck")

            assertThat(committed("pkg/kotlin/pkg.d.mts")).hasContent(before)
            assertThat(File(input, "pkg/kotlin/pkg.d.mts")).hasContent("export declare function names(): KtList<string>;")
        }

        @Test
        fun `should name a declaration file that differs from the Kotlin output`() {
            val project = generate()
            emit("pkg/kotlin/pkg.d.mts", "export declare function names(): KtList<number>;\n")

            assertThatThrownBy { project.run("tsGenCheck") }
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining("pkg/kotlin/pkg.d.mts")
                .hasMessageContaining("tsGen")
        }

        @Test
        fun `should name a compiled module that differs from the Kotlin output`() {
            val project = generate()
            emit("pkg/kotlin/pkg.mjs", "export function names() { return ['a']; }\n")

            assertThatThrownBy { project.run("tsGenCheck") }.hasMessageContaining("pkg/kotlin/pkg.mjs")
        }

        @Test
        fun `should name a file the committed copy is missing`() {
            val project = generate()
            emit("pkg/kotlin/added.d.mts", "export declare function added(): string;\n")

            assertThatThrownBy { project.run("tsGenCheck") }.hasMessageContaining("pkg/kotlin/added.d.mts")
        }

        @Test
        fun `should name a file the Kotlin output no longer has`() {
            val project = generate()
            commit("pkg/kotlin/dropped.d.mts", "export declare function gone(): string;\n")

            assertThatThrownBy { project.run("tsGenCheck") }.hasMessageContaining("pkg/kotlin/dropped.d.mts")
        }

        @Test
        fun `should ignore node_modules, source maps and the package version`() {
            val project = generate()
            commit("pkg/node_modules/dep/index.js", "linked by the package manager\n")
            emit("pkg/kotlin/pkg.mjs.map", "{\"sources\":[\"/Users/dev/src/Names.kt\"]}\n")
            emit("pkg/package.json", "{\n  \"name\": \"pkg\",\n  \"version\": \"2.0.0-SNAPSHOT\",\n  \"devDependencies\": {\"x\": \"1\"},\n}\n")

            project.run("tsGenCheck")
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

            project.run("tsGenCheck")
        }

        @Test
        fun `should name a declaration made twice`() {
            emit(
                "pkg/kotlin/pkg.d.mts",
                "export declare interface Dto {\n}\nexport declare interface Dto extends Dto {\n}\n"
            )
            val project = project()
            project.run("tsGen")

            assertThatThrownBy { project.run("tsGenCheck") }
                .hasMessageContaining("out/pkg/kotlin/pkg.d.mts")
                .hasMessageContaining("interface Dto")
                .hasMessageContaining("Rename")
                .hasMessageNotContaining("Regenerate")
        }

        @Test
        fun `should accept a class and the namespace of its companion`() {
            emit(
                "pkg/kotlin/pkg.d.mts",
                "export declare class Dto {\n}\nexport declare namespace Dto {\n}\n"
            )
            val project = project()
            project.run("tsGen")

            project.run("tsGenCheck")
        }

        @Test
        fun `should still compare a dependency named version`() {
            val project = generate()
            emit(
                "pkg/package.json",
                "{\n  \"name\": \"pkg\",\n  \"version\": \"1.0.0\",\n  \"dependencies\": {\n    \"version\": \"2\"\n  },\n}\n"
            )

            assertThatThrownBy { project.run("tsGenCheck") }.hasMessageContaining("pkg/package.json")
        }
    }
}
