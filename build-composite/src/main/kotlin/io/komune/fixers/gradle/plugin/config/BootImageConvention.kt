package io.komune.fixers.gradle.plugin.config

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal const val SPRING_BOOT_PLUGIN_ID = "org.springframework.boot"
internal const val BOOT_BUILD_IMAGE_TASK = "bootBuildImage"
internal const val HEALTH_CHECKER_ENABLED = "BP_HEALTH_CHECKER_ENABLED"
internal const val BOOT_IMAGE_CHECK_TASK = "checkBootImage"

private val HEALTH_CHECKER_BUILDPACKS = listOf("paketobuildpacks/health-checker", "paketo-buildpacks/health-checker")
private const val DIGEST = "@sha256:"
private val VERSION_SEPARATORS = setOf(':', '@')

/**
 * Every Spring Boot image is built from a builder pinned by digest and carries Paketo's health
 * checker, the only probe a compose healthcheck can run in the shell-less tiny run image.
 * Versions stay with the project. A module that leaves either out fails `check`, and its
 * `bootBuildImage` refuses to run; configuration and the other tasks are left alone.
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
    val image = tasks.named(BOOT_BUILD_IMAGE_TASK)
    image.configure {
        property<MapProperty<String, String>>("getEnvironment").put(HEALTH_CHECKER_ENABLED, "true")
        doFirst {
            failOnBootImageProblems(
                imageTask = path,
                builder = property<Property<String>>("getBuilder").orNull,
                buildpacks = property<ListProperty<String>>("getBuildpacks").getOrElse(emptyList()),
            )
        }
    }
    val check = tasks.register(BOOT_IMAGE_CHECK_TASK, BootImageCheckTask::class.java) {
        group = "verification"
        description = "Fails when $BOOT_BUILD_IMAGE_TASK has no builder pinned by digest or no health checker."
        imageTask.set(image.map { task -> task.path })
        builder.set(image.flatMap { task -> task.property<Property<String>>("getBuilder") })
        buildpacks.set(image.flatMap { task -> task.property<ListProperty<String>>("getBuildpacks") })
    }
    tasks.named("check") { dependsOn(check) }
}

/** The convention as a verification, so that `check` and `build` fail before an image is built. */
@DisableCachingByDefault(because = "A verification that produces no output")
abstract class BootImageCheckTask : DefaultTask() {

    @get:Input
    abstract val imageTask: Property<String>

    @get:Input
    @get:Optional
    abstract val builder: Property<String>

    @get:Input
    abstract val buildpacks: ListProperty<String>

    @TaskAction
    fun check() {
        failOnBootImageProblems(imageTask.get(), builder.orNull, buildpacks.getOrElse(emptyList()))
    }
}

private fun failOnBootImageProblems(imageTask: String, builder: String?, buildpacks: List<String>) {
    val problems = bootImageProblems(builder, buildpacks)
    if (problems.isNotEmpty()) {
        throw GradleException(
            "$imageTask does not follow the fixers image convention:\n" +
                problems.joinToString("\n") { "  - $it" }
        )
    }
}

/** One message per rule of [enforceBootImageConvention] that [builder] and [buildpacks] break. */
internal fun bootImageProblems(builder: String?, buildpacks: List<String>): List<String> {
    val problems = mutableListOf<String>()
    if (builder.isNullOrBlank()) {
        problems += "no builder: set one pinned by digest, " +
            "e.g. docker.io/paketobuildpacks/builder-noble-java-tiny:<version>@sha256:<digest>"
    } else if (!builder.contains(DIGEST)) {
        problems += "builder $builder is not pinned by digest ($DIGEST…)"
    }
    val healthChecker = buildpacks.firstNotNullOfOrNull { buildpack ->
        HEALTH_CHECKER_BUILDPACKS.firstOrNull { it in buildpack }
            ?.let { name -> buildpack to buildpack.substringAfter(name) }
    }
    if (healthChecker == null) {
        problems += "no health-checker buildpack: add docker.io/${HEALTH_CHECKER_BUILDPACKS.first()}:<version>"
    } else if (healthChecker.second.firstOrNull() !in VERSION_SEPARATORS) {
        problems += "health-checker buildpack ${healthChecker.first} has no version"
    }
    return problems
}

@Suppress("UNCHECKED_CAST")
private fun <T> Task.property(getter: String): T = javaClass.getMethod(getter).invoke(this) as T
