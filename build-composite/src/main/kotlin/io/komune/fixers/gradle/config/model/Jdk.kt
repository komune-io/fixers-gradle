package io.komune.fixers.gradle.config.model

data class Jdk(
	var version: Int? = null,
) {
	companion object {
		val VERSION_DEFAULT = 17
	}
}