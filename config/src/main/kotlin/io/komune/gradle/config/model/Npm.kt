package io.komune.gradle.config.model

data class Npm(
	var publish: Boolean = true,
	var organization: String = "komune-io",
	var clean: Boolean = true,
	var version: String? = null,
)
