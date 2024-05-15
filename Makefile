VERSION = $(shell cat VERSION)

.PHONY: lint build test publish promote

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

publish:
	@make -f libs.mk publish
	@make -f docs.mk publish

promote:
	@make -f libs.mk promote
	@make -f docs.mk promote
