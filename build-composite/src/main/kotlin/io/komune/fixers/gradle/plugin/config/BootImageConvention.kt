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
internal const val DIGEST = "@sha256:"
internal const val JAVA_BUILDPACK = "urn:cnb:builder:paketo-buildpacks/java"

// The run image is the one the builder names, which it names by tag only.
internal const val DEFAULT_BUILDER = "docker.io/paketobuildpacks/builder-noble-java-tiny:0.0.212" +
    "@sha256:8da9782949bb9f39f4b9d8079216531fe2791286fe33bbf450e533949cfbcc91"
internal const val DEFAULT_RUN_IMAGE = "docker.io/paketobuildpacks/ubuntu-noble-run-tiny:0.0.157" +
    "@sha256:7af66d69f095b46ea8f470751beacbf4eed7a801eb54cee4f18f4f04b94d7e07"
internal const val DEFAULT_HEALTH_CHECKER = "docker.io/paketobuildpacks/health-checker:2.14.2" +
    "@sha256:a9cf9dbff2f1460a6a2e88def93c9d760c79f1412619231a1981ddcab3e002f9"

private const val HEALTH_CHECKER = "health-checker"

/**
 * Every Spring Boot image is built from a builder and a run image pinned by digest, and carries
 * Paketo's health checker, pinned by digest and enabled: the only probe a compose healthcheck can
 * run in the shell-less tiny run image. fixers sets all three; a build may replace them with other
 * images pinned by digest, and `bootBuildImage` refuses to run otherwise. Configuration and the
 * other tasks are left alone.
 *
 * The task is reached by name and its properties by getter: fixers does not put the Spring Boot
 * plugin on its consumers' classpath.
 */
internal fun Project.enforceBootImageConvention() {
    // Boot registers its image task only once `java` is applied, which a build may do before or
    // after applying Boot.
    pluginManager.withPlugin(SPRING_BOOT_PLUGIN_ID) {
        pluginManager.withPlugin("java") { wireBootImageConvention() }
    }
}

private fun Project.wireBootImageConvention() {
    tasks.named(BOOT_BUILD_IMAGE_TASK).configure {
        // Conventions: a build script that sets an image, before or after this, keeps it.
        val builder = property<Property<String>>("getBuilder").apply { convention(DEFAULT_BUILDER) }
        val runImage = property<Property<String>>("getRunImage").apply { convention(DEFAULT_RUN_IMAGE) }
        val buildpacks = property<ListProperty<String>>("getBuildpacks").apply {
            convention(listOf(JAVA_BUILDPACK, DEFAULT_HEALTH_CHECKER))
        }
        val environment = property<MapProperty<String, String>>("getEnvironment").apply {
            put(HEALTH_CHECKER_ENABLED, "true")
        }
        val image = path
        doFirst {
            val problems = bootImageProblems(
                builder.orNull,
                runImage.orNull,
                buildpacks.getOrElse(emptyList()),
                environment.getOrElse(emptyMap()),
            )
            if (problems.isNotEmpty()) {
                throw GradleException(
                    "$image does not follow the fixers image convention:\n" + problems.joinToString("\n") { "  - $it" }
                )
            }
        }
    }
}

/** One message per rule of [enforceBootImageConvention] that an image built from these breaks. */
internal fun bootImageProblems(
    builder: String?,
    runImage: String?,
    buildpacks: List<String>,
    environment: Map<String, String>,
): List<String> {
    val problems = listOfNotNull(
        pinProblem("builder", builder),
        pinProblem("run image", runImage),
    ).toMutableList()
    val healthCheckers = buildpacks.filter { HEALTH_CHECKER in it }
    if (healthCheckers.isEmpty()) {
        problems += "no health-checker buildpack: add $DEFAULT_HEALTH_CHECKER"
    }
    healthCheckers.mapNotNullTo(problems) { pinProblem("health-checker buildpack", it) }
    if (environment[HEALTH_CHECKER_ENABLED] != "true") {
        problems += "$HEALTH_CHECKER_ENABLED must be true: put entries in the environment rather than set it"
    }
    return problems
}

private fun pinProblem(what: String, image: String?): String? = when {
    image.isNullOrBlank() -> "no $what: set one pinned by digest ($DIGEST…)"
    DIGEST !in image -> "$what $image is not pinned by digest ($DIGEST…)"
    else -> null
}

@Suppress("UNCHECKED_CAST")
private fun <T> Task.property(getter: String): T = javaClass.getMethod(getter).invoke(this) as T
