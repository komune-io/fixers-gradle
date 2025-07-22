VERSION = $(shell cat VERSION)

.PHONY: lint build test stage promote

## New
lint:
	@make -f libs.mk lint
	@make -f docs.mk lint

build:
	@make -f libs.mk build
	@make -f docs.mk build

test-pre:
	@make -f libs.mk test-pre

test:
	@make -f libs.mk test
	@make -f docs.mk test

stage:
	@make -f libs.mk stage
	@make -f docs.mk stage

promote:
	@make -f libs.mk promote
	@make -f docs.mk promote
