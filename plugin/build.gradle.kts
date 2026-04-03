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
			displayName = "Fixers Gradle Config"
			description = "Ease the configuration of Kotlin Fixers Project."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.dependencies") {
			id = "io.komune.fixers.gradle.dependencies"
			implementationClass = "io.komune.fixers.gradle.plugin.dependencies.DependenciesPlugin"
			displayName = "Fixers Dependencies version"
			description = "Register fixers dependencies version."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.kotlin.jvm") {
			id = "io.komune.fixers.gradle.kotlin.jvm"
			implementationClass = "io.komune.fixers.gradle.plugin.kotlin.JvmPlugin"
			displayName = "Fixers Gradle Kotlin JVM"
			description = "Ease the configuration of Kotlin Jvm Module."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.kotlin.mpp") {
			id = "io.komune.fixers.gradle.kotlin.mpp"
			implementationClass = "io.komune.fixers.gradle.plugin.kotlin.MppPlugin"
			displayName = "Fixers Gradle Kotlin MPP"
			description = "Ease the configuration of Kotlin Multiplatform Plugin."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.publish") {
			id = "io.komune.fixers.gradle.publish"
			implementationClass = "io.komune.fixers.gradle.plugin.publish.PublishPlugin"
			displayName = "Fixers Gradle publish"
			description = "Ease the configuration of maven publication."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.npm") {
			id = "io.komune.fixers.gradle.npm"
			implementationClass = "io.komune.fixers.gradle.plugin.npm.NpmPlugin"
			displayName = "Fixers Gradle publish npm"
			description = "Ease the configuration of npm publication."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
		}
		create("io.komune.fixers.gradle.check") {
			id = "io.komune.fixers.gradle.check"
			implementationClass = "io.komune.fixers.gradle.plugin.check.CheckPlugin"
			displayName = "Fixers Gradle Sonar"
			description = "Ease the configuration of static code analysis with sonarqube and detekt."
			tags = listOf("Komune", "Fixers", "kotlin", "mpp", "jvm", "js", "wasm")
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
