package io.komune.fixers.gradle.config.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

private const val JUNIT_PLATFORM_LAUNCHER = "org.junit.platform:junit-platform-launcher"

/**
 * Configures all Test tasks in the project to use JUnit Platform, and puts the JUnit Platform
 * launcher on `<testCompilation>RuntimeOnly` when the tests use JUnit: Gradle 9 no longer
 * provides it.
 * This is idempotent - calling it multiple times has no additional effect.
 */
fun Project.configureJUnitPlatform(testCompilation: String = "test") {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    val runtimeClasspath = "${testCompilation}RuntimeClasspath"
    configurations.matching { it.name == "${testCompilation}RuntimeOnly" }.configureEach {
        withDependencies {
            val declared = configurations.findByName(runtimeClasspath)?.allDependencies
            // Versionless: the version comes from the JUnit BOM the JUnit artifacts declare. Without
            // JUnit on the classpath nothing provides it, and the resolution would fail.
            if (declared != null && declared.any(Dependency::isJUnit) && declared.none(Dependency::isLauncher)) {
                add(project.dependencies.create(JUNIT_PLATFORM_LAUNCHER))
            }
        }
    }
}

private fun Dependency.isLauncher() = "${group.orEmpty()}:$name" == JUNIT_PLATFORM_LAUNCHER

private fun Dependency.isJUnit() =
    group?.startsWith("org.junit") == true ||
        group == "org.jetbrains.kotlin" && name in setOf("kotlin-test", "kotlin-test-junit5") ||
        group == "org.springframework.boot" && name == "spring-boot-starter-test"
