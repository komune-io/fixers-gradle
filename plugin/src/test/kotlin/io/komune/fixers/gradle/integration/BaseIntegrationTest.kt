package io.komune.fixers.gradle.integration

import java.io.File
import java.nio.file.Path
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

// Kotlin versions compatible with different Gradle versions
private val KOTLIN_VERSION_MAP = mapOf(
    "7.3" to "1.6.10",
    "7.4" to "1.6.21",
    "7.5" to "1.7.10",
    "7.6" to "1.7.21",
    "8.0" to "1.8.10",
    "8.1" to "1.8.20",
    "8.2" to "1.9.0",
    "8.3" to "1.9.10",
    "8.4" to "1.9.20",
    "8.5" to "1.9.22",
    "8.6" to "1.9.22",
    "8.7" to "1.9.23",
    "8.8" to "2.0.0",
    "8.9" to "2.0.10",
    "8.10" to "2.0.20",
    "8.11" to "2.0.21",
    "9.0" to "2.1.0",
    "9.1" to "2.1.10",
    "9.2" to "2.2.20",
    "9.3" to "2.3.0",
    "default" to "2.3.0"
)

/**
 * Base class for integration tests that test plugins in real projects.
 * This class provides utilities for creating test projects and running Gradle tasks.
 */
abstract class BaseIntegrationTest {

    companion object {
        /**
         * Command-line argument for detailed stack traces in Gradle builds.
         */
        private const val STACKTRACE_ARG = "--stacktrace"
    }

    /**
     * Temporary directory for the test project.
     */
    @TempDir
    lateinit var testProjectDir: Path

    /**
     * The build.gradle.kts file of the test project.
     */
    protected lateinit var buildFile: File

    /**
     * The settings.gradle.kts file of the test project.
     */
    protected lateinit var settingsFile: File

    /**
     * The gradle.properties file of the test project.
     */
    protected lateinit var propertiesFile: File

    /**
     * Set up the test project with basic files.
     */
    @BeforeEach
    fun setup() {
        buildFile = testProjectDir.resolve("build.gradle.kts").toFile()
        settingsFile = testProjectDir.resolve("settings.gradle.kts").toFile()
        propertiesFile = testProjectDir.resolve("gradle.properties").toFile()

        // Create basic settings file
        settingsFile.writeText("""
            rootProject.name = "integration-test-project"
        """.trimIndent())

        // Create basic properties file
        propertiesFile.writeText("""
            kotlin.code.style=official
        """.trimIndent())
    }

    /**
     * Write content to the build.gradle.kts file.
     */
    protected fun writeBuildFile(content: String) {
        buildFile.writeText(content)
    }

    /**
     * Create a file in the test project.
     */
    protected fun createFile(relativePath: String, content: String): File {
        val file = testProjectDir.resolve(relativePath).toFile()
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    /**
     * Copy a resource file to the test project.
     */
    protected fun copyResourceToProject(resourcePath: String, targetPath: String) {
        val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")

        val targetFile = testProjectDir.resolve(targetPath).toFile()
        targetFile.parentFile.mkdirs()

        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Get a Kotlin version that is compatible with the specified Gradle version.
     * 
     * @param gradleVersion The Gradle version to get a compatible Kotlin version for.
     * @return A Kotlin version that is compatible with the specified Gradle version.
     */
    protected fun getCompatibleKotlinVersion(gradleVersion: String?): String {
        return if (gradleVersion != null) {
            KOTLIN_VERSION_MAP[gradleVersion] ?: KOTLIN_VERSION_MAP["default"]!!
        } else {
            KOTLIN_VERSION_MAP["default"]!!
        }
    }

    /**
     * Run a Gradle build with the specified arguments.
     * 
     * @param arguments The Gradle arguments to pass.
     * @return The build result.
     */
    protected fun runGradle(vararg arguments: String): BuildResult {
        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(*arguments, STACKTRACE_ARG)
            .withPluginClasspath()
            .forwardOutput()

        return runner.build()
    }

    /**
     * Run a Gradle build with the specified Gradle version and arguments.
     * 
     * @param gradleVersion The Gradle version to use.
     * @param arguments The Gradle arguments to pass.
     * @return The build result.
     */
    protected fun runGradleWithVersion(gradleVersion: String, vararg arguments: String): BuildResult {
        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(*arguments, STACKTRACE_ARG)
            .withPluginClasspath()
            .withGradleVersion(gradleVersion)
            .forwardOutput()

        return runner.build()
    }

    /**
     * Run a Gradle build that is expected to fail.
     * 
     * @param arguments The Gradle arguments to pass.
     * @return The build result.
     */
    protected fun runGradleAndFail(vararg arguments: String): BuildResult {
        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(*arguments, STACKTRACE_ARG)
            .withPluginClasspath()
            .forwardOutput()

        return runner.buildAndFail()
    }

    /**
     * Run a Gradle build that is expected to fail with the specified Gradle version.
     * 
     * @param gradleVersion The Gradle version to use.
     * @param arguments The Gradle arguments to pass.
     * @return The build result.
     */
    protected fun runGradleAndFailWithVersion(gradleVersion: String, vararg arguments: String): BuildResult {
        val runner = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(*arguments, STACKTRACE_ARG)
            .withPluginClasspath()
            .withGradleVersion(gradleVersion)
            .forwardOutput()

        return runner.buildAndFail()
    }
}
