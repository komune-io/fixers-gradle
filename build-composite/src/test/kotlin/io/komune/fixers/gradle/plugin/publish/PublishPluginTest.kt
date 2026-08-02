package io.komune.fixers.gradle.plugin.publish

import groovy.util.Node
import io.komune.fixers.gradle.config.ConfigExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for PublishPlugin: subproject wiring, root lifecycle tasks
 * and POM dependency-version inlining.
 */
class PublishPluginTest {

    private fun rootWithConfig(version: String = "1.0.0"): Pair<Project, ConfigExtension> {
        val root = ProjectBuilder.builder().build()
        root.version = version
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        return root to config
    }

    private fun childOf(root: Project, name: String = "lib"): Project =
        ProjectBuilder.builder().withParent(root).withName(name).build()

    private fun fireProjectsEvaluated(root: Project) {
        val gradle = root.gradle as GradleInternal
        gradle.buildListenerBroadcaster.projectsEvaluated(gradle)
    }

    @Nested
    inner class SubprojectSetup {

        @Test
        fun `should apply maven-publish and signing plugins to subproject`() {
            val (root, _) = rootWithConfig()
            val child = childOf(root)

            child.plugins.apply(PublishPlugin::class.java)

            assertThat(child.plugins.hasPlugin("maven-publish")).isTrue()
            assertThat(child.plugins.hasPlugin("signing")).isTrue()
        }

        @Test
        fun `should configure staging and githubPackages repositories after evaluation`() {
            val (root, config) = rootWithConfig()
            val child = childOf(root)
            child.plugins.apply(PublishPlugin::class.java)

            (child as ProjectInternal).evaluate()

            val publishing = child.extensions.getByType(PublishingExtension::class.java)
            val staging = publishing.repositories.findByName("staging") as MavenArtifactRepository?
            val github = publishing.repositories.findByName("githubPackages") as MavenArtifactRepository?

            assertThat(staging).isNotNull
            assertThat(staging!!.url.toString())
                .contains(config.publish.stagingDirectory.get())
                .contains(root.layout.buildDirectory.get().asFile.name)
            assertThat(github).isNotNull
            assertThat(github!!.url.toString()).isEqualTo(config.publish.githubPackagesUrl.get())
        }

        @Test
        fun `should skip publishing configuration when root has no fixers config`() {
            val root = ProjectBuilder.builder().build()
            val child = childOf(root)
            child.plugins.apply(PublishPlugin::class.java)

            (child as ProjectInternal).evaluate()

            val publishing = child.extensions.getByType(PublishingExtension::class.java)
            assertThat(publishing.repositories.findByName("staging")).isNull()
            assertThat(publishing.repositories.findByName("githubPackages")).isNull()
        }

        @Test
        fun `should disable signing tasks when no signing key is configured`() {
            val (root, _) = rootWithConfig()
            val child = childOf(root)
            child.plugins.apply(PublishPlugin::class.java)
            child.extensions.getByType(PublishingExtension::class.java)
                .publications.create("test", MavenPublication::class.java)

            (child as ProjectInternal).evaluate()

            val signTasks = child.tasks.withType(Sign::class.java)
            assertThat(signTasks).allSatisfy { assertThat(it.enabled).isFalse() }
        }

        @Test
        fun `should disable signing tasks when signing key is empty`() {
            val (root, config) = rootWithConfig()
            config.publish.signingGpgKey.set("")
            config.publish.signingGpgKeyPassword.set("password")
            val child = childOf(root)
            child.plugins.apply(PublishPlugin::class.java)
            child.extensions.getByType(PublishingExtension::class.java)
                .publications.create("test", MavenPublication::class.java)

            (child as ProjectInternal).evaluate()

            val signTasks = child.tasks.withType(Sign::class.java)
            assertThat(signTasks).allSatisfy { assertThat(it.enabled).isFalse() }
        }

        @Test
        fun `should configure signing when key and password are provided`() {
            val (root, config) = rootWithConfig()
            config.publish.signingGpgKey.set("dummy-armored-key")
            config.publish.signingGpgKeyPassword.set("password")
            val child = childOf(root)
            child.plugins.apply(PublishPlugin::class.java)
            child.extensions.getByType(PublishingExtension::class.java)
                .publications.create("test", MavenPublication::class.java)

            (child as ProjectInternal).evaluate()

            val signing = child.extensions.getByType(SigningExtension::class.java)
            assertThat(signing.isRequired).isTrue()
            assertThat(child.tasks.withType(Sign::class.java)).isNotEmpty
        }
    }

