// Provides common configuration for publishing
plugins {
    id("java-library")
}

project.extra["signingKey"] = System.getenv("GPG_SIGNING_KEY") ?: ""
project.extra["signingPassword"] = System.getenv("GPG_SIGNING_PASSWORD") ?: ""

project.extra["repo"] = System.getenv("PKG_MAVEN_REPO") // github || sonatype_oss

val repo = project.extra["repo"] as String?
project.extra["repoUsername"] = if (repo == "github") System.getenv("PKG_GITHUB_USERNAME") else System.getenv("PKG_SONATYPE_OSS_USERNAME")
project.extra["repoPassword"] = if (repo == "github") System.getenv("PKG_GITHUB_TOKEN") else System.getenv("PKG_SONATYPE_OSS_TOKEN")

project.extra["githubRepoUrl"] = "https://maven.pkg.github.com/komune-io/${rootProject.name}"
project.extra["releasesRepoUrl"] = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2"
project.extra["snapshotsRepoUrl"] = "https://s01.oss.sonatype.org/content/repositories/snapshots"
project.extra["repoUrl"] = if (repo == "github") 
    project.extra["githubRepoUrl"] 
else 
    if (project.version.toString().endsWith("SNAPSHOT")) project.extra["snapshotsRepoUrl"] else project.extra["releasesRepoUrl"]

// Define sourcesJar and javadocJar tasks
project.extra["sourcesJar"] = tasks.register<Jar>("sourcesJar") {
    from(project.the<SourceSetContainer>().getByName("main").allJava)
    archiveClassifier.set("sources")
}

project.extra["javadocJar"] = tasks.register<Jar>("javadocJar") {
    dependsOn("javadoc")
    from(tasks.named("javadoc").get().outputs)
    archiveClassifier.set("javadoc")
}
