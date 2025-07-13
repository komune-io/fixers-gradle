package io.komune.fixers.gradle.dependencies

import java.net.URI
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.RepositoryHandler

object FixersRepository {
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
	const val kotlin = "1.9.25"
	const val springBoot = "3.3.6"
	const val npmPublish = "3.4.2"
	/**
	 * com.google.devtools.ksp
	 */
	const val ksp = "1.9.25-1.0.20"
	/**
	 * org.graalvm.buildtools.native.gradle.plugin
	 */
	const val graalvm = "0.10.3"

	val fixers = FixersPluginVersions::class.java.`package`.implementationVersion!!
}

object FixersVersions {
	object Logging {
		const val slf4j = "2.0.16"
	}

	object Spring {
		const val boot = FixersPluginVersions.springBoot
		const val data = "3.3.4"
		const val framework = "6.1.13"
		const val security = "6.3.3"
		const val jakartaPersistence = "3.2.0"
		const val reactor = "3.6.10"
	}

	object Json {
		const val jackson = "2.17.2"
		const val jacksonKotlin = jackson
	}

	object Test {
		const val cucumber = "7.19.0"
		const val junit = "5.11.1"
		const val junitPlatform = "1.11.1"
		const val assertj = "3.26.3"
		const val testcontainers = "1.20.2"
	}

	object Kotlin {
		const val coroutines = "1.8.1"
		const val serialization = "1.6.3"
		const val datetime = "0.6.1"
		const val ktor = "2.3.12"
	}
}

object FixersDependencies {
	object Jvm {
		object Json {
			fun jackson(scope: Scope) = scope.add(
					"com.fasterxml.jackson.module:jackson-module-kotlin:${FixersVersions.Json.jacksonKotlin}"
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
