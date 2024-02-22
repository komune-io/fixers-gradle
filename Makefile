.PHONY: version

libs: package-libs
docs:
	echo 'No Docs'

build-libs:
	./gradlew build

test-libs:
	./gradlew test

publish-libs: build-libs
	./gradlew publishToMavenLocal publish

version:
	@VERSION=$$(cat VERSION); \
	echo "$$VERSION"
