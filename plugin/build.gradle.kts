plugins {
	`kotlin-dsl`
	id("com.gradle.plugin-publish")
	id("io.komune.fixers.gradle.publish")
}

dependencies {

	implementation(libs.kotlinGradlePlugin)

	implementation(libs.detektGradlePlugin)
	implementation(libs.mavenPublishGradlePlugin)
	implementation(libs.npmPublishGradlePlugin)
	implementation(libs.sonarqubeGradlePlugin)
	implementation(libs.kotlinx.coroutines.core)

	api(project(":dependencies"))
	api(project(":config"))

	implementation(libs.bundles.test)
	testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
	website = "https://github.com/komune-io/fixers-gradle"
	vcsUrl = "https://github.com/komune-io/fixers-gradle"
	plugins {
		create("io.komune.fixers.gradle.config") {
			id = "io.komune.fixers.gradle.config"
			implementationClass = "io.komune.fixers.gradle.plugin.config.ConfigPlugin"
			displayName = "Komune FixersGradle Config"
			description = "Convention plugin providing a central DSL to configure Kotlin JVM and Multiplatform projects with sensible defaults."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.dependencies") {
			id = "io.komune.fixers.gradle.dependencies"
			implementationClass = "io.komune.fixers.gradle.plugin.dependencies.DependenciesPlugin"
			displayName = "Komune FixersGradle Dependencies"
			description = "Registers a curated set of dependency versions for Kotlin JVM and Multiplatform projects."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.kotlin.jvm") {
			id = "io.komune.fixers.gradle.kotlin.jvm"
			implementationClass = "io.komune.fixers.gradle.plugin.kotlin.JvmPlugin"
			displayName = "Komune FixersGradle Kotlin JVM"
			description = "Convention plugin that configures Kotlin JVM projects with compiler options, JDK version, and standard dependencies."
			tags = listOf("Komune", "Fixers", "kotlin", "jvm")
		}
		create("io.komune.fixers.gradle.kotlin.mpp") {
			id = "io.komune.fixers.gradle.kotlin.mpp"
			implementationClass = "io.komune.fixers.gradle.plugin.kotlin.MppPlugin"
			displayName = "Komune FixersGradle Kotlin Multiplatform"
			description = "Convention plugin that configures Kotlin Multiplatform projects with JVM, JS, and common targets."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.publish") {
			id = "io.komune.fixers.gradle.publish"
			implementationClass = "io.komune.fixers.gradle.plugin.publish.PublishPlugin"
			displayName = "Komune FixersGradle Publish"
			description = "Convention plugin for publishing artifacts to Maven Central, GitHub Packages, and the Gradle Plugin Portal with GPG signing."
			tags = listOf("Komune", "Fixers", "kotlin", "maven", "publish", "signing")
		}
		create("io.komune.fixers.gradle.npm") {
			id = "io.komune.fixers.gradle.npm"
			implementationClass = "io.komune.fixers.gradle.plugin.npm.NpmPlugin"
			displayName = "Komune FixersGradle NPM"
			description = "Convention plugin for publishing Kotlin/JS modules as NPM packages with TypeScript definition generation."
			tags = listOf("Komune", "Fixers", "kotlin", "npm", "js", "typescript")
		}
		create("io.komune.fixers.gradle.check") {
			id = "io.komune.fixers.gradle.check"
			implementationClass = "io.komune.fixers.gradle.plugin.check.CheckPlugin"
			displayName = "Komune FixersGradle Check"
			description = "Convention plugin that configures Detekt, SonarQube, and JaCoCo for static analysis and code coverage."
			tags = listOf("Komune", "Fixers", "kotlin", "detekt", "sonarqube", "jacoco", "code-quality")
		}
	}
}

// Declare configuration cache compatibility for all plugins
// Uses the Gradle Compatibility Plugin auto-applied by com.gradle.plugin-publish 2.1.1+
gradlePlugin.plugins.configureEach {
	(this as ExtensionAware).extensions.configure<org.gradle.plugin.compatibility.CompatibilityExtension>("compatibility") {
		features {
			configurationCache.set(false)
		}
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
