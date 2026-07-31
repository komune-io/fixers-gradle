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

@Deprecated("Use f2-bom, c2-bom or s2-bom instead")
object FixersPluginVersions {
	const val kotlin = PluginVersions.kotlin
	const val springBoot = PluginVersions.springBoot
	const val npmPublish = PluginVersions.npmPublish
	/**
	 * com.google.devtools.ksp
	 */
	const val ksp = PluginVersions.ksp
	/**
	 * org.graalvm.buildtools.native.gradle.plugin
	 */
	const val graalvm = PluginVersions.graalvm
	/**
	 * org.jacoco:jacoco
	 */
	const val jacoco = PluginVersions.jacoco

	val fixers = PluginVersions.fixers
}

@Deprecated("Use f2-bom, c2-bom or s2-bom instead")
object FixersVersions {
	object Logging {
		const val slf4j = Versions.Logging.slf4j
	}

	object Spring {
		const val boot = Versions.Spring.boot
		const val data = Versions.Spring.data
		const val framework = Versions.Spring.framework
		const val security = Versions.Spring.security
		const val jakartaPersistence = Versions.Spring.jakartaPersistence
		const val reactor = Versions.Spring.reactor
	}

	object Json {
		const val jackson = Versions.Json.jackson
		const val jacksonKotlin = Versions.Json.jacksonKotlin
	}

	object Test {
		const val cucumber = Versions.Test.cucumber
		const val junit = Versions.Test.junit
		const val junitPlatform = Versions.Test.junitPlatform
		const val assertj = Versions.Test.assertj
		object TestContainers {
			const val core = Versions.Test.TestContainers.core
			const val deps = Versions.Test.TestContainers.deps
		}
	}

	object Kotlin {
		const val coroutines = Versions.Kotlin.coroutines
		const val serialization = Versions.Kotlin.serialization
		const val datetime = Versions.Kotlin.datetime
		const val ktor = Versions.Kotlin.ktor
	}
}

@Deprecated("Use f2-bom, c2-bom or s2-bom instead")
object FixersDependencies {
	object Jvm {
		object Json {
			fun jackson(scope: Scope) = Dependencies.Jvm.Json.jackson(scope)
			fun kSerialization(scope: Scope) = Dependencies.Jvm.Json.kSerialization(scope)
		}

		object Logging {
			fun slf4j(scope: Scope) = Dependencies.Jvm.Logging.slf4j(scope)
		}

		object Spring {
			fun dataCommons(scope: Scope) = Dependencies.Jvm.Spring.dataCommons(scope)
			fun autoConfigure(scope: Scope, ksp: Scope) = Dependencies.Jvm.Spring.autoConfigure(scope, ksp)
		}

		object Kotlin {
			fun coroutines(scope: Scope) = Dependencies.Jvm.Kotlin.coroutines(scope)
		}

		object Test {
			fun cucumber(scope: Scope) = Dependencies.Jvm.Test.cucumber(scope)
			fun junit(scope: Scope) = Dependencies.Jvm.Test.junit(scope)
		}
	}

	object Common {
		fun test(scope: Scope) = Dependencies.Common.test(scope)

		object Kotlin {
			fun coroutines(scope: Scope) = Dependencies.Common.Kotlin.coroutines(scope)
			fun serialization(scope: Scope) = Dependencies.Common.Kotlin.serialization(scope)
		}
	}
}

typealias Scope = (dependencyNotation: Any) -> Dependency?

fun Scope.add(vararg deps: String): Scope {
	deps.forEach { this(it) }
	return this
}
