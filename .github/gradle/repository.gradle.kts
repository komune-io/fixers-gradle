settingsEvaluated { 
   pluginManagement {
        repositories {
            println("//////////////////////////////")
            println(System.getenv("PKG_MAVEN_USERNAME"))
            println("//////////////////////////////")
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
        println("//////////////////////////////")
        println(System.getenv("PKG_MAVEN_USERNAME"))
        println("//////////////////////////////")
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
