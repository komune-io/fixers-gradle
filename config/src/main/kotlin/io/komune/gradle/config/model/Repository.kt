package io.komune.gradle.config.model

import org.gradle.api.Project
import java.lang.System.getenv
import java.net.URI

data class Repository(
	val name: String,
	val username: String,
	val password: String,
) {
	companion object
	fun getUrl(project: Project): URI {
//		val version = project.version.toString().takeIf { it.isNotBlank() } ?: getenv("VERSION") ?: "experimental-SNAPSHOT"
//		credentials {
//			username System.getenv("PKG_MAVEN_USERNAME")
//			password System.getenv("PKG_MAVEN_TOKEN")
//		}
		return URI.create(
			"https://maven.pkg.github.com/komune-io/${project.rootProject.name}"
		)
	}
}

fun Repository.Companion.sonatype(project: Project): Repository {
	return Repository(
		name = "sonatype",
		username = getenv("PKG_MAVEN_USERNAME") ?:  project.findProperty("sonatype.username").toString(),
		password = getenv("PKG_MAVEN_TOKEN") ?: project.findProperty("sonatype.password").toString(),
	)
}
