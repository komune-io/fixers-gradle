# fixers-gradle — Context

A set of Gradle **convention plugins** that standardise how every Komune Kotlin project is built, tested, analysed, and published. Consumers apply one or more `io.komune.fixers.gradle.*` plugins and configure them through a single root-level extension.

## Glossary

### Fixers Config

The top-level Gradle extension declared as `fixers { ... }` in a consumer's root `build.gradle.kts`. It is the single source of truth for project-wide build metadata; all other convention plugins read from it. Backed by the artifact `io.komune.fixers.gradle:config` and applied via the plugin `io.komune.fixers.gradle.config`.

Sub-blocks: `bundle`, `jdk`, `kt2Ts`, `pom`, `repositories`, `sonar`, `detekt`, `jacoco`, `npm`, `publish`.

### Bundle

The Fixers Config sub-block describing **project identity** — name, group, version, description, URL, licensing, SCM, developer info. Maps onto Maven POM coordinates + metadata (the `npm` plugin's `package.json` fields come from the `npm` and `publish` sub-blocks instead). Declared once at root; subprojects inherit it by merge.

### Convention plugin

Each of the seven published plugin ids (`config`, `dependencies`, `kotlin.jvm`, `kotlin.mpp`, `publish`, `npm`, `check`). Applying one wires its concern with sensible defaults, reading from Fixers Config; consumers override only the fields that differ. The implementations live in `build-composite/src/main/kotlin/...` and are copied into `config/`, `dependencies/`, `plugin/` at build time — `build-composite/` is the authoritative source.

### Check

The umbrella for static analysis: **Detekt** (linting), **SonarQube** (analysis publication), **JaCoCo** (coverage). Applied as `io.komune.fixers.gradle.check`; configured via the `sonar`, `detekt`, `jacoco` sub-blocks of Fixers Config.

### Kt2Ts

Kotlin-to-TypeScript declaration handling: the `kt2Ts` sub-block of Fixers Config plus the `tsGen`/`cleanTsGen` tasks registered by `io.komune.fixers.gradle.config`, which copy the `.d.ts` packages emitted by the Kotlin/JS build into an output directory (default `platform/web/kotlin`) and clean them with regex rewrites. The `npm` plugin reuses the same cleaning before NPM publication.

### Publish target

A destination for built artifacts, reached through the two lifecycle tasks registered by `io.komune.fixers.gradle.publish`:

- **`stage`** — publishes everything to **GitHub Packages** (tolerates already-published artifacts)
- **`promote`** — `-SNAPSHOT` versions go to **Maven Central Snapshots**; releases are zipped and uploaded to **Maven Central** via the Central Portal, and Gradle plugins additionally run `publishPlugins` to the **Gradle Plugin Portal**

In-memory PGP signing via `useInMemoryPgpKeys()`; signing tasks are disabled when keys are absent.

### Reusable workflow

The GitHub `workflow_call` workflows in `.github/workflows/` (`make-jvm-workflow.yml`, `make-kotlin-npm-workflow.yml`, `make-nodejs-workflow.yml`, and `mise-*` variants) plus the composite actions in `.github/actions/` (`make-step-prepost`, `version`, `setup-gradle-github-pkg`, ...). Every Komune repo's CI delegates to one of them, passing a `make-file` and optional per-phase task names (`make-lint-task`, `make-build-task`, `make-test-task`, `make-check-task`, `make-stage-task`, `make-promote-task`); the workflow then runs lint → build → test → check → stage → promote, restricting tag builds to the tasks listed in `on-tag` and skipping any task named in `skip-tasks` (e.g. callers pass `skip-tasks: check` on Dependabot runs, where GitHub withholds the secrets check needs). The `mise-*` variants drive `mise` tasks instead of Make targets.

## What this project does NOT define

- Application-level domain (users, files, state machines, etc.) — those belong to consumer submodules.
- The fixers version itself — that is stored in each consumer's `gradle/libs.versions.toml` as `fixers = "X.Y.Z"` (or, for `fixers-d2`, as `val fixersVersion` in `build-composite/build.gradle.kts`).

## Cross-references

Used by every other submodule; see [../../CONTEXT-MAP.md](../../CONTEXT-MAP.md) and [../../docs/adr/0001-submodule-dependency-layers.md](../../docs/adr/0001-submodule-dependency-layers.md).
