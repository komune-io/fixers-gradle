.PHONY: version

lint: lint-libs
build: build-libs
test: test-libs
package: package-libs

docs:
	echo 'No Docs'

lint-libs:
	./gradlew detekt

build-libs:
	./gradlew build

test-libs:
	./gradlew test
	cd sandbox
	./gradlew test

package-libs: build-libs
	./gradlew publishToMavenLocal publish

version:
	@VERSION=$$(cat VERSION); \
	echo "$$VERSION"
