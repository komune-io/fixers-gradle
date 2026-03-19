package io.komune.fixers.gradle.integration.publish

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Integration tests that verify the staging directory is complete after publishing.
 * Specifically guards against the race condition where cleanStaging can run
 * concurrently with publish tasks under --parallel, producing partial staging
 * directories with missing .pom files.
 */
class PublishStagingIntegrationTest : BaseIntegrationTest() {

	private fun setupRootProject() {
		settingsFile.writeText("""
			rootProject.name = "staging-test"
			include("lib-jvm")
			include("lib-mpp")
		""".trimIndent())

		testProjectDir.resolve("VERSION").toFile().writeText("1.0.0")

		propertiesFile.writeText("""
			kotlin.code.style=official
			org.gradle.parallel=true
		""".trimIndent())

		writeBuildFile("""
			plugins {
				id("io.komune.fixers.gradle.config")
				id("io.komune.fixers.gradle.publish")
			}

			fixers {
				bundle {
					id = "staging-test"
					name = "Staging Test"
					description = "Integration test for staging directory completeness"
					url = "https://github.com/komune-io/fixers-gradle"
				}
			}
		""".trimIndent())
	}

	private fun setupJvmSubproject() {
		val jvmDir = testProjectDir.resolve("lib-jvm").toFile()
		jvmDir.mkdirs()
		File(jvmDir, "build.gradle.kts").writeText("""
			plugins {
				id("io.komune.fixers.gradle.kotlin.jvm")
				id("io.komune.fixers.gradle.publish")
			}

			repositories {
				mavenCentral()
			}

			group = "com.example"
			version = "1.0.0"
		""".trimIndent())

		val jvmSrc = testProjectDir.resolve("lib-jvm/src/main/kotlin/com/example").toFile()
		jvmSrc.mkdirs()
		File(jvmSrc, "JvmLib.kt").writeText("""
			package com.example

			class JvmLib {
				fun hello() = "Hello from JVM"
			}
		""".trimIndent())
	}

	private fun setupMppSubproject() {
		val mppDir = testProjectDir.resolve("lib-mpp").toFile()
		mppDir.mkdirs()
		File(mppDir, "build.gradle.kts").writeText("""
			plugins {
				id("io.komune.fixers.gradle.kotlin.mpp")
				id("io.komune.fixers.gradle.publish")
			}

			repositories {
				mavenCentral()
			}

			group = "com.example"
			version = "1.0.0"

			kotlin {
				jvm()
				js(IR) {
					browser()
				}
			}
		""".trimIndent())

		createMppSourceFiles()
	}

	private fun createMppSourceFiles() {
		val commonSrc = testProjectDir.resolve("lib-mpp/src/commonMain/kotlin/com/example").toFile()
		commonSrc.mkdirs()
		File(commonSrc, "MppLib.kt").writeText("""
			package com.example

			expect class MppLib() {
				fun hello(): String
			}
		""".trimIndent())

		val jvmSrc = testProjectDir.resolve("lib-mpp/src/jvmMain/kotlin/com/example").toFile()
		jvmSrc.mkdirs()
		File(jvmSrc, "MppLibJvm.kt").writeText("""
			package com.example

			actual class MppLib {
				actual fun hello() = "Hello from JVM"
			}
		""".trimIndent())

		val jsSrc = testProjectDir.resolve("lib-mpp/src/jsMain/kotlin/com/example").toFile()
		jsSrc.mkdirs()
		File(jsSrc, "MppLibJs.kt").writeText("""
			package com.example

			actual class MppLib {
				actual fun hello() = "Hello from JS"
			}
		""".trimIndent())
	}

	/**
	 * Verifies that running cleanStaging + publish tasks together (as promote/stage do)
	 * produces a complete staging directory where every published module has a .pom file.
	 *
	 * Guards against the race condition where cleanStaging's deleteRecursively()
	 * runs concurrently with publish tasks under parallel execution.
	 */
	@Test
	fun `staging directory should have pom files for all published modules after cleanStaging and publish`() {
		setupRootProject()
		setupJvmSubproject()
		setupMppSubproject()

		val result = runGradle(
			"cleanStaging",
			":lib-jvm:publishAllPublicationsToStagingRepository",
			":lib-mpp:publishAllPublicationsToStagingRepository",
			"--parallel"
		)

		assertThat(result.task(":cleanStaging")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
		assertThat(result.task(":lib-jvm:publishAllPublicationsToStagingRepository")?.outcome)
			.isEqualTo(TaskOutcome.SUCCESS)
		assertThat(result.task(":lib-mpp:publishAllPublicationsToStagingRepository")?.outcome)
			.isEqualTo(TaskOutcome.SUCCESS)

		val stagingDir = testProjectDir.resolve("build/staging-deploy").toFile()
		assertThat(stagingDir).exists()

		val versionDirs = stagingDir.walkTopDown()
			.filter { it.isDirectory && it.name == "1.0.0" }
			.toList()

		// JVM subproject: 1 module, KMP: 3 (root + jvm + js)
		assertThat(versionDirs).hasSizeGreaterThanOrEqualTo(4)

		versionDirs.forEach { versionDir ->
			val pomFiles = versionDir.listFiles()?.filter {
				it.name.endsWith(".pom") && !it.name.contains(".pom.")
			}
			val modulePath = versionDir.parentFile.name
			assertThat(pomFiles)
				.withFailMessage("No .pom file found for module '$modulePath' in ${versionDir.path}")
				.isNotNull
				.isNotEmpty
		}
	}
}
