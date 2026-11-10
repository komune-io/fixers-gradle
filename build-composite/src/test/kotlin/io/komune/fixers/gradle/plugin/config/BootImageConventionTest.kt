package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.buildFixersRoot
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
    abstract val runImage: Property<String>
    abstract val buildpacks: ListProperty<String>
    abstract val environment: MapProperty<String, String>
}

/**
 * Registered under `org.springframework.boot` by the test resources. Like Boot, it registers the
 * image task only once `java` is applied, whichever comes first.
 */
class FakeBootPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.withPlugin("java") {
            target.tasks.register(BOOT_BUILD_IMAGE_TASK, FakeBootBuildImage::class.java)
        }
    }
}

class BootImageConventionTest {

    private val digest = "@sha256:8da9782949bb9f39f4b9d8079216531fe2791286fe33bbf450e533949cfbcc91"
    private val pinnedBuilder = "docker.io/paketobuildpacks/builder-noble-java-tiny:0.0.212$digest"
    private val pinnedRunImage = "docker.io/paketobuildpacks/ubuntu-noble-run-tiny:0.0.157$digest"
    private val pinnedHealthChecker = "docker.io/paketobuildpacks/health-checker:2.14.2$digest"
    private val enabled = mapOf(HEALTH_CHECKER_ENABLED to "true")

    private fun problems(
        builder: String? = pinnedBuilder,
        runImage: String? = pinnedRunImage,
        buildpacks: List<String> = listOf(JAVA_BUILDPACK, pinnedHealthChecker),
        environment: Map<String, String> = enabled,
    ) = bootImageProblems(builder, runImage, buildpacks, environment)

    @Test
    fun `should accept a builder, a run image and a health checker pinned by digest`() {
        assertThat(problems()).isEmpty()
    }

    @Test
    fun `should give every image the pinned defaults of fixers`() {
        val image = bootModule().tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage

        assertThat(image.builder.get()).isEqualTo(DEFAULT_BUILDER).contains(DIGEST)
        assertThat(image.runImage.get()).isEqualTo(DEFAULT_RUN_IMAGE).contains(DIGEST)
        assertThat(image.buildpacks.get()).containsExactly(JAVA_BUILDPACK, DEFAULT_HEALTH_CHECKER)
        assertThat(DEFAULT_HEALTH_CHECKER).contains(DIGEST)
        assertThat(image.environment.get()).containsEntry(HEALTH_CHECKER_ENABLED, "true")
        assertThat(problems(image.builder.get(), image.runImage.get(), image.buildpacks.get(), image.environment.get()))
            .isEmpty()
    }

    @Test
    fun `should reject a missing builder`() {
        assertThat(problems(builder = null)).singleElement().asString().startsWith("no builder")
    }

    @Test
    fun `should reject a builder pinned by tag only`() {
        assertThat(problems(builder = "paketobuildpacks/builder-jammy-tiny:0.0.553"))
            .singleElement().asString().contains("builder paketobuildpacks/builder-jammy-tiny:0.0.553 is not pinned by digest")
    }

    @Test
    fun `should reject a missing run image or one pinned by tag only`() {
        assertThat(problems(runImage = null)).singleElement().asString().startsWith("no run image")
        assertThat(problems(runImage = "paketobuildpacks/ubuntu-noble-run-tiny:0.0.157"))
            .singleElement().asString().contains("run image paketobuildpacks/ubuntu-noble-run-tiny:0.0.157 is not pinned by digest")
    }

    @Test
    fun `should reject a digest that is not 64 hex digits, on any buildpack the project adds`() {
        assertThat(problems(builder = "docker.io/paketobuildpacks/builder-noble-java-tiny:0.0.212@sha256:"))
            .singleElement().asString().contains("is not pinned by digest")
        assertThat(problems(buildpacks = listOf(JAVA_BUILDPACK, pinnedHealthChecker, "docker.io/paketobuildpacks/opentelemetry:2.0")))
            .singleElement().asString().contains("buildpack docker.io/paketobuildpacks/opentelemetry:2.0 is not pinned by digest")
    }

