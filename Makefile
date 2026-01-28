VERSION = $(shell cat VERSION)

.PHONY: lint build test stage promote

LIBS_MK = infra/script/make_libs.mk
DOCS_MK = infra/script/make_docs.mk

lint:
	@make -f $(LIBS_MK) lint
	@make -f $(DOCS_MK) lint

build:
	@make -f $(LIBS_MK) build
	@make -f $(DOCS_MK) build

test-pre:
	@make -f $(LIBS_MK) test-pre

test:
	@make -f $(LIBS_MK) test
	@make -f $(DOCS_MK) test

stage:
	@make -f $(LIBS_MK) stage
	@make -f $(DOCS_MK) stage

promote:
	@make -f $(LIBS_MK) promote
	@make -f $(DOCS_MK) promote
