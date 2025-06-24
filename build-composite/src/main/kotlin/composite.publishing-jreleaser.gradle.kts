import org.jreleaser.model.Signing

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("composite.pom")
    id("composite.publishing-common")
    id("org.jreleaser")
}


val versionFile = rootProject.file("VERSION")
val versionFromFile = if (versionFile.exists()) {
    versionFile.readText().trim()
} else {
    null
}

if (!versionFromFile.isNullOrEmpty()) {
    project.version = versionFromFile
}


jreleaser {
    project {
        version = versionFromFile ?: project.version.toString()
    }
    signing {
        active.set(org.jreleaser.model.Active.NEVER)
        armored.set(true)
        mode.set(Signing.Mode.COSIGN)
    }

    deploy {
        maven {
            mavenCentral {
                create("MAVENCENTRAL") {
                    active.set(org.jreleaser.model.Active.RELEASE)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)
                }
            }
            nexus2 {
                create("SNAPSHOT") {
                    active.set(org.jreleaser.model.Active.SNAPSHOT)
                    url.set("https://central.sonatype.com/repository/maven-snapshots/")
                    snapshotUrl = "https://central.sonatype.com/repository/maven-snapshots/"
                    applyMavenCentralRules = true
                    snapshotSupported = true
                    closeRepository = true
                    releaseRepository = true
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)
                }
            }
        }
    }

    gitRootSearch.set(true)
    release {
        github {
            skipRelease.set(true)
        }
    }
}

tasks.register("deploy") {
    group = "publishing"
    description = "Publishes all plugin marker artifacts (skipping JReleaser due to configuration issues)"
    dependsOn("publish", "jreleaserDeploy")

}
