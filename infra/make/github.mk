VERSION = $(shell cat VERSION)

.PHONY: tag-github-actions untag-github-actions

tag-github-actions:
	@echo "Pinning action refs to @$(VERSION)"
	find .github/workflows -name '*.yml' -exec sed -i '' 's|@main|@$(VERSION)|g' {} +
	find .github/actions -name 'action.yml' -exec sed -i '' 's|@main|@$(VERSION)|g' {} +

untag-github-actions:
	@echo "Unpinning action refs from @$(VERSION) back to @main"
	find .github/workflows -name '*.yml' -exec sed -i '' 's|@$(VERSION)|@main|g' {} +
	find .github/actions -name 'action.yml' -exec sed -i '' 's|@$(VERSION)|@main|g' {} +