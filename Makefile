VERSION = $(shell cat VERSION)

.PHONY: clean lint build test stage promote tag untag tag-github-actions untag-github-actions

LIBS_MK = infra/make/libs.mk
GITHUB_MK = infra/make/github.mk

clean:
	@make -f $(LIBS_MK) clean

lint:
	@make -f $(LIBS_MK) lint

build:
	@make -f $(LIBS_MK) build

test:
	@make -f $(LIBS_MK) test

stage:
	@make -f $(LIBS_MK) stage

promote:
	@make -f $(LIBS_MK) promote

tag:
	@echo "$(VERSION)" > VERSION
	@VERSION=$(VERSION) make -f $(GITHUB_MK) tag-github-actions

untag:
	@echo "$(VERSION)" > VERSION
	@VERSION=$(VERSION) make -f $(GITHUB_MK) untag-github-actions
