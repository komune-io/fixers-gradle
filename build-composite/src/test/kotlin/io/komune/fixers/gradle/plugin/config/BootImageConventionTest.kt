package io.komune.fixers.gradle.plugin.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/** Stands in for Spring Boot's `bootBuildImage`: the convention reaches it by getter name. */
abstract class FakeBootBuildImage : DefaultTask() {
    abstract val builder: Property<String>
    abstract val buildpacks: ListProperty<String>
    abstract val environment: MapProperty<String, String>
}

/** Registered under `org.springframework.boot` by the test resources. */
class FakeBootPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register(BOOT_BUILD_IMAGE_TASK, FakeBootBuildImage::class.java)
    }
}

class BootImageConventionTest {

    private val pinnedBuilder =
        "docker.io/paketobuildpacks/builder-noble-java-tiny:0.0.211@sha256:8da9782949bb9f39f4b9d8079216531fe2791286fe33bbf450e533949cfbcc91"
    private val healthChecker = "docker.io/paketobuildpacks/health-checker:2.14.1"

    @Test
    fun `should accept a builder pinned by digest with a versioned health checker`() {
        assertThat(bootImageProblems(pinnedBuilder, listOf("urn:cnb:builder:paketo-buildpacks/java", healthChecker)))
            .isEmpty()
    }

    @Test
    fun `should reject a missing builder`() {
        assertThat(bootImageProblems(null, listOf(healthChecker))).singleElement().asString().startsWith("no builder")
    }

    @Test
    fun `should reject a builder pinned by tag only`() {
        assertThat(bootImageProblems("paketobuildpacks/builder-jammy-tiny:0.0.553", listOf(healthChecker)))
            .singleElement().asString().contains("is not pinned by digest")
    }

    @Test
    fun `should reject an image without the health checker`() {
        assertThat(bootImageProblems(pinnedBuilder, listOf("urn:cnb:builder:paketo-buildpacks/java")))
            .singleElement().asString().startsWith("no health-checker buildpack")
    }

    @Test
    fun `should reject an unversioned health checker`() {
        assertThat(bootImageProblems(pinnedBuilder, listOf("docker.io/paketobuildpacks/health-checker")))
            .singleElement().asString().contains("has no version")
    }

    @Test
    fun `should accept the health checker in its registry form`() {
        assertThat(
            bootImageProblems(pinnedBuilder, listOf("urn:cnb:registry:paketo-buildpacks/health-checker@2.14.1"))
        ).isEmpty()
    }

    @Test
    fun `should enable the health checker on the image task`() {
        val module = bootModule()
        val task = module.tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage

        assertThat(task.environment.get()).containsEntry(HEALTH_CHECKER_ENABLED, "true")
    }

    @Test
    fun `should leave the configuration and the other tasks alone`() {
        val module = bootModule()
        var realized = false
        module.tasks.register("unrelated") { realized = true }
        (module.tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage).builder.set("unpinned:1")

        (module as ProjectInternal).evaluate()

        assertThat(realized).isFalse()
    }

    @Test
    fun `should fail check on an image that breaks the convention`() {
        val module = bootModule()
        (module.tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage).builder.set("paketobuildpacks/builder-jammy-tiny:0.0.553")
        val check = module.tasks.getByName(BOOT_IMAGE_CHECK_TASK)

        assertThat(module.tasks.getByName("check").taskDependencies.getDependencies(null)).contains(check)
        assertThatThrownBy { check.actions.forEach { it.execute(check) } }
            .hasMessageContaining(":app:bootBuildImage does not follow the fixers image convention")
            .hasMessageContaining("is not pinned by digest")
            .hasMessageContaining("no health-checker buildpack")
    }

    @Test
    fun `should pass check on a conforming image`() {
        val module = bootModule()
        val image = module.tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage
        image.builder.set(pinnedBuilder)
        image.buildpacks.set(listOf(healthChecker))
        val check = module.tasks.getByName(BOOT_IMAGE_CHECK_TASK)

        check.actions.forEach { it.execute(check) }
    }

    @Test
    fun `should refuse to build an image that breaks the convention`() {
        val module = bootModule()
        val image = module.tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage

        assertThatThrownBy { image.actions.forEach { it.execute(image) } }
            .hasMessageContaining("no builder")
    }

    private fun bootModule(): Project {
        // Every project exists before the root build script runs, as in a real build.
        val root = ProjectBuilder.builder().build()
        val module = ProjectBuilder.builder().withParent(root).withName("app").build()
        root.plugins.apply(ConfigPlugin::class.java)
        module.plugins.apply("base")
        module.plugins.apply(SPRING_BOOT_PLUGIN_ID)
        return module
    }
}
