package io.komune.fixers.gradle.plugin.publish

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JReleaserDeployerTest {

    @Test
    fun `parseRepoOwner extracts owner from standard GitHub URL`() {
        assertThat(JReleaserDeployer.parseRepoOwner("https://github.com/komune-io/fixers-gradle"))
            .isEqualTo("komune-io")
    }

    @Test
    fun `parseRepoOwner handles trailing slash`() {
        assertThat(JReleaserDeployer.parseRepoOwner("https://github.com/komune-io/fixers-gradle/"))
            .isEqualTo("komune-io")
    }

    @Test
    fun `parseRepoOwner handles dot git suffix`() {
        assertThat(JReleaserDeployer.parseRepoOwner("https://github.com/komune-io/fixers-gradle.git"))
            .isEqualTo("komune-io")
    }

    @Test
    fun `parseRepoName extracts name from standard GitHub URL`() {
        assertThat(JReleaserDeployer.parseRepoName("https://github.com/komune-io/fixers-gradle"))
            .isEqualTo("fixers-gradle")
    }

    @Test
    fun `parseRepoName handles trailing slash`() {
        assertThat(JReleaserDeployer.parseRepoName("https://github.com/komune-io/fixers-gradle/"))
            .isEqualTo("fixers-gradle")
    }

    @Test
    fun `parseRepoName handles dot git suffix`() {
        assertThat(JReleaserDeployer.parseRepoName("https://github.com/komune-io/fixers-gradle.git"))
            .isEqualTo("fixers-gradle")
    }

    @Test
    fun `parseRepoOwner and parseRepoName are consistent across all fixers repos`() {
        val repos = listOf(
            "https://github.com/komune-io/fixers-gradle",
            "https://github.com/komune-io/fixers-d2",
            "https://github.com/komune-io/fixers-f2",
            "https://github.com/komune-io/fixers-c2",
            "https://github.com/komune-io/fixers-s2",
            "https://github.com/komune-io/fixers-g2",
        )
        for (url in repos) {
            val owner = JReleaserDeployer.parseRepoOwner(url)
            val name = JReleaserDeployer.parseRepoName(url)
            assertThat(owner).describedAs("owner for $url").isEqualTo("komune-io")
            assertThat(name).describedAs("name for $url").startsWith("fixers-")
        }
    }
}
