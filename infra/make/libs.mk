VERSION = $(shell cat VERSION)

.PHONY: clean lint build test stage promote version

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
