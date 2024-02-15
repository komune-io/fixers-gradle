rootProject.name = "fixers-gradle"

pluginManagement {
	repositories {
		gradlePluginPortal()
	}
}

plugins {
	id("com.gradle.enterprise") version "3.16.2"
}


include("config")
include("dependencies")
include("plugin")

gradleEnterprise {
	if (System.getenv("CI") != null) {
		buildScan {
			publishAlways()
			termsOfServiceUrl = "https://gradle.com/terms-of-service"
			termsOfServiceAgree = "yes"
		}
	}
}