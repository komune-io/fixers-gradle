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

object FixersPluginVersions {
	const val kotlin = "2.3.0"
	const val springBoot = "4.0.1"
	const val npmPublish = "3.5.3"
	/**
	 * com.google.devtools.ksp
	 */
	const val ksp = "2.3.4"
	/**
	 * org.graalvm.buildtools.native.gradle.plugin
	 */
	const val graalvm = "0.11.3"
	/**
	 * org.jacoco:jacoco
	 */
	const val jacoco = "0.8.14"

	val fixers = FixersPluginVersions::class.java.`package`.implementationVersion!!
}

object FixersVersions {
	object Logging {
		const val slf4j = "2.0.17"
	}

	object Spring {
		const val boot = FixersPluginVersions.springBoot
		const val data = "4.0.0"
		const val framework = "7.0.1"
		const val security = "7.0.0"
		const val jakartaPersistence = "3.2.0"
		const val reactor = "3.8.0"
	}

	object Json {
		const val jackson = "3.0.3"
		const val jacksonKotlin = jackson
	}

	object Test {
		const val cucumber = "7.33.0"
		const val junit = "6.0.2"
		const val junitPlatform = "6.0.2"
		const val assertj = "3.27.7"
		@Deprecated("Use FixersVersions.Test.TestContainers.core instead")
		const val testcontainers = "2.0.3"
		object TestContainers {
			val core = "2.0.3"
			val deps = "1.21.4"
		}
	}

	object Kotlin {
		const val coroutines = "1.10.2"
		const val serialization = "1.10.0"
		const val datetime = "0.7.1"
		const val ktor = "3.4.0"
	}
}

object FixersDependencies {
	object Jvm {
		object Json {
			fun jackson(scope: Scope) = scope.add(
					"tools.jackson.module:jackson-module-kotlin:${FixersVersions.Json.jacksonKotlin}"
			)
			fun kSerialization(scope: Scope) = Common.Kotlin.serialization(scope)
		}

		object Logging {
			fun slf4j(scope: Scope) = scope.add(
					"org.slf4j:slf4j-api:${FixersVersions.Logging.slf4j}"
			)
		}

		object Spring {
			fun dataCommons(scope: Scope) = scope.add(
					"jakarta.persistence:jakarta.persistence-api:${FixersVersions.Spring.jakartaPersistence}",
					"org.springframework:spring-context:${FixersVersions.Spring.framework}",
					"org.springframework.data:spring-data-commons:${FixersVersions.Spring.data}"
			)
			fun autoConfigure(scope: Scope, ksp: Scope) = scope.add(
					"org.springframework.boot:spring-boot-autoconfigure:${FixersVersions.Spring.boot}"
			).also {
				ksp.add(
						"org.springframework.boot:spring-boot-configuration-processor:${FixersVersions.Spring.boot}"
				)
			}
		}

		object Kotlin {
			fun coroutines(scope: Scope) = scope.add(
					"org.jetbrains.kotlinx:kotlinx-coroutines-core:${FixersVersions.Kotlin.coroutines}",
					"org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${FixersVersions.Kotlin.coroutines}",
					"org.jetbrains.kotlinx:kotlinx-coroutines-reactive:${FixersVersions.Kotlin.coroutines}",
			)
		}

		object Test {
			fun cucumber(scope: Scope) = scope.add(
					"io.cucumber:cucumber-java:${FixersVersions.Test.cucumber}",
					"io.cucumber:cucumber-java8:${FixersVersions.Test.cucumber}",
					"io.cucumber:cucumber-junit-platform-engine:${FixersVersions.Test.cucumber}",
			)

			fun junit(scope: Scope) = scope.add(
					"org.junit.jupiter:junit-jupiter:${FixersVersions.Test.junit}",
					"org.junit.jupiter:junit-jupiter-api:${FixersVersions.Test.junit}",
					"org.junit.platform:junit-platform-suite:${FixersVersions.Test.junitPlatform}",
					"org.assertj:assertj-core:${FixersVersions.Test.assertj}",
					"org.jetbrains.kotlinx:kotlinx-coroutines-test:${FixersVersions.Kotlin.coroutines}"
			)
		}
	}

	object Common {
		fun test(scope: Scope) = scope.add(
				"org.jetbrains.kotlin:kotlin-test-common:${FixersPluginVersions.kotlin}",
				"org.jetbrains.kotlin:kotlin-test-annotations-common:${FixersPluginVersions.kotlin}",
				"org.jetbrains.kotlinx:kotlinx-coroutines-test:${FixersVersions.Kotlin.coroutines}"
		)

		object Kotlin {
			fun coroutines(scope: Scope) = scope.add(
					"org.jetbrains.kotlinx:kotlinx-coroutines-core:${FixersVersions.Kotlin.coroutines}"
			)

			fun serialization(scope: Scope) = scope.add(
					"org.jetbrains.kotlinx:kotlinx-serialization-core:${FixersVersions.Kotlin.serialization}",
					"org.jetbrains.kotlinx:kotlinx-serialization-json:${FixersVersions.Kotlin.serialization}"
			)
		}
	}
}

typealias Scope = (dependencyNotation: Any) -> Dependency?

fun Scope.add(vararg deps: String): Scope {
	deps.forEach { this(it) }
	return this
}
