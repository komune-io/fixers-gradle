package io.komune.fixers.gradle.config

import io.komune.fixers.gradle.config.model.Bundle
import io.komune.fixers.gradle.config.model.Detekt
import io.komune.fixers.gradle.config.model.Jacoco
import io.komune.fixers.gradle.config.model.Jdk
import io.komune.fixers.gradle.config.model.Kt2Ts
import io.komune.fixers.gradle.config.model.Npm
import io.komune.fixers.gradle.config.model.Publication
import io.komune.fixers.gradle.config.model.PublishConfig
import io.komune.fixers.gradle.config.model.Sonar
import io.komune.fixers.gradle.config.model.sonarCloud
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.maven.MavenPom
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

/**
 * Retrieves the [fixers][io.komune.fixers.gradle.config.ConfigExtension.NAME] extension.
 */
val ExtensionContainer.fixers: ConfigExtension?
    get() = findByName(ConfigExtension.NAME) as? ConfigExtension

/**
 * Configures the [fixers][io.komune.fixers.gradle.config.ConfigExtension.NAME] extension.
 */
fun Project.fixers(configure: Action<ConfigExtension>) {
    this.rootProject.extensions.configure(ConfigExtension.NAME, configure)
}

fun PluginDependenciesSpec.fixers(module: String): PluginDependencySpec = id("io.komune.fixers.gradle.${module}")

/**
 * Main configuration extension for the Fixers Gradle plugins.
 *
 * This class is marked as abstract to allow Gradle to create a dynamic subclass at runtime
 * for property convention mapping and extension instantiation. This is a common pattern
 * in Gradle plugin development, even when the class doesn't have explicit abstract members.
 */
@Suppress("UnnecessaryAbstractClass", "AbstractClassCanBeConcreteClass") // Gradle subclasses it, see above
abstract class ConfigExtension(
    val project: Project
) {
    var bundle: Bundle = Bundle(
        project = project,
        name = project.name
    )

    var kt2Ts: Kt2Ts = Kt2Ts(project)

    var jdk: Jdk = Jdk(project)

    var pom: Publication = Publication(project)

    var npm: Npm = Npm(project)

    var detekt: Detekt = Detekt(project)

    var jacoco: Jacoco = Jacoco(project)

    var sonar: Sonar = Sonar.sonarCloud(project)

    var publish: PublishConfig = PublishConfig(project)

    fun bundle(configure: Action<Bundle>) {
        configure.execute(bundle)
    }

    /** The TypeScript contract of the build, which the root project generates: a subproject's would be ignored. */
    fun kt2Ts(configure: Action<Kt2Ts>) {
        if (project != project.rootProject) {
            throw GradleException("kt2Ts is read from the root project only: move the block of ${project.path} there.")
        }
        configure.execute(kt2Ts)
    }

    fun sonar(configure: Action<Sonar>) {
        configure.execute(sonar)
    }

    fun jdk(configure: Action<Jdk>) {
        configure.execute(jdk)
    }

    fun pom(configure: Action<MavenPom>) {
        pom.configure.set(configure)
    }

    fun npm(configure: Action<Npm>) {
        configure.execute(npm)
    }

    fun detekt(configure: Action<Detekt>) {
        configure.execute(detekt)
    }

    fun jacoco(configure: Action<Jacoco>) {
        configure.execute(jacoco)
    }

    fun publish(configure: Action<PublishConfig>) {
        configure.execute(publish)
    }

    override fun toString(): String = """
        ConfigExtension(
        bundle=$bundle,
        kt2Ts=$kt2Ts,
        jdk=$jdk,
        publication=$pom,
        npm=$npm,
        detekt=$detekt,
        jacoco=$jacoco,
        sonar=$sonar,
        publish=$publish)
    """.trimIndent()

    companion object {
        const val NAME: String = "fixers"
    }
}
