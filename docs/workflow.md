# Workflows and Actions

This document provides an overview of the GitHub Actions workflows and reusable actions defined in the `.github` directory.

## Table of Contents

- [Build Steps](#build-steps)
- [Reusable Workflows](#reusable-workflows)
  - [make-jvm-workflow.yml](#make-jvm-workflowyml)
  - [make-kotlin-npm-workflow.yml](#make-kotlin-npm-workflowyml)
  - [make-nodejs-workflow.yml](#make-nodejs-workflowyml)
  - [publish-storybook-workflow.yml](#publish-storybook-workflowyml)
  - [release-workflow.yml](#release-workflowyml)
  - [sec-workflow.yml](#sec-workflowyml)
- [Actions](#actions)
  - [chromatic](#chromatic)
  - [github-page](#github-page)
  - [jvm](#jvm)
  - [make-step-optional](#make-step-optional)
  - [make-step-prepost](#make-step-prepost)
  - [nodejs](#nodejs)
  - [setup-gradle-github-pkg](#setup-gradle-github-pkg)
  - [setup-npm-github-pkg](#setup-npm-github-pkg)
  - [version](#version)

## Build Steps

The build process is divided into several steps, each with a specific purpose. The steps are defined in the Makefiles and are executed in a specific order to ensure that the project is built, tested, and published correctly.

- **lint**: This step checks the code for style and formatting errors.
- **build**: This step compiles the code and creates the artifacts.
- **test**: This step runs the tests to ensure that the code is working correctly.
- **publish**: This step publishes the artifacts to a package repository.
- **promote**: This step promotes the artifacts to a production environment.

## Reusable Workflows

### [make-jvm-workflow.yml](./.github/workflows/make-jvm-workflow.yml)

A reusable workflow for JVM-based projects. It sets up Java and Gradle, then runs linting, building, testing, and publishing tasks defined in a Makefile.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `java-version` | The Java version to use. | `17` |
| `with-gradle-build-scan-publish` | Flag to enable gradle build scan publish | `true` |
| `with-docker-registry-login` | Flag to enable Docker login to the Docker registry. | `false` |
| `excluded-versioning-branch` | A single branch name prefix where the job should be excluded from running. | `versioning/` |
| `make-file` | The Makefile to execute. | `Makefile` |
| `make-lint-task` | The Make command to execute for linting. | `lint` |
| `make-build-task` | The Make command to execute for building. | `build` |
| `make-test-task` | The Make command to execute for testing. | `test` |
| `make-publish-task` | The Make command to execute for publishing. | `publish` |
| `make-check-task` | The Make command to execute for checking. | `check` |
| `make-promote-task` | The Make command to execute for promote. | `promote` |
| `docker-buildx-platform` | Specify the target platforms for Docker buildx. | |
| `docker-publish-repository` | The Docker registry to which images should be published. | `ghcr.io` |
| `force-publish` | Flag to force execute publish task. | `false` |
| `docker-promote-repository` | The Docker registry to which images should be promoted. | `docker.io` |
| `artifact-name` | The name for the uploaded artifact, if needed. | |
| `artifact-path` | The path to the artifact to upload, if needed. | |
| `on-tag` | The path to the artifact to upload, if needed. | `false` |

**Steps:**

| Name | Step | Description |
| --- | --- | --- |
| Checkout Repository | `setup` | Checks out the repository. |
| Add Firefox to Run Kotlin test | `setup` | Installs Firefox for Kotlin tests. |
| Initialize Java and Gradle Environment | `setup` | Sets up the Java and Gradle environment. |
| Get Version from File | `setup` | Retrieves the version from the VERSION file. |
| Execute Make Lint Task | `lint` | Runs the linting task. |
| Setup Docker Buildx for Specified Platforms | `setup` | Sets up Docker Buildx for multi-platform builds. |
| Execute Make Build Task | `build` | Runs the build task. |
| Execute Make Test Task | `test` | Runs the test task. |
| Docker Registry Login to Publish | `publish` | Logs in to the Docker registry for publishing. |
| Execute Make Publish Task | `publish` | Runs the publish task. |
| Execute Make Check Task | `check` | Runs the check task. |
| Docker Registry Login To Promote | `promote` | Logs in to the Docker registry for promotion. |
| Execute Make Promote Task | `promote` | Runs the promote task. |
| Upload Artifact if Specified | `upload` | Uploads the build artifact. |

### [make-kotlin-npm-workflow.yml](./.github/workflows/make-kotlin-npm-workflow.yml)

A reusable workflow for Kotlin/JS projects that are published to NPM. It sets up Java, Gradle, and Node.js, then runs linting, building, testing, and publishing tasks defined in a Makefile.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `java-version` | The Java version to use. | `17` |
| `with-gradle-build-scan-publish` | Flag to enable gradle build scan publish | `true` |
| `make-file` | The Makefile to execute. | `Makefile` |
| `make-lint-task` | The Make command to execute for linting. | `lint` |
| `make-build-task` | The Make command to execute for building. | `build` |
| `make-test-task` | The Make command to execute for testing. | `test` |
| `make-publish-task` | The Make command to execute for publishing. | `publish` |
| `make-promote-task` | The Make command to execute for promote. | `promote` |
| `artifact-name` | The name for the uploaded artifact, if needed. | |
| `artifact-path` | The path to the artifact to upload, if needed. | |
| `version-with-snapshot-tag` | Base directory of the Node.js project, if applicable. | `.${GITHUB_SHA:0:7}` |
| `on-tag` | The path to the artifact to upload, if needed. | `false` |

**Steps:**

| Name | Step | Description |
| --- | --- | --- |
| Checkout Repository | `setup` | Checks out the repository. |
| Initialize Java and Gradle Environment | `setup` | Sets up the Java and Gradle environment. |
| Get Version from File | `setup` | Retrieves the version from the VERSION file. |
| Execute Make Lint Task | `lint` | Runs the linting task. |
| Setup Docker Buildx for Specified Platforms | `setup` | Sets up Docker Buildx for multi-platform builds. |
| Execute Make Build Task | `build` | Runs the build task. |
| Execute Make Test Task | `test` | Runs the test task. |
| Execute Make Publish Task | `publish` | Runs the publish task. |
| Execute Make Check Task | `check` | Runs the check task. |
| Execute Make Promote Task | `promote` | Runs the promote task. |
| Upload Artifact if Specified | `upload` | Uploads the build artifact. |

### [make-nodejs-workflow.yml](./.github/workflows/make-nodejs-workflow.yml)

A reusable workflow for Node.js projects. It sets up Node.js, then runs linting, building, testing, and publishing tasks defined in a Makefile.

**Inputs:**

| Name                         | Description | Default |
|------------------------------| --- | --- |
| `node-version`               | The version of Node.js to use. | |
| `with-setup-npm-github-pkg`  | Flag to enable Npm setup with GitHub Packages. | `true` |
| `with-docker-registry-login` | Flag to enable Docker login to the Docker registry. | `true` |
| `excluded-versioning-branch` | A single branch name prefix where the job should be excluded from running. | `versioning/` |
| `version-with-snapshot-tag`  | Base directory of the Node.js project, if applicable. | `.${GITHUB_SHA:0:7}` |
| `make-file`                  | The Makefile to execute. | `Makefile` |
| `base-dir`                   | The base directory of the Node.js project. | |
| `make-lint-task`             | The Make command to execute for linting. | `lint` |
| `make-build-task`            | The Make command to execute for building. | `build` |
| `make-test-task`             | The Make command to execute for testing. | `test` |
| `make-stage-task`            | The Make command to execute for publishing. | `publish` |
| `make-promote-task`          | The Make command to execute for promoting. | `promote` |
| `docker-stage-repository`    | The Docker registry to which images should be published. | `ghcr.io` |
| `docker-promote-repository`  | The Docker registry to which images should be promoted. | `docker.io` |
| `docker-buildx-platform`     | Specify the target platforms for Docker buildx. | |
| `artifact-name`              | The name for the uploaded artifact, if needed. | |
| `artifact-path`              | The path to the artifact to upload, if needed. | |
| `on-tag`                     | Comma-separated list of task names that should be executed when a tag is pushed. | `false` |

**Steps:**

| Name                                        | Step      | Description                                      |
|---------------------------------------------|-----------|--------------------------------------------------|
| Checkout Repository                         | `setup`   | Checks out the repository.                       |
| Initialize Node.js Environment              | `setup`   | Sets up the Node.js environment.                 |
| Setup Npm with GitHub Packages              | `setup`   | Configures NPM to use GitHub Packages.           |
| Docker Registry Login                       | `setup`   | Logs in to the Docker registry.                  |
| Get Version from File                       | `setup`   | Retrieves the version from the VERSION file.     |
| Execute Make Lint Task                      | `lint`    | Runs the linting task.                           |
| Setup Docker Buildx for Specified Platforms | `setup`   | Sets up Docker Buildx for multi-platform builds. |
| Execute Make Build Task                     | `build`   | Runs the build task.                             |
| Execute Make Test Task                      | `test`    | Runs the test task.                              |
| Docker Registry Login to stage              | `stage`   | Logs in to the Docker registry for publishing.   |
| Execute Make Stage Task                     | `stage`   | Runs the stage task.                             |
| Docker Registry Login To Promote            | `promote` | Logs in to the Docker registry for promotion.    |
| Setup Npm with NpmJs Repository             | `promote` | Configures NPM to use the npmjs repository.      |
| Execute Make Check Task                     | `check`   | Runs the check task.                             |
| Execute Make Promote Task                   | `promote` | Runs the promote task.                           |
| Upload Artifact if Specified                | `upload`  | Uploads the build artifact.                      |

### [publish-storybook-workflow.yml](./.github/workflows/publish-storybook-workflow.yml)

A reusable workflow for publishing Storybook. It uses the `make-nodejs-workflow.yml` to build the Storybook, then publishes it to Chromatic and GitHub Pages.

**Inputs:**

| Name                             | Description | Default |
|----------------------------------| --- | --- |
| `node-version`                   | The Node.js version to use for the workflow. | |
| `with-chromatic`                 | Determines whether to publish the Storybook to Chromatic. | `false` |
| `with-setup-npm-github-pkg`      | Flag to enable Npm setup with GitHub Packages. | `true` |
| `storybook-dir`                  | The base directory where the Storybook configuration resides. | `storybook` |
| `docker-stage-repository`        | The Docker registry to which images should be published. | `ghcr.io` |
| `docker-promote-repository`      | The Docker registry to which images should be promoted. | `docker.io` |
| `storybook-static-dir-separator` | The directory path where the static Storybook build will be placed. | `/` |
| `storybook-static-dir`           | The directory path where the static Storybook build will be placed. | `storybook-static` |
| `on-tag`                         | The path to the artifact to upload, if needed. | `false` |
| `make-file`                      | The Makefile to execute. | `infra/script/make_docs.mk` |

**Jobs:**

| Name | Step | Description |
| --- | --- | --- |
| package-storybook | `build` | Packages the Storybook application. |
| publish-chromatic | `publish` | Publishes the Storybook to Chromatic. |
| publish-github-page | `publish` | Publishes the Storybook to GitHub Pages. |

### [release-workflow.yml](./.github/workflows/release-workflow.yml)

This workflow is triggered manually. It creates a new release branch, updates the version, creates a tag, and then creates a pull request to merge the release branch back into the source branch.

**Inputs:**

| Name | Description |
| --- | --- |
| `version` | Version to release (e.g., 1.0.0) |
| `next_version` | Next development version (e.g., 1.0.1-dev) |

**Steps:**

| Name | Step | Description |
| --- | --- | --- |
| Print Env | `setup` | Prints the environment variables. |
| Set up Git | `setup` | Configures Git. |
| Checkout repository | `setup` | Checks out the repository. |
| Create new release branch | `release` | Creates a new release branch. |
| Update VERSION file with new version | `release` | Updates the VERSION file with the new version. |
| Commit updated VERSION file | `release` | Commits the updated VERSION file. |
| Create a new Git tag | `release` | Creates a new Git tag. |
| Update VERSION file to next development version | `release` | Updates the VERSION file to the next development version. |
| Commit next development version | `release` | Commits the next development version. |
| Create Pull Request via GitHub API | `release` | Creates a pull request to merge the release branch. |

### [sec-workflow.yml](./.github/workflows/sec-workflow.yml)

A reusable workflow for security analysis. It runs Gradle dependency analysis and SonarCloud analysis.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `java-distribution` | Java distribution. | `temurin` |
| `java-version` | Java version | `17` |
| `with-gradle-build-scan-publish` | Flag to enable gradle build scan publish | `true` |

**Jobs:**

| Name | Step | Description |
| --- | --- | --- |
| gradle-dependency-analysis | `scan` | Submits the dependency graph to Gradle Enterprise. |
| sonarcloud-analysis | `scan` | Runs a SonarCloud analysis. |

---
## Actions

### [chromatic](./.github/actions/chromatic/action.yml)

This action deploys a Storybook to Chromatic for visual regression testing.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `storybook-dir` | Storybook base directory | |
| `storybook-static-dir` | Path to the storybook-static | |
| `project-token` | Path to the artifact to upload, leave empty if no artifact is needed | |

### [github-page](./.github/actions/github-page/action.yml)

This action deploys a static website to GitHub Pages.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `artifact-name` | Name for the uploaded artifact, leave empty if no artifact is needed | `storybook-static` |
| `artifact-path` | Path to the artifact to upload, leave empty if no artifact is needed | `storybook-static` |

### [jvm](./.github/actions/jvm/action.yml)

This action sets up a Java and Gradle environment.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `java-distribution` | Java distribution (e.g., OpenJDK or Temurin) | `temurin` |
| `java-version` | Java version (e.g., 11, 17) | `17` |
| `with-gradle` | Flag to initialize Gradle | `true` |
| `with-gradle-build-scan-publish` | Flag to enable gradle build scan publish | `true` |
| `with-setup-gradle-github-pkg` | Flag to set up Gradle with GitHub Packages | `true` |

### [make-step-optional](./.github/actions/make-step-optional/action.yml)

This action runs a Makefile step only if it exists.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `make-file` | The Makefile to use | |
| `make-step` | The make step to conditionally execute | |

### [make-step-prepost](./.github/actions/make-step-prepost/action.yml)

This action runs a Makefile step with optional pre and post steps.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `make-file` | Makefile to use | `Makefile` |
| `make-step` | The base make step to execute along with its optional pre and post steps | |

### [nodejs](./.github/actions/nodejs/action.yml)

This action sets up a Node.js environment and caches dependencies.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `base-dir` | Base directory of the Node.js project, if applicable. | `''` |
| `node-version` | The version of Node.js to be installed. | `20` |

### [setup-gradle-github-pkg](./.github/actions/setup-gradle-github-pkg/action.yml)

This action configures Gradle to use GitHub Packages as a repository.

### [setup-npm-github-pkg](./.github/actions/setup-npm-github-pkg/action.yml)

This action configures NPM to use GitHub Packages as a repository.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `npm-auth-token` | Npm Auth Token | `Makefile` |
| `npm-registry` | Npm registry | `npm.pkg.github.com` |

### [version](./.github/actions/version/action.yml)

This action manages version tags for different branches and snapshots.

**Inputs:**

| Name | Description | Default |
| --- | --- | --- |
| `use-snapshot` | Enable/disable using snapshot tags. | `true` |
| `branch-prefixes` | Snapshot tag to use for non-release branches. | `main release/ feat/ fix/ refact/ versioning/` |
| `with-snapshot-tag` | Snapshot tag to use for non-release branches. | `.${GITHUB_SHA:0:7}` |
| `with-main-pre-release-tag` | Pre-release tag for main branch. | `-SNAPSHOT` |
| `with-release-pre-release-tag` | Pre-release tag for release branches. | `-SNAPSHOT` |
| `with-feat-pre-release-tag` | Pre-release tag for feature branches. | `-dev-SNAPSHOT` |
| `with-fix-pre-release-tag` | Pre-release tag for fix branches. | `-dev-SNAPSHOT` |
| `with-refact-pre-release-tag` | Pre-release tag for refactoring branches. | `-dev-SNAPSHOT` |
| `with-versioning-pre-release-tag` | Pre-release tag for refactoring branches. | `-dev-SNAPSHOT` |
| `with-default-tag` | Default tag to use if the specific branch tag is empty. | `-dev-SNAPSHOT` |
