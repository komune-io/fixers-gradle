package io.komune.fixers.gradle.plugin.publish

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Enum representing the deployment type
 */
enum class PkgDeployType {
    PUBLISH, PROMOTE;

	/**
	 * Checks if the package deployment type is PROMOTE.
	 */
	fun isPkgDeployTypePromote(): Boolean {
		return this == PROMOTE
	}

	/**
	 * Checks if the package deployment type is PUBLISH.
	 */
	fun isPkgDeployTypePublish(): Boolean {
		return this == PUBLISH
	}

    companion object {
        fun fromString(value: String?): PkgDeployType {
            return when (value?.uppercase()) {
                "PROMOTE" -> PROMOTE
                "PUBLISH" -> PUBLISH
                else -> PUBLISH // Default value
            }
        }
        fun fromStrings(value: String?): List<PkgDeployType> {
            return value?.split(",")?.map { fromString(it.trim()) } ?: listOf(PUBLISH)
        }
    }
}

/**
 * Enum representing the Maven repository type
 */
enum class PkgMavenRepo {
    GITHUB, MAVEN_CENTRAL, NONE;

	fun isGithubMavenRepo(): Boolean {
		return this == GITHUB
	}

	fun isNotGithubMavenRepo(): Boolean {
		return !isGithubMavenRepo() && this != NONE
	}

	companion object {
        fun fromString(value: String?): PkgMavenRepo {
            return when (value?.uppercase()) {
                "GITHUB" -> GITHUB
                "MAVEN_CENTRAL" -> MAVEN_CENTRAL
                else -> NONE
            }
        }
    }
}

/**
 * Main configuration extension for the Fixers Gradle plugins.
 *
 * This class is marked as abstract to allow Gradle to create a dynamic subclass at runtime
 * for property convention mapping and extension instantiation. This is a common pattern
 * in Gradle plugin development, even when the class doesn't have explicit abstract members.
 */
open class PublishConfiguration(
	private val project: Project
) {
	/**
	 * Maven Central URL for publishing.
	 */
	val mavenCentralUrl: Property<String> = project.objects.property(String::class.java).apply {
		convention("https://central.sonatype.com/api/v1/publisher")
	}

	/**
	 * Maven Snapshots URL for publishing.
	 */
	val mavenSnapshotsUrl: Property<String> = project.objects.property(String::class.java).apply {
		convention("https://central.sonatype.com/repository/maven-snapshots/")
	}

	/**
	 * Gets the package deployment type from environment variables or project properties.
	 * @return The package deployment type, defaulting to PUBLISH if not specified.
	 */
	val pkgDeployTypes: ListProperty<PkgDeployType>
		= project.objects.listProperty(PkgDeployType::class.java).apply {
		convention(project.provider {
			val deployTypeStr = System.getenv("PKG_DEPLOY_TYPE") ?: project.findProperty("PKG_DEPLOY_TYPE")?.toString()
			PkgDeployType.fromStrings(deployTypeStr)
		})
	}

	/**
	 * Gets the Maven repository type from environment variables or project properties.
	 * @return The Maven repository type, defaulting to GITHUB if not specified.
	 */
	val pkgMavenRepo: Property<PkgMavenRepo>
		= project.objects.property(PkgMavenRepo::class.java).apply {
		convention(project.provider {
			val repoTypeStr = System.getenv("PKG_MAVEN_REPO") ?: project.findProperty("PKG_MAVEN_REPO")?.toString()
			PkgMavenRepo.fromString(repoTypeStr)
		})
	}

	/**
	 * Checks if the package deployment type is PROMOTE.
	 */
	val isPkgDeployTypePromote: Provider<Boolean> = project.provider {
		pkgDeployTypes.get().contains(PkgDeployType.PROMOTE)
	}

	/**
	 * Checks if the package deployment type is PUBLISH.
	 */
	val isPkgDeployTypePublish: Provider<Boolean> = project.provider {
		pkgDeployTypes.get().contains(PkgDeployType.PUBLISH)
	}

	/**
	 * Checks if the Maven repository is GitHub.
	 */
	val isGithubMavenRepo: Provider<Boolean> = project.provider {
		pkgMavenRepo.get().isGithubMavenRepo()
	}

	/**
	 * Checks if the Maven repository is not GitHub and not NONE.
	 */
	val isNotGithubMavenRepo: Provider<Boolean> = project.provider {
		pkgMavenRepo.get().isNotGithubMavenRepo()
	}

	/**
	 * Determines if artifacts should be published.
	 * Artifacts are published if the deployment type is PUBLISH or if the repository is GitHub.
	 */
	val isPublish: Provider<Boolean> = project.provider {
		isPkgDeployTypePublish.get() || isGithubMavenRepo.get()
	}

	/**
	 * Determines if artifacts should be promoted.
	 * Artifacts are promoted if the deployment type is PROMOTE or if the repository is not GitHub.
	 */
	val isPromote: Provider<Boolean> = project.provider {
		isPkgDeployTypePromote.get() || isNotGithubMavenRepo.get()
	}

	/**
	 * GitHub username for package publishing.
	 */
	val pkgGithubUsername: Property<String> = project.objects.property(String::class.java).apply {
		convention(project.provider {
			System.getenv("PKG_GITHUB_USERNAME") ?: project.findProperty("PKG_GITHUB_USERNAME")?.toString() ?: ""
		})
	}

	/**
	 * GitHub token for package publishing.
	 */
	val pkgGithubToken: Property<String> = project.objects.property(String::class.java).apply {
		convention(project.provider {
			System.getenv("PKG_GITHUB_TOKEN") ?: project.findProperty("PKG_GITHUB_TOKEN")?.toString() ?: ""
		})
	}

	/**
	 * Searches for the version in the VERSION file or falls back to the project's version.
	 *
	 * @return A provider that resolves to the version string
	 */
	val version: Provider<String> = project.provider {
		val versionFile = project.rootProject.file("VERSION")
		if (versionFile.exists()) {
			versionFile.readText().trim()
		} else {
			project.version.toString()
		}
	}

	val signingKey: Property<String> = project.objects.property(String::class.java).apply {
		set(System.getenv("GPG_SIGNING_KEY") ?: "")
	}

	val signingPassword: Property<String> = project.objects.property(String::class.java).apply {
		set(System.getenv("GPG_SIGNING_PASSWORD") ?: "")
	}
}
