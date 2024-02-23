package io.komune.gradle.config.model

import org.gradle.api.Project

data class Sonar(
	var login: String,
	var url: String
) {
	companion object
}

fun Sonar.Companion.sonarCloud(project: Project) = Sonar(
	url = "https://sonarcloud.io",
	login = System.getenv("sonar.username") ?: project.findProperty("sonar.username").toString(),
)
