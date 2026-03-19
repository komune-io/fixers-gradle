plugins {
	`kotlin-dsl`
}

dependencies {
	implementation(project(":plugin"))
	// Override kotlin-gradle-plugin to 2.3.10 (consumer version)
	// so withPluginClasspath() injects the correct Kotlin compiler
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")

	implementation(libs.bundles.test)
	testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
	useJUnitPlatform()
}
