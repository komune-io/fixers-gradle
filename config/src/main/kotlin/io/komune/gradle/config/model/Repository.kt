package io.komune.gradle.config.model

import java.lang.System.getenv
import java.net.URI
import org.gradle.api.Project

data class Repository(
	val name: String,
	val url: String,
	val username: String,
	val password: String,
) {
	companion object
	fun getUrl(): URI {
		return URI.create(url)
	}
}

fun Repository.Companion.sonatypeOss(project: Project): Repository {
	val version =  getenv("VERSION") ?: project.version.toString().takeIf { it.isNotBlank() } ?: "experimental-SNAPSHOT"
	val url = if(version.endsWith("-SNAPSHOT")) {
		"https://s01.oss.sonatype.org/content/repositories/snapshots"
	} else {
		"https://s01.oss.sonatype.org/service/local/staging/deploy/maven2"
	}
	return Repository(
		name = "sonatype_oss",
		username = getenv("PKG_SONATYPE_OSS_USERNAME") ?:  project.findProperty("sonatype.username").toString(),
		password = getenv("PKG_SONATYPE_OSS_TOKEN") ?: project.findProperty("sonatype.password").toString(),
		url = url
	)
}


fun Repository.Companion.github(project: Project): Repository {
	return Repository(
		name = "github",
		username = getenv("PKG_GITHUB_USERNAME") ?:  project.findProperty("sonatype.username").toString(),
		password = getenv("PKG_GITHUB_TOKEN") ?: project.findProperty("sonatype.password").toString(),
		url = "https://maven.pkg.github.com/komune-io/${project.rootProject.name}"
	)
}
