VERSION = $(shell cat VERSION)

lint: lint-libs
build: build-libs
test: test-libs
publish: publish-libs
promote: promote-libs

lint-libs:
	./gradlew detekt

build-libs:
	./gradlew clean build publishToMavenLocal -x test

test-libs:
	./gradlew test
	cd sandbox
	./gradlew test

publish-libs:
	PKG_MAVEN_REPO=github ./gradlew publish --info

promote-libs:
	PKG_MAVEN_REPO=sonatype_oss ./gradlew publish

.PHONY: version
version:
	echo "$$VERSION"
