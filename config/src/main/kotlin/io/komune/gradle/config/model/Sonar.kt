package io.komune.gradle.config.model

import org.gradle.api.Project

data class Sonar(
	var url: String,
	var organization: String,
	var projectKey: String,
	var jacoco: String?,
	var language: String,
	var detekt: String?,
	val exclusions: String,
	val githubSummaryComment: String?
) {

	companion object
}



fun Sonar.Companion.sonarCloud(project: Project) = Sonar(
	url =  project.propertyOr("sonar.url", "https://sonarcloud.io"),
	organization = project.propertyOr("sonar.organization", ""),
	projectKey = project.propertyOr("sonar.projectKey", ""),
	language = project.propertyOr("sonar.language", "kotlin"),
	detekt = project.propertyOr(
		"sonar.kotlin.detekt.reportPaths",
		"build/reports/detekt/detekt.xml"
	),
	jacoco = project.propertyOr(
		"sonar.jacoco",
		"${project.rootDir}/**/build/reports/jacoco/test/jacocoTestReport.xml"
//		"./build/reports/jacoco/test/jacocoTestReport.xml"
	),
	exclusions = project.propertyOr("sonar.pullrequest.github.summary_comment", "**/*.java") ,
	githubSummaryComment = project.propertyOr("sonar.githubSummaryComment", "true")
)

private fun Project.propertyOr(key: String, default: String) = (System.getenv(key)
	?: findProperty(key)?.toString()
	?: default)
