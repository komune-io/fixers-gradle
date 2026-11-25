# The images of the fixers Spring Boot image convention (BootImageConvention.kt), pinned by tag and digest.
# Not built: build-composite/build.gradle.kts reads the FROM lines into FixersBootImages, and Dependabot
# (docker ecosystem) keeps them current. The run image must be the one the builder names: bump them together.
FROM paketobuildpacks/builder-noble-java-tiny:0.0.222@sha256:96678722f0d61f6102ceccd83f829a935a304fc353bbd5b20bbbfd90c28533b8 AS builder
FROM paketobuildpacks/ubuntu-noble-run-tiny:0.0.163@sha256:34e5d30227697b12a5d6d484aeee44662f288640e15bc36d1165f932bef56853 AS run-image
FROM paketobuildpacks/health-checker:2.14.5@sha256:2d0a695649aab94fef550069cd5e17105af65a3c4b3fed4a9c574e9276be38d2 AS health-checker
