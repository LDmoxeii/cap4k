# cap4k GitHub Workflow Governance Design

## Context

cap4k has three long-lived branches:

- `master`
- `publish/aliyun-private`
- `publish/maven-central`

The current `CI` workflow runs Gradle checks for every pull request, including
documentation-only changes. This makes small docs, issue-template, and PR-template
updates unnecessarily expensive. The publish branches also repeat checks even
though publish promotion should only happen from content already accepted into
`master`.

## Goals

1. Run Gradle checks only when a change can affect code, build, scripts,
   workflow behavior, tests, fixtures, or generated templates.
2. Treat `docs/superpowers/specs/**` and `docs/superpowers/plans/**` as
   documentation-only changes.
3. Require publish branch promotion pull requests to come from the same
   repository's `master` branch.
4. Avoid repeating Gradle checks in publish workflows; publish branches consume
   already-checked `master` content.
5. Add issue and pull request templates so future contributors can state target
   branch, change type, verification, and docs-only skip reasons.

## Non-Goals

- Do not change Gradle tasks or project build logic.
- Do not add release automation beyond the existing Aliyun snapshot and Maven
  Central publish lanes.
- Do not rely on workflow-level `paths-ignore` for required checks. A skipped
  workflow can leave a required check pending, so the required `check` job must
  always be created.

## CI Contract

The `CI` workflow keeps the required job name `check`.

For pull requests targeting `master`:

- Docs-only changes skip Gradle and make the `check` job succeed.
- Non-docs changes run `buildSrc` tests and `./gradlew check`.

For pull requests targeting `publish/aliyun-private` or
`publish/maven-central`:

- The `check` job validates that the PR head branch is `master`.
- The `check` job validates that the head repository is the same repository as
  the base repository.
- The `check` job does not run Gradle.

For pushes to `master` and manual dispatches:

- The workflow runs the full Gradle check.

## Docs-Only Change Set

Docs-only paths include:

- `docs/**`
- `README*`
- `*.md`
- `.github/ISSUE_TEMPLATE/**`
- `.github/PULL_REQUEST_TEMPLATE.md`
- `.github/pull_request_template.md`
- `CHANGELOG.md`
- `CONTRIBUTING.md`
- `CODE_OF_CONDUCT.md`

The following paths are never docs-only:

- `.github/workflows/**`
- `scripts/**`
- `buildSrc/**`
- `gradle/**`
- `gradlew`
- `gradlew.bat`
- `*.gradle.kts`
- `gradle.properties`
- `cap4k-plugin-*`
- `ddd-*`
- source files, test files, fixtures, and template resources

## Publish Workflow Contract

`publish/aliyun-private` is a private snapshot publish lane:

- Triggered by push to `publish/aliyun-private`.
- Performs credential preflight.
- Runs `./gradlew publish`.
- Does not run `./gradlew check`.

`publish/maven-central` is a Central release lane:

- Triggered by `v*` tags.
- Validates tag format.
- Validates the tag commit is contained in `origin/publish/maven-central`.
- Runs `./gradlew publish -Prelease.version=...`.
- Does not run `./gradlew check`.

## Branch Protection Contract

After the workflow change is merged and propagated to publish branches:

- `master` requires status check `check`.
- `publish/aliyun-private` requires status check `check`.
- `publish/maven-central` requires status check `check`.
- All three protected branches enforce admins and require pull requests.

The source-branch rule for publish promotion is enforced by the required
`check` job because GitHub branch protection does not directly express "only
PRs from `master`".

## Verification

- Parse workflow YAML files.
- Exercise the changed-file classifier with docs-only and code-change samples.
- Run a lightweight local check command for workflow/template syntax.
- Use `gh` to inspect and later update branch protection after publish branches
  have received the new workflow.
