# cap4k Single-Mainline Release Governance Implementation Plan

**Goal:** Replace long-lived artifact-channel branches with one `master` source line, tag-driven Maven Central releases, and explicit Gradle Composite Build local development.

**Architecture:** Source integration enters protected `master` through short-lived pull requests. Stable release tags are contained by `origin/master`. Local consumers substitute cap4k source through an explicitly selected included build, while public consumers remain stable-version-only.

**Current design:** [2026-07-28-cap4k-single-mainline-release-governance-design.md](../specs/2026-07-28-cap4k-single-mainline-release-governance-design.md)

## Global Constraints

- Work from a short-lived branch based on `origin/master`; do not implement directly on a protected branch.
- Keep the required GitHub status context named `check`.
- Do not move, rebuild, or overwrite `v2.0.1`.
- Do not add Aliyun, Central Snapshot, another remote Snapshot repository, or `com.only4` compatibility.
- Do not put local-development settings into the independent official Template.
- Do not restore old publish-branch bootstrap, codegen, fixture, or build logic.
- Remote branch/rules/secrets deletion happens after merge and smoke verification, not in the implementation pull request.

## Task 1: Move Stable Release Containment To Master

**Files:**

- Modify: `.github/workflows/maven-central-release.yml`
- Modify: `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`
- Test: `buildSrc/src/test/kotlin/buildsrc/convention/*`

1. Change the tag containment fetch and ancestor check from the old Central publish branch to `origin/master`.
2. Preserve exact tag validation, plain `release.version` derivation, signing, Central publication, automatic upload, and GitHub Release creation.
3. Keep remote Central tasks disabled for non-release builds and retain tests for plain versions, Snapshot rejection, marker allowlisting, and signing/task gates.

## Task 2: Remove Aliyun And Snapshot Publication

**Files:**

- Delete: `.github/workflows/aliyun-snapshot.yml`
- Modify: `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`
- Delete or update: Aliyun-specific policy and tests under `buildSrc`

1. Remove Aliyun triggers, repositories, credentials, version inputs, group switching, and remote task gating.
2. Keep `io.github.ldmoxeii` as the only active publication/substitution group.
3. Prove a normal build and unversioned publish invocation cannot activate a remote channel.

## Task 3: Add Composite Build Evidence

**Files:**

- Add or modify: focused integration fixture and its verification entrypoint
- Add: `docs/superpowers/analysis/local-composite-development.md`
- Modify: `docs/superpowers/analysis/README.md`

1. Create a minimal consumer that declares `999.0.0-local` for the pipeline plugin and the three official default runtime modules.
2. Resolve the pipeline plugin through the included build rather than TestKit `withPluginClasspath()`.
3. Prove `ddd-core`, `ddd-domain-repo-jpa`, and `cap4k-ddd-jpa-starter` resolve from local projects.
4. Run `cap4kPlan`, Kotlin compile/test, and dependency-insight checks.
5. Document only the explicit opt-in local workflow; do not change public Template defaults.

## Task 4: Collapse PR And CI Governance To Master

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `.github/PULL_REQUEST_TEMPLATE.md`
- Modify: `.github/ISSUE_TEMPLATE/release.yml`
- Modify: `scripts/create-pr.ps1`
- Modify: `scripts/test-pr-workflow.ps1`
- Modify: `AGENTS.md`

1. Reject pull request bases other than `master` and remove publish-promotion classification.
2. Preserve docs-only/full-check behavior and always complete the required `check` job.
3. Keep PR workflow/body guard tests running before Gradle checks.
4. Make the PR template list only `master` and remove the release-promotion change type.
5. Make the release issue form track version/tag, source `master` commit, and publication checks instead of a publish lane.
6. Update contributor rules to the single-mainline, tag-driven, no-Snapshot contract.

## Task 5: Replace Current Release Documentation

**Files:**

- Add: `docs/superpowers/specs/2026-07-28-cap4k-single-mainline-release-governance-design.md`
- Add: `docs/superpowers/plans/2026-07-28-cap4k-single-mainline-release-governance.md`
- Modify: `docs/superpowers/analysis/release-map.md`
- Modify: `docs/superpowers/specs/2026-05-22-cap4k-publish-channel-governance-design.md`
- Modify: `docs/superpowers/specs/2026-07-21-cap4k-github-workflow-governance-design.md`
- Modify: `docs/superpowers/plans/2026-07-21-cap4k-github-workflow-governance.md`

1. Record the complete current contract and implementation plan.
2. Rewrite the current release map around `origin/master` containment and the absence of private/Snapshot channels.
3. Add concise superseded pointers to historical documents without rewriting their historical contents.

## Task 6: Verify Repository Behavior

1. Run `buildSrc` tests and the focused Composite fixture checks.
2. Run `scripts/test-pr-workflow.ps1`.
3. Parse changed workflow and issue-form YAML.
4. Run `./gradlew check`, or record the exact external/environment blocker if it cannot complete.
5. Run `git diff --check`.
6. Search current governance and implementation surfaces for active Aliyun, Snapshot, old coordinates, publish-promotion, and old containment contracts. Historical documents may retain old facts only behind their superseded notice.

## Task 7: Land And Perform Post-Merge Cleanup

1. Open a normal pull request from the short-lived branch to `master` using `scripts/create-pr.ps1`.
2. After required checks pass and the change is merged, smoke-test the new tag-containment and local Composite paths without changing `v2.0.1`.
3. Record the tips of `publish/aliyun-private` and `publish/maven-central` and confirm no required stable-release behavior remains only there.
4. Remove branch protection/rules for those refs, delete the remote branches, and prune local refs/worktrees.
5. Delete Aliyun repository credentials from GitHub after confirming no workflow consumes them.
