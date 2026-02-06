package io.komune.fixers.gradle.check

import io.komune.fixers.gradle.config.model.Bundle
import io.komune.fixers.gradle.config.model.Sonar
import io.komune.fixers.gradle.plugin.check.SonarQubeConfigurator
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
            assertThat(properties["sonar.sources"]).isEqualTo("src/main")
            assertThat(properties["sonar.exclusions"]).isEqualTo("**/generated/**")
            assertThat(properties["sonar.inclusions"]).isEqualTo("**/*.kt")
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
        fun `should include github summary comment setting`() {
            val sonar = Sonar(project).apply {
                githubSummaryComment.set("false")
            }

            val properties = configurator.buildSonarProperties(sonar, null)

            assertThat(properties["sonar.pullrequest.github.summary_comment"]).isEqualTo("false")
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
}
