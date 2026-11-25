// The images of the Spring Boot image convention, from the FROM lines of the Dockerfile beside this script, which
// Dependabot bumps. Applied by build-composite and config: both compile FixersBootImages.
tasks.register("generateBootImages") {
    // Next to this script, whichever build applies it.
    val dockerfile = checkNotNull(buildscript.sourceFile).resolveSibling("Dockerfile")
    val outputDir = layout.buildDirectory.dir("generated/fixers/boot-images")
    inputs.file(dockerfile)
    outputs.dir(outputDir)
    doLast {
        val from = Regex("""^FROM\s+(\S+)\s+AS\s+(\S+)\s*$""", RegexOption.IGNORE_CASE)
        val images = dockerfile.readLines()
            .mapNotNull { from.find(it.trim())?.destructured }
            .associate { (image, stage) -> stage to if (image.substringBefore('/').contains('.')) image else "docker.io/$image" }
        fun image(stage: String) = images[stage] ?: throw GradleException("$dockerfile has no stage $stage")
        val file = outputDir.get().file("io/komune/fixers/gradle/config/model/FixersBootImages.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package io.komune.fixers.gradle.config.model
            |
            |/**
            | * Spring Boot image convention images generated from build-composite/boot-images/Dockerfile.
            | */
            |object FixersBootImages {
            |    const val builder = "${image("builder")}"
            |    const val runImage = "${image("run-image")}"
            |    const val healthChecker = "${image("health-checker")}"
            |}
            |""".trimMargin()
        )
    }
}

