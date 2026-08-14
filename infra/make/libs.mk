VERSION = $(shell cat VERSION)

.PHONY: clean lint build test stage promote version verify-metadata verify-metadata-dry

clean:
	./gradlew clean

lint:
	./gradlew detekt

build:
	VERSION=$(VERSION) ./gradlew clean build publishToMavenLocal -x test

test:
	./gradlew test :build-composite:test
	cd sandbox && ./gradlew test

stage:
	VERSION=$(VERSION) ./gradlew stage

promote:
	VERSION=$(VERSION) ./gradlew promote

version:
	@echo "$(VERSION)"

# Regenerates gradle/verification-metadata.xml and the exported keyring after a dependency bump.
# --dry-run runs no task (no test, no publish) but still resolves the whole task graph, so every
# configuration CI touches is covered. Gradle writes the result next to the real files with a
# .dryrun suffix; they are moved into place here. The sandbox build shares gradle/ through a
# symlink, so a second pass from sandbox/ folds its extra resolutions (Kotlin/MPP JS artifacts)
# into the same files. Review the diff before committing.
verify-metadata: verify-metadata-dry
	mv gradle/verification-metadata.dryrun.xml gradle/verification-metadata.xml
	mv gradle/verification-keyring.dryrun.keys gradle/verification-keyring.keys
	mv gradle/verification-keyring.dryrun.gpg gradle/verification-keyring.gpg
	cd sandbox && ./gradlew --write-verification-metadata pgp,sha256 --export-keys --dry-run build
	mv gradle/verification-metadata.dryrun.xml gradle/verification-metadata.xml
	mv gradle/verification-keyring.dryrun.keys gradle/verification-keyring.keys
	mv gradle/verification-keyring.dryrun.gpg gradle/verification-keyring.gpg

# Generates the root-build files with the .dryrun suffix, to inspect the delta without replacing
# anything. Note: the full regen (verify-metadata) also needs the sandbox pass above.
verify-metadata-dry:
	./gradlew --write-verification-metadata pgp,sha256 --export-keys --dry-run build publishToMavenLocal detekt :build-composite:test
