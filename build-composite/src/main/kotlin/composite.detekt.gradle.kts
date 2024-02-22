import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  id("io.gitlab.arturbosch.detekt")
}


internal inline val Project.libs get() = the<LibrariesForLibs>()

dependencies {
  detektPlugins(libs.detekt.formatting)
}

detekt {
  config.from(rootDir.resolve("detekt.yml"))
//  buildUponDefaultConfig = true
}

tasks.withType<Detekt> {
  println("//////////////////////////////")
  println("//////////////////////////////")
  println(project.name)
  println(rootDir.resolve("detekt.yml"))
  println("//////////////////////////////")
  println("//////////////////////////////")
//  debug = true
  reports {
    html.required.set(true) // observe findings in your browser with structure and code snippets
    xml.required.set(true) // checkstyle like format mainly for integrations like Jenkins
    txt.required.set(true) // similar to the console output, contains issue signature to manually edit baseline files
    sarif.required.set(true) // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations with Github Code Scanning
  }
}
