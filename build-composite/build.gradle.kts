plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(kotlin("gradle-plugin", embeddedKotlinVersion))

    implementation(libs.detektGradlePlugin)
    implementation(libs.jreleaserGradlePlugin)
    implementation(libs.npmPublishGradlePlugin)
    implementation(libs.sonarqubeGradlePlugin)

}