    @Nested
    inner class RootLifecycleTasks {

        private fun evaluatedBuild(version: String): Project {
            val (root, _) = rootWithConfig(version)
            val child = childOf(root)
            child.pluginManager.apply("io.komune.fixers.gradle.publish")
            root.plugins.apply(PublishPlugin::class.java)
            (child as ProjectInternal).evaluate()
            fireProjectsEvaluated(root)
            return root
        }

        @Test
        fun `should register cleanStaging stage and promote tasks on root`() {
            val root = evaluatedBuild("1.0.0")

            assertThat(root.tasks.findByName("cleanStaging")).isNotNull
            assertThat(root.tasks.findByName("stage")).isNotNull
            assertThat(root.tasks.findByName("promote")).isNotNull
        }

        @Test
        fun `should route promote to Central Portal for release versions`() {
            val root = evaluatedBuild("1.0.0")

            val promote = root.tasks.getByName("promote")
            assertThat(promote.group).isEqualTo("publishing")
            assertThat(promote.description).contains("Central Portal")
        }

        @Test
        fun `should route promote to Maven Central Snapshots for snapshot versions`() {
            val root = evaluatedBuild("1.0.0-SNAPSHOT")

            val promote = root.tasks.getByName("promote")
            assertThat(promote.description).contains("SNAPSHOT")
        }

        @Test
        fun `should describe stage task as GitHub Packages publication`() {
            val root = evaluatedBuild("1.0.0")

            val stage = root.tasks.getByName("stage")
            assertThat(stage.group).isEqualTo("publishing")
            assertThat(stage.description).contains("GitHub Packages")
        }

        @Test
        fun `should not register lifecycle tasks when no subproject applies the plugin`() {
            val (root, _) = rootWithConfig()
            childOf(root)
            root.plugins.apply(PublishPlugin::class.java)
            fireProjectsEvaluated(root)

            assertThat(root.tasks.findByName("stage")).isNull()
            assertThat(root.tasks.findByName("promote")).isNull()
        }
    }

