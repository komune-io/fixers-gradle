package io.komune.fixers.gradle.plugin.config

import io.komune.fixers.gradle.plugin.kotlin.JvmPlugin
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Resolves against a local Maven repository holding POM-only BouncyCastle 1.84 and 1.86
 * modules, wired the way the real ones are (bcpkix -> bcutil -> bcprov, bcpg -> bcprov).
 */
class BouncyCastleAlignmentTest {

    @TempDir
    lateinit var repoDir: File

    @Test
    fun `should resolve patched BouncyCastle on KGP's PGP helper configuration`() {
        val project = kotlinJvmSubproject()

        val resolved = project.configurations.getByName(KOTLIN_BOUNCY_CASTLE_CONFIGURATION).resolvedModules()

        assertThat(resolved.filter { it.group == "org.bouncycastle" }.map { "${it.name}:${it.version}" })
            .containsExactlyInAnyOrder(
                "bcprov-jdk18on:$PATCHED_BOUNCY_CASTLE_VERSION",
                "bcpg-jdk18on:$PATCHED_BOUNCY_CASTLE_VERSION",
                "bcpkix-jdk18on:$PATCHED_BOUNCY_CASTLE_VERSION",
                "bcutil-jdk18on:$PATCHED_BOUNCY_CASTLE_VERSION",
            )
    }

    @Test
    fun `should keep the version a project declares itself on other configurations`() {
        val project = kotlinJvmSubproject()
        project.dependencies.add("implementation", "org.bouncycastle:bcprov-jdk18on:1.84")

        val resolved = project.configurations.getByName("compileClasspath").resolvedModules()

        assertThat(resolved.filter { it.name == "bcprov-jdk18on" }.map { it.version })
            .containsExactly("1.84")
    }

    @Test
    fun `should align a Kotlin subproject that applies no fixers plugin`() {
        val root = ProjectBuilder.builder().build()
        val project = ProjectBuilder.builder().withParent(root).withName("plain-kotlin").build()
        root.plugins.apply(ConfigPlugin::class.java)
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("maven-publish")
        project.plugins.apply("signing")
        project.withBouncyCastleRepository()

        val resolved = project.configurations.getByName(KOTLIN_BOUNCY_CASTLE_CONFIGURATION).resolvedModules()

        assertThat(resolved.filter { it.name == "bcprov-jdk18on" }.map { it.version })
            .containsExactly(PATCHED_BOUNCY_CASTLE_VERSION)
    }

    private fun kotlinJvmSubproject(): Project {
        // As in a real build, every project exists before the root applies ConfigPlugin.
        val root = ProjectBuilder.builder().build()
        val project = ProjectBuilder.builder().withParent(root).withName("jvm-lib").build()
        root.plugins.apply(ConfigPlugin::class.java)
        project.plugins.apply(JvmPlugin::class.java)
        // KGP creates its BouncyCastle configuration once publishing/signing is applied.
        project.plugins.apply("maven-publish")
        project.plugins.apply("signing")
        project.withBouncyCastleRepository()
        return project
    }

    private fun Project.withBouncyCastleRepository() {
        listOf("1.84", PATCHED_BOUNCY_CASTLE_VERSION).forEach { version ->
            writePom("bcprov-jdk18on", version)
            writePom("bcutil-jdk18on", version, "bcprov-jdk18on")
            writePom("bcpkix-jdk18on", version, "bcutil-jdk18on")
            writePom("bcpg-jdk18on", version, "bcprov-jdk18on")
        }
        repositories.maven { url = repoDir.toURI() }
    }

    private fun writePom(artifactId: String, version: String, dependsOn: String? = null) {
        val dependency = dependsOn?.let {
            """
            |  <dependencies>
            |    <dependency>
            |      <groupId>org.bouncycastle</groupId>
            |      <artifactId>$it</artifactId>
            |      <version>$version</version>
            |    </dependency>
            |  </dependencies>
            """.trimMargin()
        }.orEmpty()
        val dir = File(repoDir, "org/bouncycastle/$artifactId/$version").apply { mkdirs() }
        File(dir, "$artifactId-$version.pom").writeText(
            """
            |<project xmlns="http://maven.apache.org/POM/4.0.0">
            |  <modelVersion>4.0.0</modelVersion>
            |  <groupId>org.bouncycastle</groupId>
            |  <artifactId>$artifactId</artifactId>
            |  <version>$version</version>
            |$dependency
            |</project>
            """.trimMargin()
        )
    }

    /** Resolved graph only: modules missing from the local repository are left unresolved, not thrown. */
    private fun Configuration.resolvedModules(): List<ModuleVersionIdentifier> =
        incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }
            .filter { it.group.isNotEmpty() && it.version != "unspecified" }
}