    @Test
    fun `should reject another builder on fixers' default run image`() {
        assertThat(problems(builder = pinnedBuilder.replace("noble", "jammy"), runImage = DEFAULT_RUN_IMAGE))
            .singleElement().asString().contains("runs on fixers' default run image")
    }

    @Test
    fun `should reject an image without the health checker`() {
        assertThat(problems(buildpacks = listOf(JAVA_BUILDPACK)))
            .singleElement().asString().startsWith("no health-checker buildpack")
    }

    @Test
    fun `should reject a health checker pinned by tag, or in its registry form`() {
        listOf(
            "docker.io/paketobuildpacks/health-checker:2.14.2",
            "docker.io/paketobuildpacks/health-checker",
            "urn:cnb:registry:paketo-buildpacks/health-checker@2.14.2",
        ).forEach { healthChecker ->
            assertThat(problems(buildpacks = listOf(JAVA_BUILDPACK, healthChecker)))
                .singleElement().asString().contains("buildpack $healthChecker is not pinned by digest")
        }
    }

    @Test
    fun `should reject an environment that does not enable the health checker`() {
        listOf(emptyMap(), mapOf(HEALTH_CHECKER_ENABLED to "false")).forEach { environment ->
            assertThat(problems(environment = environment))
                .singleElement().asString().contains("$HEALTH_CHECKER_ENABLED must be true")
        }
    }

    @Test
    fun `should refuse to build an image whose environment a build script replaced`() {
        val image = bootModule().tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage
        image.environment.set(mapOf("BP_JVM_VERSION" to "21"))

        assertThatThrownBy { image.actions.forEach { it.execute(image) } }
            .hasMessageContaining(":app:bootBuildImage does not follow the fixers image convention")
            .hasMessageContaining("$HEALTH_CHECKER_ENABLED must be true")
    }

    @Test
    fun `should refuse to build an image a build script unpinned`() {
        val image = bootModule().tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage
        image.builder.set("paketobuildpacks/builder-jammy-tiny:0.0.553")
        image.buildpacks.set(listOf(JAVA_BUILDPACK, "docker.io/paketobuildpacks/health-checker:2.14.2"))

        assertThatThrownBy { image.actions.forEach { it.execute(image) } }
            .hasMessageContaining("builder paketobuildpacks/builder-jammy-tiny:0.0.553 is not pinned by digest")
            .hasMessageContaining("buildpack docker.io/paketobuildpacks/health-checker:2.14.2 is not pinned")
    }

    @Test
    fun `should register no image check task and leave check alone`() {
        val module = bootModule()

        assertThat(module.tasks.names).doesNotContain("checkBootImage")
        assertThat(module.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
            .doesNotContain(BOOT_BUILD_IMAGE_TASK)
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
    fun `should wire the convention whether java comes before or after Spring Boot`() {
        listOf(true, false).forEach { javaFirst ->
            val image = bootModule(javaFirst).tasks.getByName(BOOT_BUILD_IMAGE_TASK) as FakeBootBuildImage

            assertThat(image.environment.get()).containsEntry(HEALTH_CHECKER_ENABLED, "true")
            assertThat(image.builder.get()).isEqualTo(DEFAULT_BUILDER)
        }
    }

    private fun bootModule(javaFirst: Boolean = false): Project {
        // Every project exists before the root build script runs, as in a real build.
        val root = ProjectBuilder.builder().buildFixersRoot()
        val module = ProjectBuilder.builder().withParent(root).withName("app").build()
        root.plugins.apply(ConfigPlugin::class.java)
        if (javaFirst) module.plugins.apply("java")
        module.plugins.apply(SPRING_BOOT_PLUGIN_ID)
        if (!javaFirst) module.plugins.apply("java")
        return module
    }
}
