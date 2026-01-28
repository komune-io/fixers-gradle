VERSION = $(shell cat VERSION)

.PHONY: lint build test stage promote version

lint:
	./gradlew check

build:
	VERSION=$(VERSION) ./gradlew clean build publishToMavenLocal -x test

test:
	./gradlew test
	cd sandbox && ./gradlew test

stage:
	VERSION=$(VERSION) ./gradlew stage

promote:
	VERSION=$(VERSION) ./gradlew promote

version:
	@echo "$(VERSION)"
