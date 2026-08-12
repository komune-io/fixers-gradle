package io.komune.fixers.gradle.config.utils

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for [versionFromFile].
 */
class VersionUtilsTest {

    @TempDir
    lateinit var rootDir: File

    private fun rootProject(): Project = ProjectBuilder.builder()
        .withProjectDir(rootDir)
        .build()

    @Test
    fun `should return null when no VERSION file exists`() {
        assertThat(rootProject().versionFromFile()).isNull()
    }

    @Test
    fun `should read the version from the VERSION file`() {
        File(rootDir, "VERSION").writeText("1.2.3")

        assertThat(rootProject().versionFromFile()).isEqualTo("1.2.3")
    }

    @Test
    fun `should trim surrounding whitespace and trailing newline`() {
        File(rootDir, "VERSION").writeText("  1.2.3-SNAPSHOT \n")

        assertThat(rootProject().versionFromFile()).isEqualTo("1.2.3-SNAPSHOT")
    }

    @Test
    fun `should resolve the VERSION file of the root project from a subproject`() {
        File(rootDir, "VERSION").writeText("4.5.6")
        val root = rootProject()
        val sub = ProjectBuilder.builder()
            .withParent(root)
            .withName("sub")
            .build()

        assertThat(sub.versionFromFile()).isEqualTo("4.5.6")
    }
}
