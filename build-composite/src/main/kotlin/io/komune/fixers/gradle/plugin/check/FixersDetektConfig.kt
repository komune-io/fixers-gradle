package io.komune.fixers.gradle.plugin.check

/**
 * The Detekt configuration of every fixers build, on top of Detekt's defaults with every rule
 * active. Each entry below is an exemption decided once for all of them, with its reason.
 */
internal fun fixersDetektConfig(): String = """
    config:
      validation: true

    comments:
      # No KDoc required on public declarations: a comment says a why or a trap, never the name again.
      UndocumentedPublicClass:
        active: false
      UndocumentedPublicFunction:
        active: false
      UndocumentedPublicProperty:
        active: false

    complexity:
      LongParameterList:
        # A Spring bean receives its dependencies through its constructor.
        ignoreAnnotated:
          - 'Component'
          - 'Configuration'
          - 'ConfigurationProperties'
          - 'Controller'
          - 'Repository'
          - 'RestController'
          - 'Service'

    naming:
      PackageNaming:
        # Lower-case letters and digits, each segment starting with a letter: f2, s2, c2.
        packagePattern: '[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*'
      FunctionNameMaxLength:
        # A test is named by the sentence it checks.
        excludes:
          - '**/test/**'
          - '**/testFixtures/**'
          - '**/commonTest/**'
          - '**/jvmTest/**'
          - '**/jsTest/**'

    style:
      ForbiddenVoid:
        # Spring and Reactor take Void as a type argument (Mono<Void>), which Kotlin cannot replace.
        ignoreUsageInGenerics: true
""".trimIndent() + "\n"
