plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(kotlin("gradle-plugin", embeddedKotlinVersion))
    implementation(libs.detektGradlePlugin)
}
