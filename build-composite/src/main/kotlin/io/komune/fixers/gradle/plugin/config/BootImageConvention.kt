package io.komune.fixers.gradle.plugin.config

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

internal const val SPRING_BOOT_PLUGIN_ID = "org.springframework.boot"
internal const val BOOT_BUILD_IMAGE_TASK = "bootBuildImage"
internal const val HEALTH_CHECKER_ENABLED = "BP_HEALTH_CHECKER_ENABLED"

private const val HEALTH_CHECKER_BUILDPACK = "paketobuildpacks/health-checker"
private const val DIGEST = "@sha256:"

/**
 * Every Spring Boot image is built from a builder pinned by digest and carries Paketo's health
 * checker, the only probe a compose healthcheck can run in the shell-less tiny run image.
 * Versions stay with the project; a module that leaves either out fails at configuration.
 *
 * The task is reached by name: fixers does not put the Spring Boot plugin on its consumers'
 * classpath.
 */
internal fun Project.enforceBootImageConvention() {
    pluginManager.withPlugin(SPRING_BOOT_PLUGIN_ID) {
        if (state.executed) checkBootImages() else afterEvaluate { checkBootImages() }
    }
}

private fun Project.checkBootImages() {
    tasks.matching { it.name == BOOT_BUILD_IMAGE_TASK }.forEach { task ->
        val problems = bootImageProblems(
            builder = task.property<Property<String>>("getBuilder").orNull,
            buildpacks = task.property<ListProperty<String>>("getBuildpacks").getOrElse(emptyList()),
        )
        if (problems.isNotEmpty()) {
            throw GradleException(
                "${task.path} does not follow the fixers image convention:\n" +
                    problems.joinToString("\n") { "  - $it" }
            )
        }
        task.property<MapProperty<String, String>>("getEnvironment").put(HEALTH_CHECKER_ENABLED, "true")
    }
}

/** One message per rule of [enforceBootImageConvention] that [builder] and [buildpacks] break. */
internal fun bootImageProblems(builder: String?, buildpacks: List<String>): List<String> {
    val problems = mutableListOf<String>()
    if (builder.isNullOrBlank()) {
        problems += "no builder: set one pinned by digest, e.g. docker.io/paketobuildpacks/builder-noble-java-tiny:<version>@sha256:<digest>"
    } else if (!builder.contains(DIGEST)) {
        problems += "builder $builder is not pinned by digest ($DIGEST…)"
    }
    val healthChecker = buildpacks.firstOrNull { it.contains(HEALTH_CHECKER_BUILDPACK) }
    if (healthChecker == null) {
        problems += "no health-checker buildpack: add docker.io/$HEALTH_CHECKER_BUILDPACK:<version>"
    } else if (!healthChecker.substringAfter(HEALTH_CHECKER_BUILDPACK).let { it.startsWith(":") || it.startsWith("@") }) {
        problems += "health-checker buildpack $healthChecker has no version"
    }
    return problems
}

@Suppress("UNCHECKED_CAST")
private fun <T> Task.property(getter: String): T = javaClass.getMethod(getter).invoke(this) as T
