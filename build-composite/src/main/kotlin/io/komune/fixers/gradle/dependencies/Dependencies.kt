package io.komune.fixers.gradle.dependencies

import java.net.URI
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.provider.ProviderFactory

object FixersRepository {
	/**
	 * Configures default repositories with configuration cache compatible environment variable access.
	 * @param repositoryHandler The repository handler to configure
	 * @param providers The provider factory for lazy environment variable access
	 */
	fun defaultRepo(repositoryHandler: RepositoryHandler, providers: ProviderFactory) {
		repositoryHandler.mavenCentral()
		repositoryHandler.maven {
			url = URI("https://maven.pkg.github.com/komune-io/fixers")
			credentials {
				// Use providers.environmentVariable() for configuration cache compatibility
				username = providers.environmentVariable("GITHUB_PKG_MAVEN_USERNAME").orNull
				password = providers.environmentVariable("GITHUB_PKG_MAVEN_TOKEN").orNull
			}
		}
	}

	/**
	 * @deprecated Use defaultRepo(repositoryHandler, providers) instead for configuration cache compatibility
	 */
	@Deprecated(
		message = "Use defaultRepo(repositoryHandler, providers) for configuration cache compatibility",
		replaceWith = ReplaceWith("defaultRepo(repositoryHandler, providers)")
	)
	fun defaultRepo(repositoryHandler: RepositoryHandler) {
		repositoryHandler.mavenCentral()
		repositoryHandler.maven {
			url = URI("https://maven.pkg.github.com/komune-io/fixers")
			credentials {
				username = System.getenv("GITHUB_PKG_MAVEN_USERNAME")
				password = System.getenv("GITHUB_PKG_MAVEN_TOKEN")
			}
		}
	}

}

/**
 * Versions of the plugins and tools used internally by the fixers Gradle plugins.
 * External consumers should rely on f2-bom, c2-bom or s2-bom instead.
 */
object PluginVersions {
	const val kotlin = "2.3.20"
	const val springBoot = "4.0.3"
	const val npmPublish = "3.5.3"
	/**
	 * com.google.devtools.ksp
	 */
	const val ksp = "2.3.6"
	/**
	 * org.graalvm.buildtools.native.gradle.plugin
	 */
	const val graalvm = "0.11.5"
	/**
	 * org.jacoco:jacoco
	 */
	const val jacoco = "0.8.14"

	val fixers = PluginVersions::class.java.`package`.implementationVersion!!
}

/**
 * Versions of the libraries used internally by the fixers Gradle plugins.
 * External consumers should rely on f2-bom, c2-bom or s2-bom instead.
 */
object Versions {
	object Logging {
		const val slf4j = "2.0.17"
	}

	object Spring {
		const val boot = PluginVersions.springBoot
		const val data = "4.0.4"
		const val framework = "7.0.6"
		const val security = "7.0.4"
		const val jakartaPersistence = "3.2.0"
		const val reactor = "3.8.4"
	}

	object Json {
		const val jackson = "3.1.0"
		const val jacksonKotlin = jackson
	}

	object Test {
		const val cucumber = "7.34.3"
		const val junit = "6.0.3"
		const val junitPlatform = "6.0.3"
		const val assertj = "3.27.7"
		object TestContainers {
			const val core = "2.0.3"
			const val deps = "1.21.4"
		}
	}

	object Kotlin {
		const val coroutines = "1.10.2"
		const val serialization = "1.10.0"
		const val datetime = "0.7.1"
		const val ktor = "3.4.1"
	}
}

/**
 * Dependency bundles used internally by the fixers Gradle plugins.
 * External consumers should rely on f2-bom, c2-bom or s2-bom instead.
 */
object Dependencies {
	object Jvm {
		object Json {
			fun jackson(scope: Scope) = scope.add(
					"tools.jackson.module:jackson-module-kotlin:${Versions.Json.jacksonKotlin}"
			)
			fun kSerialization(scope: Scope) = Common.Kotlin.serialization(scope)
		}

		object Logging {
			fun slf4j(scope: Scope) = scope.add(
					"org.slf4j:slf4j-api:${Versions.Logging.slf4j}"
			)
		}

		object Spring {
			fun dataCommons(scope: Scope) = scope.add(
					"jakarta.persistence:jakarta.persistence-api:${Versions.Spring.jakartaPersistence}",
					"org.springframework:spring-context:${Versions.Spring.framework}",
					"org.springframework.data:spring-data-commons:${Versions.Spring.data}"
			)
			fun autoConfigure(scope: Scope, ksp: Scope) = scope.add(
					"org.springframework.boot:spring-boot-autoconfigure:${Versions.Spring.boot}"
			).also {
				ksp.add(
						"org.springframework.boot:spring-boot-configuration-processor:${Versions.Spring.boot}"
				)
			}
		}

		object Kotlin {
			fun coroutines(scope: Scope) = scope.add(
					"org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Kotlin.coroutines}",
					"org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.Kotlin.coroutines}",
					"org.jetbrains.kotlinx:kotlinx-coroutines-reactive:${Versions.Kotlin.coroutines}",
			)
		}

		object Test {
			fun cucumber(scope: Scope) = scope.add(
					"io.cucumber:cucumber-java:${Versions.Test.cucumber}",
					"io.cucumber:cucumber-java8:${Versions.Test.cucumber}",
					"io.cucumber:cucumber-junit-platform-engine:${Versions.Test.cucumber}",
			)

			fun junit(scope: Scope) = scope.add(
					"org.junit.jupiter:junit-jupiter:${Versions.Test.junit}",
					"org.junit.jupiter:junit-jupiter-api:${Versions.Test.junit}",
					"org.junit.platform:junit-platform-suite:${Versions.Test.junitPlatform}",
					"org.assertj:assertj-core:${Versions.Test.assertj}",
					"org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}"
			)
		}
	}

	object Common {
		fun test(scope: Scope) = scope.add(
				"org.jetbrains.kotlin:kotlin-test-common:${PluginVersions.kotlin}",
				"org.jetbrains.kotlin:kotlin-test-annotations-common:${PluginVersions.kotlin}",
				"org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}"
		)

		object Kotlin {
			fun coroutines(scope: Scope) = scope.add(
					"org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Kotlin.coroutines}"
			)

			fun serialization(scope: Scope) = scope.add(
					"org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.Kotlin.serialization}",
					"org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.Kotlin.serialization}"
			)
		}
	}
}

typealias Scope = (dependencyNotation: Any) -> Dependency?

fun Scope.add(vararg deps: String): Scope {
	deps.forEach { this(it) }
	return this
}
