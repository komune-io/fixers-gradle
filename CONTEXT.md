# fixers-gradle — Context

A set of Gradle **convention plugins** that standardise how every Komune Kotlin project is built, tested, analysed, and published. Consumers apply one or more `io.komune.fixers.gradle.*` plugins and configure them through a single root-level extension.

## Glossary

### Fixers Config

The top-level Gradle extension declared as `fixers { ... }` in a consumer's root `build.gradle.kts`. It is the single source of truth for project-wide build metadata; all other convention plugins read from it. Backed by the artifact `io.komune.fixers.gradle:config` and applied via the plugin `io.komune.fixers.gradle.config`.

Sub-blocks: `bundle`, `jdk`, `repositories`, `sonar`, `detekt`, `jacoco`, `npm`, `publish`.

### Bundle

The Fixers Config sub-block describing **project identity** — name, group, version, description, URL, licensing, SCM, developer info. Maps onto Maven POM coordinates + metadata and onto the `package.json` fields published by the `npm` plugin. One Bundle per root project.

### Convention plugin

Each of the seven published plugin ids (`config`, `dependencies`, `kotlin.jvm`, `kotlin.mpp`, `publish`, `npm`, `check`). Applying one wires its concern with sensible defaults, reading from Fixers Config; consumers override only the fields that differ. The implementations live in `build-composite/src/main/kotlin/...` and are copied into `config/`, `dependencies/`, `plugin/` at build time — `build-composite/` is the authoritative source.

### Check

The umbrella for static analysis: **Detekt** (linting), **SonarQube** (analysis publication), **JaCoCo** (coverage). Applied as `io.komune.fixers.gradle.check`; configured via the `sonar`, `detekt`, `jacoco` sub-blocks of Fixers Config.

### Kt2Ts

Kotlin-to-TypeScript declaration generation, emitted by the Kotlin/JS build when a multiplatform module publishes a JS target. Consumed by `fixers-g2` apps that want TS types for an `f2` client. Not directly user-facing in this project — it's a side-effect of `io.komune.fixers.gradle.kotlin.mpp`.

### Publish target

A destination for built artifacts. Three are configured by `io.komune.fixers.gradle.publish`:

- **GitHub Packages** — snapshot / pre-release JARs
- **Maven Central** (via Central Portal ZIP upload) — release JARs
- **Gradle Plugin Portal** — plugin marker artifacts

In-memory PGP signing via `useInMemoryPgpKeys()`; gracefully skipped when keys are absent.

## What this project does NOT define

- Application-level domain (users, files, state machines, etc.) — those belong to consumer submodules.
- The fixers version itself — that is stored in each consumer's `gradle/libs.versions.toml` as `fixers = "X.Y.Z"` (or, for `fixers-d2`, as `val fixersVersion` in `build-composite/build.gradle.kts`).
- A Gradle wrapper — Komune uses `gradle` from `mise` directly.

## Cross-references

Used by every other submodule; see [../../CONTEXT-MAP.md](../../CONTEXT-MAP.md) and [../../docs/adr/0001-submodule-dependency-layers.md](../../docs/adr/0001-submodule-dependency-layers.md).
