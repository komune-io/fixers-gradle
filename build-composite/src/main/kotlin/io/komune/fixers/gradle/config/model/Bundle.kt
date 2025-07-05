package io.komune.fixers.gradle.config.model

data class Bundle(
	var name: String,
	var id: String? = null,
	var description: String? = null,
	var version: String? = null,
	var url: String? = null,

	// Signing properties
	var signingKey: String? = System.getenv("GPG_SIGNING_KEY") ?: "",
	var signingPassword: String? = System.getenv("GPG_SIGNING_PASSWORD") ?: "",

	// License properties
	var licenseName: String? = "The Apache Software License, Version 2.0",
	var licenseUrl: String? = "https://www.apache.org/licenses/LICENSE-2.0.txt",
	var licenseDistribution: String? = "repo",

	// Developer properties
	var developerId: String? = "Komune",
	var developerName: String? = "Komune Team",
	var developerOrganization: String? = "Komune",
	var developerOrganizationUrl: String? = "https://komune.io",

	// SCM properties
	var scmConnection: String? = "scm:git:git://github.com/komune-io/fixers-gradle.git",
	var scmDeveloperConnection: String? = "scm:git:ssh://github.com/komune-io/fixers-gradle.git",

	// Publication properties
	var markerPublications: List<String>? = emptyList()
)