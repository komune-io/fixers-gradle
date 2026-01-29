plugins {
	`kotlin-dsl`
	id("com.gradle.plugin-publish")
	id("io.komune.fixers.gradle.publishing")
}

dependencies {

	implementation(libs.kotlinGradlePlugin)

	implementation(libs.detektGradlePlugin)
	implementation(libs.jreleaserGradlePlugin)
	implementation(libs.npmPublishGradlePlugin)
	implementation(libs.sonarqubeGradlePlugin)

	// TODO: Remove if build works without - constraint may no longer be needed
	// Force specific version of commons-lang3 (was for npmPublishGradlePlugin, now comes from jreleaser)
	// constraints {
	// 	implementation(libs.commons.lang3)
	// }

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
			description = "Ease the configuration of Kotlin Multiplateform Plugin."
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

tasks.withType<Test> {
	useJUnitPlatform()
}
