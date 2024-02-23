import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  id("io.gitlab.arturbosch.detekt")
}

val libs = the<LibrariesForLibs>()

val detektReportMergeSarif by tasks.registering(ReportMergeTask::class) {
  output = layout.buildDirectory.file("reports/detekt/merge.sarif")
}

allprojects {

  apply(plugin = "io.gitlab.arturbosch.detekt")

  detekt {
//    buildUponDefaultConfig = true
    config.from(rootDir.resolve("detekt.yml"))
  }

  dependencies {
    detektPlugins(libs.detekt.formatting)
    detektPlugins(libs.bundles.detekt.rules)
  }

  tasks.withType<Detekt>().configureEach {
    reports {
      xml.required = true
      html.required = true
      txt.required = false
      sarif.required = true
      md.required = true
    }
    basePath = rootDir.absolutePath
  }

  detektReportMergeSarif {
    input.from(tasks.withType<Detekt>().map { it.reports.sarif.outputLocation })
  }
}
