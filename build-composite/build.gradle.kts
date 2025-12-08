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
    
    // Force specific version of commons-lang3 sub dep of npmPublishGradlePlugin
    constraints {
        implementation(libs.commons.lang3)
    }

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core.specific)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
