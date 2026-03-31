.PHONY: tag-github-actions

tag-github-actions:
ifndef VERSION
	$(error Usage: make tag-github-actions VERSION=0.31.0)
endif
	@echo "Pinning action refs to @$(VERSION)"
	find .github/workflows -name '*.yml' -exec sed -i '' '/komune-io\/fixers-gradle/s|@[^"'"'"' ]*|@$(VERSION)|g' {} +
	find .github/actions -name 'action.yml' -exec sed -i '' '/komune-io\/fixers-gradle/s|@[^"'"'"' ]*|@$(VERSION)|g' {} +
