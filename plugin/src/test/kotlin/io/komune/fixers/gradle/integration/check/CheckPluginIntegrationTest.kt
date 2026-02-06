package io.komune.fixers.gradle.integration.check

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Integration tests for the CheckPlugin.
 * Tests the plugin in a real project with different configurations.
 */
class CheckPluginIntegrationTest : BaseIntegrationTest() {

    /**
     * Creates a simple Kotlin source file for JVM projects.
     */
    private fun createJvmSourceFile() {
        val sourceDir = testProjectDir.resolve("src/main/kotlin/com/example").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello(name: String): String = "Hello, ${'$'}name!"
            }
        """.trimIndent())
    }

    /**
     * Creates a simple Kotlin test file for JVM projects.
     */
    private fun createJvmTestFile() {
        val testDir = testProjectDir.resolve("src/test/kotlin/com/example").toFile()
        testDir.mkdirs()
        File(testDir, "SampleTest.kt").writeText("""
            package com.example

            import org.junit.jupiter.api.Test
            import org.junit.jupiter.api.Assertions.assertEquals

            class SampleTest {
                @Test
                fun `hello returns greeting`() {
                    val sample = Sample()
                    assertEquals("Hello, World!", sample.hello("World"))
                }
            }
        """.trimIndent())
    }

    /**
     * Creates source files for Kotlin Multiplatform JVM target.
     */
    private fun createMppJvmSourceFiles() {
        val jvmMainDir = testProjectDir.resolve("src/jvmMain/kotlin/com/example").toFile()
        jvmMainDir.mkdirs()
        File(jvmMainDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello(): String = "Hello from JVM!"
            }
        """.trimIndent())

        val jvmTestDir = testProjectDir.resolve("src/jvmTest/kotlin/com/example").toFile()
        jvmTestDir.mkdirs()
        File(jvmTestDir, "SampleTest.kt").writeText("""
            package com.example

            import kotlin.test.Test
            import kotlin.test.assertEquals

            class SampleTest {
                @Test
                fun helloReturnsGreeting() {
                    val sample = Sample()
                    assertEquals("Hello from JVM!", sample.hello())
                }
            }
        """.trimIndent())
    }

    /**
     * Test that the CheckPlugin applies correctly and Detekt runs successfully.
     */
    @Test
    fun `should apply CheckPlugin and run Detekt successfully`() {
        // Create a simple Kotlin source file with no issues
        val sourceDir = testProjectDir.resolve("src/main/kotlin/com/example").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello(name: String): String = "Hello, ${'$'}name!"
            }

        """.trimIndent())

        // Create a detekt config file
        createFile("detekt.yml", """
            complexity:
              LongParameterList:
                active: true
                allowedFunctionParameters: 5
            style:
              MagicNumber:
                active: true
        """.trimIndent())

        // Set up the build file with the CheckPlugin
        writeBuildFile("""
            plugins {
                kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.check")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                detekt {
                    disable = false
                }
            }
        """.trimIndent())

        // Run the detekt task
        val result = runGradle("detektMain")

        // Verify that the task completed successfully
        assertThat(result.task(":detektMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    /**
     * Test that the CheckPlugin correctly disables Detekt when configured to do so.
     */
    @Test
    fun `should not run Detekt when disabled`() {
        // Set up the build file with the CheckPlugin and Detekt disabled
        writeBuildFile("""
            plugins {
                kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.check")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                detekt {
                    disable = true
                }
            }
        """.trimIndent())

        // Try to run the detekt task - it should fail because the task doesn't exist
        val result = runGradleAndFail("detektMain")

        // Verify that the task failed because it doesn't exist
        assertThat(result.output).contains("Task 'detektMain' not found")
    }

    /**
     * Test that the SonarQube plugin is applied and configured correctly.
     * 
     * Note: This test only verifies that the SonarQube plugin is applied,
     * without actually running the analysis, to avoid issues with SonarQube
     * scanner dependencies.
     */
    @Test
    fun `should apply and configure SonarQube plugin`() {
        // Set up the build file with the CheckPlugin and SonarQube configuration
        writeBuildFile("""
            plugins {
                kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.check")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                sonar {
                    projectKey = "test-project"
                    organization = "test-org"
                    url = "https://sonarcloud.io"
                }
            }
        """.trimIndent())

        // Just run the tasks command to verify that the sonarqube task is available
        val result = runGradle("tasks")

        // Verify that the sonarqube task is available
        assertThat(result.output).contains("sonar")
    }

    /**
     * Tests for JaCoCo integration with the CheckPlugin.
     * Note: The CheckPlugin configures JaCoCo for subprojects, so these tests use multi-project setups.
     */
    @Nested
    inner class JacocoIntegrationTests {

        /**
         * Creates a multi-project setup with a subproject for JaCoCo testing.
         */
        private fun setupMultiProjectWithSubproject() {
            // Update settings file for multi-project build
            settingsFile.writeText("""
                rootProject.name = "integration-test-project"
                include("subproject")
            """.trimIndent())

            // Create subproject directory and build file
            val subprojectDir = testProjectDir.resolve("subproject").toFile()
            subprojectDir.mkdirs()

            // Create source files in subproject
            val sourceDir = subprojectDir.resolve("src/main/kotlin/com/example")
            sourceDir.mkdirs()
            File(sourceDir, "Sample.kt").writeText("""
                package com.example

                class Sample {
                    fun hello(name: String): String = "Hello, ${'$'}name!"
                }
            """.trimIndent())

            // Create test files in subproject
            val testDir = subprojectDir.resolve("src/test/kotlin/com/example")
            testDir.mkdirs()
            File(testDir, "SampleTest.kt").writeText("""
                package com.example

                import org.junit.jupiter.api.Test
                import org.junit.jupiter.api.Assertions.assertEquals

                class SampleTest {
                    @Test
                    fun `hello returns greeting`() {
                        val sample = Sample()
                        assertEquals("Hello, World!", sample.hello("World"))
                    }
                }
            """.trimIndent())

            // Create subproject build file
            File(subprojectDir, "build.gradle.kts").writeText("""
                plugins {
                    kotlin("jvm")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                tasks.withType<Test> {
                    useJUnitPlatform()
                }
            """.trimIndent())
        }

        /**
         * Test that JaCoCo is configured for JVM subprojects and generates reports.
         */
        @Test
        fun `should configure JaCoCo for JVM subprojects`() {
            setupMultiProjectWithSubproject()

            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}" apply false
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                fixers {
                    jacoco {
                        enabled = true
                        htmlReport = true
                        xmlReport = true
                    }
                }
            """.trimIndent())

            val result = runGradle(":subproject:test", ":subproject:jacocoTestReport")

            assertThat(result.task(":subproject:test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.task(":subproject:jacocoTestReport")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

            // Verify reports are generated
            val htmlReportDir = testProjectDir.resolve("subproject/build/reports/jacoco/test/html").toFile()
            val xmlReportFile =
                testProjectDir.resolve("subproject/build/reports/jacoco/test/jacocoTestReport.xml").toFile()
            assertThat(htmlReportDir.exists()).isTrue()
            assertThat(xmlReportFile.exists()).isTrue()
        }

        /**
         * Test that JaCoCo can be disabled via configuration.
         */
        @Test
        fun `should disable JaCoCo when configured`() {
            setupMultiProjectWithSubproject()

            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}" apply false
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                fixers {
                    jacoco {
                        enabled = false
                    }
                }
            """.trimIndent())

            val result = runGradle(":subproject:test")

            assertThat(result.task(":subproject:test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

            // Verify that jacoco plugin is not applied (no jacoco task in subproject)
            val tasksResult = runGradle(":subproject:tasks", "--all")
            assertThat(tasksResult.output).doesNotContain("jacocoTestReport")
        }

        /**
         * Test that JaCoCo HTML report can be disabled.
         */
        @Test
        fun `should respect JaCoCo HTML report configuration`() {
            setupMultiProjectWithSubproject()

            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}" apply false
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                fixers {
                    jacoco {
                        enabled = true
                        htmlReport = false
                        xmlReport = true
                    }
                }
            """.trimIndent())

            val result = runGradle(":subproject:test", ":subproject:jacocoTestReport")

            assertThat(result.task(":subproject:jacocoTestReport")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

            // Verify HTML report is not generated but XML is
            val htmlReportIndex =
                testProjectDir.resolve("subproject/build/reports/jacoco/test/html/index.html").toFile()
            val xmlReportFile =
                testProjectDir.resolve("subproject/build/reports/jacoco/test/jacocoTestReport.xml").toFile()
            assertThat(htmlReportIndex.exists()).isFalse()
            assertThat(xmlReportFile.exists()).isTrue()
        }

        /**
         * Creates a multi-project setup with an MPP subproject for JaCoCo testing.
         */
        private fun setupMppSubproject() {
            settingsFile.writeText("""
                rootProject.name = "integration-test-project"
                include("mpp-subproject")
            """.trimIndent())

            val subprojectDir = testProjectDir.resolve("mpp-subproject").toFile()
            subprojectDir.mkdirs()

            val jvmMainDir = subprojectDir.resolve("src/jvmMain/kotlin/com/example")
            jvmMainDir.mkdirs()
            File(jvmMainDir, "Sample.kt").writeText("""
                package com.example

                class Sample {
                    fun hello(): String = "Hello from JVM!"
                }
            """.trimIndent())

            val jvmTestDir = subprojectDir.resolve("src/jvmTest/kotlin/com/example")
            jvmTestDir.mkdirs()
            File(jvmTestDir, "SampleTest.kt").writeText("""
                package com.example

                import kotlin.test.Test
                import kotlin.test.assertEquals

                class SampleTest {
                    @Test
                    fun helloReturnsGreeting() {
                        val sample = Sample()
                        assertEquals("Hello from JVM!", sample.hello())
                    }
                }
            """.trimIndent())

            File(subprojectDir, "build.gradle.kts").writeText("""
                plugins {
                    id("io.komune.fixers.gradle.kotlin.mpp")
                }

                repositories {
                    mavenCentral()
                }

                kotlin {
                    sourceSets {
                        val jvmTest by getting {
                            dependencies {
                                implementation(kotlin("test"))
                            }
                        }
                    }
                }
            """.trimIndent())
        }

        /**
         * Test that JaCoCo is configured for Kotlin Multiplatform projects with JVM target.
         */
        @Test
        fun `should configure JaCoCo for Kotlin Multiplatform JVM target`() {
            setupMppSubproject()

            writeBuildFile("""
                plugins {
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                fixers {
                    bundle {
                        id = "test-bundle"
                        name = "Test Bundle"
                        description = "A test bundle"
                        url = "https://github.com/test/test"
                    }
                    jacoco {
                        enabled = true
                        htmlReport = true
                        xmlReport = true
                    }
                }
            """.trimIndent())

            val result = runGradle(":mpp-subproject:jvmTest", ":mpp-subproject:jacocoJvmTestReport")

            assertThat(result.task(":mpp-subproject:jvmTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.task(":mpp-subproject:jacocoJvmTestReport")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

            // Verify reports are generated in the correct location
            val htmlReportDir = testProjectDir.resolve("mpp-subproject/build/reports/jacoco/jvmTest/html").toFile()
            val xmlReportFile =
                testProjectDir.resolve("mpp-subproject/build/reports/jacoco/jvmTest/jacocoTestReport.xml").toFile()
            assertThat(htmlReportDir.exists()).isTrue()
            assertThat(xmlReportFile.exists()).isTrue()
        }
    }

    /**
     * Tests for SonarQube integration with the CheckPlugin.
     */
    @Nested
    inner class SonarIntegrationTests {

        /**
         * Test that Sonar JaCoCo path uses the correct default pattern with the shared constant.
         */
        @Test
        fun `should configure Sonar JaCoCo path with correct default pattern`() {
            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                repositories {
                    mavenCentral()
                }

                fixers {
                    sonar {
                        projectKey = "test-project"
                        organization = "test-org"
                    }
                }

                tasks.register("verifySonarJacocoPath") {
                    doLast {
                        val config = rootProject.extensions.getByName("fixers")
                            as io.komune.fixers.gradle.config.ConfigExtension
                        val jacocoPath = config.sonar.jacoco.get()
                        println("Sonar JaCoCo path: ${'$'}jacocoPath")
                        // Verify it uses the shared constant filename
                        val expectedFilename = io.komune.fixers.gradle.config.model.Jacoco.DEFAULT_XML_REPORT_FILENAME
                        println("Expected filename: ${'$'}expectedFilename")
                        println("Path contains expected filename: ${'$'}{jacocoPath.contains(expectedFilename)}")
                    }
                }
            """.trimIndent())

            val result = runGradle("verifySonarJacocoPath")

            assertThat(result.task(":verifySonarJacocoPath")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains("Expected filename: jacocoTestReport.xml")
            assertThat(result.output).contains("Path contains expected filename: true")
            // Verify the glob pattern supports both JVM and KMP
            assertThat(result.output).contains("**/build/reports/jacoco/**/jacocoTestReport.xml")
        }

        /**
         * Test that Sonar Detekt path uses the merged report from root project.
         */
        @Test
        fun `should configure Sonar Detekt path to merged report`() {
            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                repositories {
                    mavenCentral()
                }

                fixers {
                    sonar {
                        projectKey = "test-project"
                        organization = "test-org"
                    }
                }

                tasks.register("verifySonarDetektPath") {
                    doLast {
                        val config = rootProject.extensions.getByName("fixers")
                            as io.komune.fixers.gradle.config.ConfigExtension
                        val detektPath = config.sonar.detekt.get()
                        println("Sonar Detekt path: ${'$'}detektPath")
                        println("Path ends with merge.xml: ${'$'}{detektPath.endsWith("build/reports/detekt/merge.xml")}")
                    }
                }
            """.trimIndent())

            val result = runGradle("verifySonarDetektPath")

            assertThat(result.task(":verifySonarDetektPath")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains("Path ends with merge.xml: true")
        }

        /**
         * Test that all Sonar configuration properties can be customized.
         */
        @Test
        fun `should allow customizing all Sonar properties`() {
            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                repositories {
                    mavenCentral()
                }

                fixers {
                    sonar {
                        projectKey = "custom-project-key"
                        organization = "custom-org"
                        url = "https://custom-sonar.example.com"
                        language = "java"
                        sources = "**/src/main/java"
                        exclusions = "**/*.xml"
                        verbose = false
                    }
                }

                tasks.register("verifySonarConfig") {
                    doLast {
                        val config = rootProject.extensions.getByName("fixers")
                            as io.komune.fixers.gradle.config.ConfigExtension
                        println("projectKey: ${'$'}{config.sonar.projectKey.get()}")
                        println("organization: ${'$'}{config.sonar.organization.get()}")
                        println("url: ${'$'}{config.sonar.url.get()}")
                        println("language: ${'$'}{config.sonar.language.get()}")
                        println("sources: ${'$'}{config.sonar.sources.get()}")
                        println("exclusions: ${'$'}{config.sonar.exclusions.get()}")
                        println("verbose: ${'$'}{config.sonar.verbose.get()}")
                    }
                }
            """.trimIndent())

            val result = runGradle("verifySonarConfig")

            assertThat(result.task(":verifySonarConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains("projectKey: custom-project-key")
            assertThat(result.output).contains("organization: custom-org")
            assertThat(result.output).contains("url: https://custom-sonar.example.com")
            assertThat(result.output).contains("language: java")
            assertThat(result.output).contains("sources: **/src/main/java")
            assertThat(result.output).contains("exclusions: **/*.xml")
            assertThat(result.output).contains("verbose: false")
        }

        /**
         * Test that Sonar JaCoCo path can be customized.
         */
        @Test
        fun `should allow customizing Sonar JaCoCo path`() {
            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                repositories {
                    mavenCentral()
                }

                fixers {
                    sonar {
                        projectKey = "test-project"
                        organization = "test-org"
                        jacoco = "custom/path/to/jacoco.xml"
                    }
                }

                tasks.register("verifyCustomJacocoPath") {
                    doLast {
                        val config = rootProject.extensions.getByName("fixers")
                            as io.komune.fixers.gradle.config.ConfigExtension
                        println("Custom JaCoCo path: ${'$'}{config.sonar.jacoco.get()}")
                    }
                }
            """.trimIndent())

            val result = runGradle("verifyCustomJacocoPath")

            assertThat(result.task(":verifyCustomJacocoPath")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains("Custom JaCoCo path: custom/path/to/jacoco.xml")
        }

        /**
         * Test that generateSonarProperties task does not generate config when sonar is not configured.
         */
        @Test
        fun `should not generate sonar config when sonar is not configured`() {
            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                repositories {
                    mavenCentral()
                }

                // No sonar configuration
            """.trimIndent())

            val result = runGradle("generateSonarProperties")

            assertThat(result.task(":generateSonarProperties")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

            // Verify the file was created with placeholder content (no actual config)
            val propsFile = testProjectDir.resolve("build/sonar-project.properties").toFile()
            assertThat(propsFile.exists()).isTrue()

            val content = propsFile.readText()
            assertThat(content).contains("# No fixers sonar configuration found")
            assertThat(content).doesNotContain("sonar.organization=")
            assertThat(content).doesNotContain("sonar.projectKey=")
        }

        /**
         * Test that generateSonarProperties task creates the properties file.
         */
        @Test
        fun `should generate sonar-project properties file`() {
            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                repositories {
                    mavenCentral()
                }

                fixers {
                    sonar {
                        projectKey = "test-project-key"
                        organization = "test-organization"
                    }
                }
            """.trimIndent())

            val result = runGradle("generateSonarProperties")

            assertThat(result.task(":generateSonarProperties")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

            // Verify the file was created with correct content
            val propsFile = testProjectDir.resolve("build/sonar-project.properties").toFile()
            assertThat(propsFile.exists()).isTrue()

            val content = propsFile.readText()
            assertThat(content).contains("sonar.organization=test-organization")
            assertThat(content).contains("sonar.projectKey=test-project-key")
            assertThat(content).contains("sonar.sources=")
            assertThat(content).contains("sonar.inclusions=")
            assertThat(content).contains("sonar.exclusions=")
            assertThat(content).contains("sonar.coverage.jacoco.xmlReportPaths=")
            assertThat(content).contains("sonar.kotlin.detekt.reportPaths=")
        }

        /**
         * Test that custom properties are included in the generated sonar-project.properties file.
         */
        @Test
        fun `should include custom properties in generated sonar-project properties file`() {
            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                repositories {
                    mavenCentral()
                }

                fixers {
                    sonar {
                        projectKey = "test-project-key"
                        organization = "test-organization"
                        properties {
                            property("sonar.coverage.exclusions", "src/generated/**/*,**/models/*.kt")
                            property("sonar.cpd.exclusions", "**/generated/**")
                        }
                    }
                }
            """.trimIndent())

            val result = runGradle("generateSonarProperties")

            assertThat(result.task(":generateSonarProperties")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

            // Verify the file was created with custom properties
            val propsFile = testProjectDir.resolve("build/sonar-project.properties").toFile()
            assertThat(propsFile.exists()).isTrue()

            val content = propsFile.readText()
            assertThat(content).contains("sonar.organization=test-organization")
            assertThat(content).contains("sonar.projectKey=test-project-key")
            assertThat(content).contains("# Custom properties")
            assertThat(content).contains("sonar.coverage.exclusions=src/generated/**/*,**/models/*.kt")
            assertThat(content).contains("sonar.cpd.exclusions=**/generated/**")
        }

        /**
         * Test that custom properties can be accessed programmatically.
         */
        @Test
        fun `should allow accessing custom properties programmatically`() {
            writeBuildFile("""
                plugins {
                    kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                    id("io.komune.fixers.gradle.config")
                    id("io.komune.fixers.gradle.check")
                }

                repositories {
                    mavenCentral()
                }

                fixers {
                    sonar {
                        projectKey = "test-project"
                        organization = "test-org"
                        properties {
                            property("sonar.coverage.exclusions", "src/generated/**/*")
                            property("sonar.test.inclusions", "**/*Test.kt")
                        }
                    }
                }

                tasks.register("verifyCustomProperties") {
                    doLast {
                        val config = rootProject.extensions.getByName("fixers")
                            as io.komune.fixers.gradle.config.ConfigExtension
                        val customProps = config.sonar.customProperties
                        println("Custom properties count: ${'$'}{customProps.size}")
                        customProps.forEach { (key, value) ->
                            println("Property: ${'$'}key = ${'$'}value")
                        }
                    }
                }
            """.trimIndent())

            val result = runGradle("verifyCustomProperties")

            assertThat(result.task(":verifyCustomProperties")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains("Custom properties count: 2")
            assertThat(result.output).contains("Property: sonar.coverage.exclusions = src/generated/**/*")
            assertThat(result.output).contains("Property: sonar.test.inclusions = **/*Test.kt")
        }
    }
}
