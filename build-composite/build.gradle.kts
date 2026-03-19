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
    implementation(libs.mavenPublishGradlePlugin)
    implementation(libs.npmPublishGradlePlugin)
    implementation(libs.sonarqubeGradlePlugin)
    implementation(libs.kotlinx.coroutines.core)

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
