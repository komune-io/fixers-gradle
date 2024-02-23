settingsEvaluated { 
   pluginManagement {
        repositories {
	    gradlePluginPortal()
            maven {
                url = uri("https://maven.pkg.github.com/komune-io/fixers")
                credentials {
                    username = System.getenv("PKG_MAVEN_USERNAME")
                    password = System.getenv("PKG_MAVEN_TOKEN")
                }
            }
        }
    }
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/komune-io/fixers")
            credentials {
                username = System.getenv("PKG_MAVEN_USERNAME")
                password = System.getenv("PKG_MAVEN_TOKEN")
            }
        }
    }
}