    @Nested
    inner class GradlePortalCredentialsBridge {

        @Test
        fun `should bridge portal credentials to system properties`() {
            val previousKey = System.getProperty("gradle.publish.key")
            val previousSecret = System.getProperty("gradle.publish.secret")
            System.clearProperty("gradle.publish.key")
            System.clearProperty("gradle.publish.secret")
            try {
                val (root, config) = rootWithConfig()
                config.publish.gradlePortalKey.set("portal-key")
                config.publish.gradlePortalSecret.set("portal-secret")
                root.plugins.apply(PublishPlugin::class.java)

                fireProjectsEvaluated(root)

                assertThat(System.getProperty("gradle.publish.key")).isEqualTo("portal-key")
                assertThat(System.getProperty("gradle.publish.secret")).isEqualTo("portal-secret")
            } finally {
                restoreSystemProperty("gradle.publish.key", previousKey)
                restoreSystemProperty("gradle.publish.secret", previousSecret)
            }
        }

        @Test
        fun `should not override explicitly set system properties`() {
            val previousKey = System.getProperty("gradle.publish.key")
            System.setProperty("gradle.publish.key", "explicit-key")
            try {
                val (root, config) = rootWithConfig()
                config.publish.gradlePortalKey.set("portal-key")
                root.plugins.apply(PublishPlugin::class.java)

                fireProjectsEvaluated(root)

                assertThat(System.getProperty("gradle.publish.key")).isEqualTo("explicit-key")
            } finally {
                restoreSystemProperty("gradle.publish.key", previousKey)
            }
        }

        private fun restoreSystemProperty(key: String, value: String?) {
            if (value == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, value)
            }
        }
    }

    @Nested
    inner class InlineDependencyVersionsTests {

        private fun pomWithDependency(groupId: String, artifactId: String, version: String? = null): Node {
            val root = Node(null, "project")
            val deps = root.appendNode("dependencies")
            val dep = deps.appendNode("dependency")
            dep.appendNode("groupId", groupId)
            dep.appendNode("artifactId", artifactId)
            if (version != null) {
                dep.appendNode("version", version)
            }
            return root
        }

        private fun Node.appendDependencyManagement(vararg entries: Triple<String, String, String>): Node {
            val mgmt = appendNode("dependencyManagement")
            val deps = mgmt.appendNode("dependencies")
            entries.forEach { (groupId, artifactId, version) ->
                val dep = deps.appendNode("dependency")
                dep.appendNode("groupId", groupId)
                dep.appendNode("artifactId", artifactId)
                dep.appendNode("version", version)
            }
            return mgmt
        }

        private fun dependencyVersion(root: Node, artifactId: String): String? {
            val deps = (root.get("dependencies") as groovy.util.NodeList).firstOrNull() as Node? ?: return null
            return deps.children().filterIsInstance<Node>()
                .firstOrNull { dep ->
                    dep.children().filterIsInstance<Node>()
                        .any { it.name() == "artifactId" && it.text() == artifactId }
                }
                ?.children()?.filterIsInstance<Node>()
                ?.firstOrNull { it.name() == "version" }?.text()
        }

        private fun hasDependencyManagement(root: Node): Boolean =
            (root.get("dependencyManagement") as groovy.util.NodeList).isNotEmpty()

        @Test
        fun `should inline version from dependencyManagement and remove the section`() {
            val root = pomWithDependency("io.komune", "lib-a")
            root.appendDependencyManagement(Triple("io.komune", "lib-a", "2.0.0"))

            inlineDependencyVersions(root)

            assertThat(dependencyVersion(root, "lib-a")).isEqualTo("2.0.0")
            assertThat(hasDependencyManagement(root)).isFalse()
        }

        @Test
        fun `should keep explicit dependency versions untouched`() {
            val root = pomWithDependency("io.komune", "lib-a", "1.5.0")
            root.appendDependencyManagement(Triple("io.komune", "lib-a", "2.0.0"))

            inlineDependencyVersions(root)

            assertThat(dependencyVersion(root, "lib-a")).isEqualTo("1.5.0")
        }

        @Test
        fun `should fall back to resolved versions when dependencyManagement has no entry`() {
            val root = pomWithDependency("io.komune", "lib-b")

            inlineDependencyVersions(root, mapOf("io.komune:lib-b" to "3.1.0"))

            assertThat(dependencyVersion(root, "lib-b")).isEqualTo("3.1.0")
        }

        @Test
        fun `should prefer dependencyManagement version over resolved version`() {
            val root = pomWithDependency("io.komune", "lib-a")
            root.appendDependencyManagement(Triple("io.komune", "lib-a", "2.0.0"))

            inlineDependencyVersions(root, mapOf("io.komune:lib-a" to "9.9.9"))

            assertThat(dependencyVersion(root, "lib-a")).isEqualTo("2.0.0")
        }

        @Test
        fun `should ignore BOM imports in dependencyManagement`() {
            val root = pomWithDependency("io.komune", "lib-a")
            val mgmt = root.appendNode("dependencyManagement")
            val deps = mgmt.appendNode("dependencies")
            val bom = deps.appendNode("dependency")
            bom.appendNode("groupId", "io.komune")
            bom.appendNode("artifactId", "lib-a")
            bom.appendNode("version", "1.0.0")
            bom.appendNode("scope", "import")

            inlineDependencyVersions(root)

            assertThat(dependencyVersion(root, "lib-a")).isNull()
            assertThat(hasDependencyManagement(root)).isFalse()
        }

        @Test
        fun `should leave dependency without any known version untouched`() {
            val root = pomWithDependency("io.komune", "lib-unknown")

            inlineDependencyVersions(root, mapOf("io.komune:other" to "1.0.0"))

            assertThat(dependencyVersion(root, "lib-unknown")).isNull()
        }

        @Test
        fun `should handle pom without dependencies section`() {
            val root = Node(null, "project")
            root.appendDependencyManagement(Triple("io.komune", "lib-a", "2.0.0"))

            inlineDependencyVersions(root)

            assertThat(hasDependencyManagement(root)).isFalse()
        }

        @Test
        fun `should handle namespaced node names`() {
            val root = Node(null, "{http://maven.apache.org/POM/4.0.0}project")
            val deps = root.appendNode("{http://maven.apache.org/POM/4.0.0}dependencies")
            val dep = deps.appendNode("{http://maven.apache.org/POM/4.0.0}dependency")
            dep.appendNode("{http://maven.apache.org/POM/4.0.0}groupId", "io.komune")
            dep.appendNode("{http://maven.apache.org/POM/4.0.0}artifactId", "lib-a")

            inlineDependencyVersions(root, mapOf("io.komune:lib-a" to "4.0.0"))

            val version = dep.children().filterIsInstance<Node>()
                .firstOrNull { it.name().toString().endsWith("version") }
            assertThat(version?.text()).isEqualTo("4.0.0")
        }
    }
}
