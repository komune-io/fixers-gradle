package io.komune.fixers.gradle.plugin.npm.task

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for NpmTsGenTask.
 */
class NpmTsGenTaskTest {

    @TempDir
    lateinit var buildDir: File

    @Test
    fun `should clean files in build directory using configured regexes`() {
        val file = File(buildDir, "api.d.ts")
        file.writeText("value: kotlin.Long;")
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("npmTsGenTask", NpmTsGenTask::class.java).get()
        task.buildDir = buildDir.absolutePath
        task.cleaning = mapOf(".d.ts" to listOf(Regex("""kotlin\.Long""") to "number"))

        task.doAction()

        assertThat(file.readText()).isEqualTo("value: number;")
    }

    @Test
    fun `should be a no-op with empty cleaning configuration`() {
        val file = File(buildDir, "api.d.ts")
        file.writeText("value: kotlin.Long;")
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("npmTsGenTask", NpmTsGenTask::class.java).get()
        task.buildDir = buildDir.absolutePath

        task.doAction()

        assertThat(file.readText()).isEqualTo("value: kotlin.Long;")
    }
}
