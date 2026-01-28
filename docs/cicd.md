# CI/CD Usage

This document provides an overview of how to use the CI/CD workflows.

## Table of Contents

- [Usage](#usage)
  - [dev.yml](#devyml)
  - [sec.yml](#secyml)
- [Makefiles](#makefiles)
  - [Makefile](#makefile)
  - [make_libs.mk](#make_libsmk)
  - [make_docs.mk](#make_docsmk)

## Usage

| Name | Description |
| --- | --- |
| [dev.yml](./.github/workflows/dev.yml) | This workflow is triggered on pull requests, pushes to `main` and `release/*` branches, and tags. It uses the `make-jvm-workflow.yml` to build, test, and publish the JVM-based libraries. It also uses the `publish-storybook-workflow.yml` to publish the documentation. |
| [sec.yml](./.github/workflows/sec.yml) | This workflow is triggered on pull requests and pushes to `main` and `release/*` branches. It uses the `sec-workflow.yml` to perform security analysis. |

---
## Usage Details

### [dev.yml](./.github/workflows/dev.yml)

This workflow is triggered on pull requests, pushes to `main` and `release/*` branches, and tags. It uses the `make-jvm-workflow.yml` to build, test, and publish the JVM-based libraries. It also uses the `publish-storybook-workflow.yml` to publish the documentation.

**Jobs:**

| Name | Step | Description |
| --- | --- | --- |
| dev | `build` | Runs the JVM development workflow. |
| docs | `build` | Publishes the Storybook documentation. |

### [sec.yml](./.github/workflows/sec.yml)

This workflow is triggered on pull requests and pushes to `main` and `release/*` branches. It uses the `sec-workflow.yml` to perform security analysis.

**Jobs:**

| Name | Step | Description |
| --- | --- | --- |
| sec | `scan` | Runs the security analysis workflow. |

---
## Makefiles

This project uses Makefiles to automate common tasks. The main `Makefile` delegates tasks to more specific `.mk` files.

### [Makefile](./Makefile)

The main entry point for the build system. It defines the following high-level targets that call targets in `infra/script/make_libs.mk` and `infra/script/make_docs.mk`:

*   `lint`: Lints the libraries and documentation.
*   `build`: Builds the libraries and documentation.
*   `test`: Runs tests for the libraries and documentation.
*   `stage`: Stage the libraries and documentation.
*   `promote`: Promotes the libraries and documentation to a production environment.

### [make_libs.mk](./infra/script/make_libs.mk)

This file contains the logic for building, testing, and publishing the Gradle libraries.

*   `lint`: Runs `detekt` to perform static analysis on the Kotlin code.
*   `build`: Builds the project and publishes it to the local Maven repository.
*   `test`: Runs the unit tests.
*   `stage`: Publishes the artifacts to GitHub Packages.
*   `promote`: Publishes the artifacts to Sonatype OSS.

### [make_docs.mk](./infra/script/make_docs.mk)

This file contains the logic for building and publishing the Storybook documentation.

*   `lint`: Lints the Storybook Dockerfile.
*   `build`: Builds the Storybook static site.
*   `test`: No tests are defined for the documentation.
*   `package`: Builds and pushes a Docker image containing the Storybook static site.
