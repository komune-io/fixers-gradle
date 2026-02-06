plugins {
    `kotlin-dsl`
    jacoco
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
    
    // TODO: Remove if build works without - constraint may no longer be needed
    // Force specific version of commons-lang3 (was for npmPublishGradlePlugin, now comes from jreleaser)
    // constraints {
    //     implementation(libs.commons.lang3)
    // }

    testImplementation(gradleTestKit())
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
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
