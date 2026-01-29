VERSION = $(shell cat VERSION)

.PHONY: lint build test stage promote

LIBS_MK = infra/make/libs.mk

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
