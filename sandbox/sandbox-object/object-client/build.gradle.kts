import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec

plugins {
    id("io.komune.fixers.gradle.kotlin.mpp")
    id("io.komune.fixers.gradle.publish")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":sandbox-object:object-domain"))
            }
        }
    }
}

// Type-checks ts-check/ against the committed contract, which kt2Ts made from the compiler's output:
// what Json<T> accepts and what the Kotlin types refuse, under tsc --strict.
val node = the<NodeJsEnvSpec>().executable
val checkTsContract = tasks.register<Exec>("checkTsContract") {
    val tsc = rootProject.layout.buildDirectory.file("js/node_modules/typescript/bin/tsc")
    val tsCheck = rootProject.layout.projectDirectory.dir("ts-check")
    dependsOn(":kotlinNpmInstall")
    mustRunAfter(":tsGen", ":checkTsGen")
    inputs.dir(rootProject.layout.projectDirectory.dir("ts"))
    inputs.dir(tsCheck)
    // tsc --noEmit writes nothing; the stamp lets an unchanged contract and check be up to date.
    val stamp = layout.buildDirectory.file("ts-contract/checked")
    outputs.file(stamp)
    executable = node.get()
    args(tsc.get().asFile.absolutePath, "--project", tsCheck.asFile.absolutePath)
    doLast { stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("tsc --strict passed\n") }
}
tasks.named("check") { dependsOn(checkTsContract) }
