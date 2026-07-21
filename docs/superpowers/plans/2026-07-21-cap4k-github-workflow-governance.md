# cap4k GitHub Workflow Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make cap4k GitHub checks branch-aware and change-aware, and add issue/PR templates for predictable collaboration.

**Architecture:** Keep the required `check` job stable. Move docs-only and publish-promotion decisions inside the job so branch protection always receives a terminal check result. Keep publish workflows focused on publishing and rely on `master` checks plus publish promotion gates for validation.

**Tech Stack:** GitHub Actions YAML, Bash, Gradle wrapper, GitHub issue forms, GitHub branch protection via `gh api`.

## Global Constraints

- Do not implement this directly on `master`.
- Do not use workflow-level `paths-ignore` for the required `check` job.
- Treat `docs/superpowers/specs/**` and `docs/superpowers/plans/**` as docs-only.
- Publish branch PRs must come from the same repository's `master` branch.
- Publish workflows must not run Gradle `check`.
- Keep the required status check context named `check`.

---

### Task 1: Make CI Branch-Aware And Change-Aware

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: GitHub event context (`github.event_name`, `github.base_ref`, `github.head_ref`, `github.event.pull_request.head.repo.full_name`, `github.repository`)
- Produces: required job `check`, step output `docs_only`, full Gradle checks only when required

- [ ] **Step 1: Replace checkout/setup with reusable actions**

Use `actions/checkout@v4`, `actions/setup-java@v4`, and `gradle/actions/setup-gradle@v4` so changed-file detection can compare against the PR base.

- [ ] **Step 2: Add publish PR gate**

For PRs targeting `publish/aliyun-private` or `publish/maven-central`, fail unless `github.head_ref == 'master'` and the head repository equals `github.repository`.

- [ ] **Step 3: Add docs-only classifier**

Use `git diff --name-only "origin/${base_ref}...HEAD"` on PRs targeting `master`. Mark the change as docs-only when every changed path is in the allowed docs/template set.

- [ ] **Step 4: Gate Gradle steps**

Run `buildSrc` tests and `./gradlew check` only when the PR is not docs-only and is not a publish-promotion PR. Always run full checks for pushes to `master` and `workflow_dispatch`.

### Task 2: Remove Duplicate Checks From Publish Workflows

**Files:**
- Create: `.github/workflows/aliyun-snapshot.yml`
- Modify: `.github/workflows/maven-central-release.yml`

**Interfaces:**
- Consumes: existing publish secrets and tag/branch context
- Produces: publish-only workflows that do not run Gradle `check`

- [ ] **Step 1: Add Aliyun snapshot workflow to `master`**

Create the workflow already present on `publish/aliyun-private`, but without `./gradlew check`.

- [ ] **Step 2: Remove release duplicate checks**

Delete `Test buildSrc` and `Check` steps from the Maven Central release workflow.

### Task 3: Add Collaboration Templates

**Files:**
- Create: `.github/PULL_REQUEST_TEMPLATE.md`
- Create: `.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `.github/ISSUE_TEMPLATE/feature_request.yml`
- Create: `.github/ISSUE_TEMPLATE/docs.yml`
- Create: `.github/ISSUE_TEMPLATE/release.yml`
- Create: `.github/ISSUE_TEMPLATE/config.yml`
- Create: `.github/ISSUE_TEMPLATE/workflow_config.yml`

**Interfaces:**
- Consumes: GitHub issue/PR template conventions
- Produces: structured issue forms and a PR checklist that records target branch, change type, verification, and docs-only skip reason

- [ ] **Step 1: Add PR template**

The template must include Summary, Target Branch, Change Type, Verification, Docs-only skip reason, and Related issue/spec/plan sections.

- [ ] **Step 2: Add issue forms**

Add bug, feature, docs, release/publish, and config/workflow forms. Keep them concise and required fields minimal. Use `config.yml` only for issue-template configuration.

### Task 4: Verify, Commit, And Open PR

**Files:**
- Test: `.github/workflows/*.yml`
- Test: `.github/ISSUE_TEMPLATE/*.yml`

**Interfaces:**
- Consumes: final repository file set
- Produces: pushed branch and PR to `master`

- [ ] **Step 1: Validate YAML parsing**

Run a local parser over `.github/**/*.yml` and fail on syntax errors.

- [ ] **Step 2: Exercise classifier samples**

Run the Bash classifier logic against a docs-only sample and a code-change sample.

- [ ] **Step 3: Check diff and status**

Run `git diff --check` and inspect `git status --short`.

- [ ] **Step 4: Commit and create PR**

Commit the workflow/template change and open a PR to `master`.

### Task 5: Post-Merge Publish Branch And Protection Updates

**Files:**
- GitHub branch protection settings
- Long-lived branches `publish/aliyun-private` and `publish/maven-central`

**Interfaces:**
- Consumes: merged `master` workflow
- Produces: publish branches carrying the new workflow and protected by required `check`

- [ ] **Step 1: Merge workflow PR to `master` after checks pass**

Do not update branch protection before the new workflow is on `master`.

- [ ] **Step 2: Open `master` promotion PRs to both publish branches**

Use PRs from `master` to `publish/aliyun-private` and `publish/maven-central`.

- [ ] **Step 3: Configure branch protection**

Use `gh api` to require `check`, require PRs, enforce admins, and keep strict checks on `master`, `publish/aliyun-private`, and `publish/maven-central`.
