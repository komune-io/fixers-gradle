package city.smartb.gradle.dependencies

import org.gradle.api.artifacts.Dependency

object FixersPluginVersions {
	const val kotlin = "1.6.20-RC2"
	const val springBoot = "2.6.6"
	const val npmPublish = "2.1.2"
	const val ksp = "1.6.20-RC2-1.0.4"

	val fixers = FixersPluginVersions::class.java.`package`.implementationVersion!!
}

object FixersVersions {
	object Logging {
		const val slf4j = "1.7.36"
	}

	object Spring {
		const val boot = FixersPluginVersions.springBoot
		const val data = "2.6.3"
		const val framework = "5.3.18"
		const val javaxPersistence = "2.2"
	}

	object Json {
		const val jacksonKotlin = "2.13.2"
	}

	object Test {
		const val cucumber = "7.2.3"
		const val junit = "5.8.2"
		const val junitPlatform = "1.8.2"
		const val assertj = "3.22.0"
		const val testcontainers = "1.16.3"
	}

	object Kotlin {
		const val coroutines = "1.6.0"
		const val serialization = "1.3.2"
		const val ktor = "1.6.8"
	}
}

object FixersDependencies {
	object Jvm {
		object Json {
			fun jackson(scope: Scope) = scope.add(
				"com.fasterxml.jackson.module:jackson-module-kotlin:${FixersVersions.Json.jacksonKotlin}"
			)
			fun kSerialization(scope: Scope) = Common.Kotlin.serialization(scope).also {

			}
		}

		object Logging {
			fun slf4j(scope: Scope) = scope.add(
				"org.slf4j:slf4j-api:${FixersVersions.Logging.slf4j}"
			)
		}

		object Spring {
			fun dataCommons(scope: Scope) = scope.add(
				"javax.persistence:javax.persistence-api:${FixersVersions.Spring.javaxPersistence}",
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

			fun ktorClient(scope: Scope) = scope.add(
				"io.ktor:ktor-client-core:${FixersVersions.Kotlin.ktor}",
				"io.ktor:ktor-client-cio:${FixersVersions.Kotlin.ktor}",
				"io.ktor:ktor-client-auth:${FixersVersions.Kotlin.ktor}",
				"io.ktor:ktor-client-jackson:${FixersVersions.Kotlin.ktor}"
			)
		}

		object Test {
			fun cucumber(scope: Scope) = scope.add(
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
