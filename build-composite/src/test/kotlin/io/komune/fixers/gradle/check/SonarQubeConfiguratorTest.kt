package io.komune.fixers.gradle.check

import io.komune.fixers.gradle.config.model.Bundle
import io.komune.fixers.gradle.config.model.Sonar
import io.komune.fixers.gradle.plugin.check.SonarQubeConfigurator
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for SonarQubeConfigurator.
 */
class SonarQubeConfiguratorTest {

    private lateinit var project: Project
    private lateinit var configurator: SonarQubeConfigurator

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().build()
        configurator = SonarQubeConfigurator(project)
    }

    @Nested
    inner class BuildSonarPropertiesTest {

        @Test
        fun `should build properties with all standard fields`() {
            val sonar = Sonar(project).apply {
                organization.set("my-org")
                projectKey.set("my-project")
                url.set("https://sonarcloud.io")
                language.set("kotlin")
                sources.set("src/main")
                exclusions.set("**/generated/**")
                inclusions.set("**/*.kt")
                verbose.set(true)
            }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.organization"]).isEqualTo("my-org")
            assertThat(properties["sonar.projectKey"]).isEqualTo("my-project")
            assertThat(properties["sonar.host.url"]).isEqualTo("https://sonarcloud.io")
            assertThat(properties["sonar.language"]).isEqualTo("kotlin")
            // sources/inclusions must not reach the Gradle scanner: it derives them
            // from module source sets, and setting them causes double indexing.
            assertThat(properties).doesNotContainKey("sonar.sources")
            assertThat(properties).doesNotContainKey("sonar.inclusions")
            assertThat(properties["sonar.exclusions"]).isEqualTo("**/generated/**")
            assertThat(properties["sonar.verbose"]).isEqualTo(true)
        }

        @Test
        fun `should include project name from bundle`() {
            val sonar = Sonar(project).apply {
                organization.set("my-org")
                projectKey.set("my-project")
            }
            val bundle = Bundle(project, "My Test Project")

            val properties = configurator.buildSonarProperties(sonar, bundle)

            assertThat(properties["sonar.projectName"]).isEqualTo("My Test Project")
        }

        @Test
        fun `should include custom properties`() {
            val sonar = Sonar(project).apply {
                organization.set("my-org")
                projectKey.set("my-project")
                properties {
                    property("sonar.coverage.exclusions", "src/generated/**/*")
                    property("sonar.cpd.exclusions", "**/models/**")
                }
            }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.coverage.exclusions"]).isEqualTo("src/generated/**/*")
            assertThat(properties["sonar.cpd.exclusions"]).isEqualTo("**/models/**")
        }

        @Test
        fun `should include jacoco report path`() {
            val sonar = Sonar(project)

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.coverage.jacoco.xmlReportPaths"])
                .isNotNull
                .asString()
                .contains("jacocoTestReport.xml")
        }

        @Test
        fun `should include detekt report path`() {
            val sonar = Sonar(project)

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.kotlin.detekt.reportPaths"])
                .isNotNull
                .asString()
                .contains("merge.xml")
        }

        @Test
        fun `should make detekt report path absolute so every module resolves the root merged report`() {
            val sonar = Sonar(project)

            val properties = configurator.buildSonarProperties(sonar, null)

            val detektPath = properties["sonar.kotlin.detekt.reportPaths"] as String
            assertThat(File(detektPath).isAbsolute).isTrue()
            assertThat(detektPath).isEqualTo(
                File(project.rootDir, "build/reports/detekt/merge.xml").path
            )
        }

        @Test
        fun `should resolve detekt report path of a subproject against the root project`() {
            val subproject = ProjectBuilder.builder().withParent(project).withName("sub").build()
            val subConfigurator = SonarQubeConfigurator(subproject)

            val properties = subConfigurator.buildSonarProperties(Sonar(subproject), null)

            // The merged report only exists in the root build directory: a path relative to the
            // subproject would make Sonar log "Unable to import detekt report file(s)".
            assertThat(properties["sonar.kotlin.detekt.reportPaths"]).isEqualTo(
                File(project.rootDir, "build/reports/detekt/merge.xml").path
            )
        }

        @Test
        fun `should keep an absolute detekt report path unchanged`() {
            val absolutePath = File(System.getProperty("java.io.tmpdir"), "custom/detekt.xml").path
            val sonar = Sonar(project).apply { detekt.set(absolutePath) }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.kotlin.detekt.reportPaths"]).isEqualTo(absolutePath)
        }

        @Test
        fun `should resolve every entry of a comma separated detekt report path`() {
            val sonar = Sonar(project).apply {
                detekt.set("build/reports/detekt/merge.xml, build/reports/detekt/detekt.xml")
            }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.kotlin.detekt.reportPaths"]).isEqualTo(
                listOf("build/reports/detekt/merge.xml", "build/reports/detekt/detekt.xml")
                    .joinToString(",") { File(project.rootDir, it).path }
            )
        }

        @Test
        fun `should match jacoco reports of both the jvm and the multiplatform layout`() {
            val sonar = Sonar(project)
            val pattern = configurator.buildSonarProperties(sonar, null)["sonar.coverage.jacoco.xmlReportPaths"] as String

            // Reproduces how Sonar resolves the pattern: relative to the base directory of each
            // module, with an Ant-style matcher where "**/" also matches zero directories.
            val moduleDir = project.projectDir
            listOf("test", "jvmTest").forEach { testTaskDir ->
                val report = File(moduleDir, "build/reports/jacoco/$testTaskDir/jacocoTestReport.xml")
                report.parentFile.mkdirs()
                report.writeText("<report/>")
            }

            val matched = project.fileTree(moduleDir).matching { include(pattern) }.files.map { it.name }

            assertThat(matched).hasSize(2).containsOnly("jacocoTestReport.xml")
        }

        @Test
        fun `should include github summary comment setting`() {
            val sonar = Sonar(project).apply {
                githubSummaryComment.set("false")
            }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.pullrequest.github.summary_comment"]).isEqualTo("false")
        }

        @Test
        fun `should include sonar token when set via property`() {
            val sonar = Sonar(project).apply {
                organization.set("my-org")
                projectKey.set("my-project")
                token.set("test-sonar-token")
            }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.token"]).isEqualTo("test-sonar-token")
        }

        @Test
        fun `should allow custom properties to override sonar token`() {
            val sonar = Sonar(project).apply {
                organization.set("my-org")
                projectKey.set("my-project")
                token.set("token-from-config")
                properties {
                    property("sonar.token", "token-from-custom")
                }
            }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.token"]).isEqualTo("token-from-custom")
        }

        @Test
        fun `should include detekt config path`() {
            val sonar = Sonar(project).apply {
                detektConfigPath.set("custom-detekt.yml")
            }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["detekt.sonar.kotlin.config.path"])
                .asString()
                .endsWith("custom-detekt.yml")
        }
    }

    @Nested
    inner class ConfigureTest {

        @Test
        fun `should apply sonarqube plugin`() {
            val sonar = Sonar(project).apply {
                organization.set("my-org")
                projectKey.set("my-project")
            }

            configurator.configure(sonar, null)

            assertThat(project.plugins.hasPlugin("org.sonarqube")).isTrue()
        }

        @Test
        fun `should register generateSonarProperties task`() {
            val sonar = Sonar(project).apply {
                organization.set("my-org")
                projectKey.set("my-project")
            }

            configurator.configure(sonar, null)

            assertThat(project.tasks.findByName("generateSonarProperties")).isNotNull
        }

        @Test
        fun `should work with null sonar config`() {
            configurator.configure(null, null)

            assertThat(project.plugins.hasPlugin("org.sonarqube")).isTrue()
            assertThat(project.tasks.findByName("generateSonarProperties")).isNotNull
        }
    }

    @Nested
    inner class ConfigureSonarExtensionTest {

        @Test
        fun `should configure extension with sonar properties via buildSonarProperties`() {
            project.plugins.apply("org.sonarqube")
            val sonar = Sonar(project).apply {
                organization.set("test-org")
                projectKey.set("test-project")
            }

            configurator.configureSonarExtension(sonar, null)

            // Verify properties were set via the delegated buildSonarProperties
            val properties = configurator.buildSonarProperties(sonar, null)
            assertThat(properties["sonar.organization"]).isEqualTo("test-org")
            assertThat(properties["sonar.projectKey"]).isEqualTo("test-project")
        }

        @Test
        fun `should configure extension with bundle only when sonar is null`() {
            project.plugins.apply("org.sonarqube")
            val bundle = Bundle(project, "My Project")

            // Should not throw - only sets projectName from bundle
            configurator.configureSonarExtension(null, bundle)
        }

        @Test
        fun `should configure extension with sonar and bundle`() {
            project.plugins.apply("org.sonarqube")
            val sonar = Sonar(project).apply {
                organization.set("test-org")
                projectKey.set("test-project")
            }
            val bundle = Bundle(project, "My Project")

            configurator.configureSonarExtension(sonar, bundle)

            val properties = configurator.buildSonarProperties(sonar, bundle)
            assertThat(properties["sonar.organization"]).isEqualTo("test-org")
            assertThat(properties["sonar.projectName"]).isEqualTo("My Project")
        }
    }
}
