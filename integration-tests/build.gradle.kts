plugins {
	`kotlin-dsl`
}

dependencies {
	implementation(project(":plugin"))
	// Pin kotlin-gradle-plugin to the consumer version
	// so withPluginClasspath() injects the correct Kotlin compiler
	implementation(libs.kotlinGradlePlugin)

	implementation(libs.bundles.test)
	testRuntimeOnly(libs.junit.platform.launcher)

	// BaseIntegrationTest, shared with :plugin instead of being duplicated here
	testImplementation(testFixtures(project(":plugin")))
}
