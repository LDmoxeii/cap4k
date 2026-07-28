# cap4k Single-Mainline Release Governance Design

Date: 2026-07-28

Status: current

## Summary

cap4k has one long-lived source branch, `master`. Source integration, artifact publication, and local development substitution are separate concerns:

- short-lived working branches enter `master` through pull requests;
- exact stable version tags on accepted `master` commits publish to Maven Central and create GitHub Releases;
- local cap4k/consumer co-development uses an explicitly enabled Gradle Composite Build;
- public consumers and the official GitHub Template use stable Maven Central artifacts by default.

Aliyun, Central Snapshot, other remote Snapshot channels, and long-lived publish branches are not part of the current model.

## Context

The previous release design used `publish/aliyun-private` and `publish/maven-central` as artifact-channel branches. Aliyun existed primarily to let a local consumer use cap4k changes before a public release. That made source history carry a local-development concern and allowed release-branch-specific coordinates, fixtures, and build logic to drift from `master`.

The conflict observed while promoting accepted `master` content to the Aliyun branch demonstrated the structural problem: branches model source histories, while repositories and versions model artifact channels. Local source substitution does not require a remote artifact channel at all.

## Goals

- Keep `master` as the only long-lived source branch.
- Preserve the required `check` context and current docs-only/full-check behavior for `master` pull requests.
- Publish future stable versions from exact tags whose commits are contained in `origin/master`.
- Keep Central credentials and signing material out of ordinary builds.
- Remove Aliyun and all current remote Snapshot publication contracts.
- Prove local pipeline plugin and runtime-module consumption through Gradle Composite Build without relying on an already published version.
- Keep the independent official Template runtime-decoupled and stable-version-only.
- Preserve existing `v2.0.1` history without moving or rebuilding the tag.

## Non-Goals

- Add Central Portal Snapshot publication.
- Preserve `com.only4:*` or branch-specific publication compatibility.
- Put sibling checkout paths, filesystem repositories, Snapshot repositories, or `mavenLocal()` into the official Template.
- Restore bootstrap, codegen, fixtures, or other content that survived only on an old publish branch.
- Change runtime, generator, starter, or default-project business behavior.
- Delete remote branches, rules, or secrets inside the implementation pull request; remote cleanup follows the merged and smoke-tested repository change.

## Source And Pull Request Governance

- `master` is the only supported long-lived branch and pull request base.
- Normal implementation, documentation, workflow, and governance changes start from `origin/master` on a short-lived branch such as `feature/*`, `fix/*`, or `docs/*`.
- Direct commits to `master` are not allowed.
- `scripts/create-pr.ps1` rejects unsupported bases, qualified cross-repository heads, and protected or retiring heads.
- No publish-promotion pull request type exists.
- Historical `publish/*` refs, while awaiting deletion, are retirement targets only. Do not merge into them, tag from them, or add new branch-specific release behavior.

## CI And Branch Protection

- The required job context remains `check`.
- Pull requests must target `master`; the classifier fails other bases.
- A `master` pull request that changes only documented docs-only paths skips Gradle but still completes `check`.
- Code, build, script, workflow, test, fixture, or template-resource changes run `buildSrc` tests and `./gradlew check`.
- Pushes to `master` and manual CI dispatches run the full checks.
- The PR workflow guard and PR body validation run before Gradle checks.
- Publish-promotion classification and publish-branch-only skip behavior are removed.

## Maven Central Stable Release

- The workflow may use the broad GitHub trigger `v*`, but it accepts only exact `v<major>.<minor>.<patch>` tags.
- The workflow derives plain `major.minor.patch` and passes it to Gradle as `release.version`.
- The tagged commit must be contained in `origin/master`; failure occurs before remote publication.
- Gradle continues to reject Snapshot and `v`-prefixed release-version inputs.
- Publication retains signed artifacts, the Central Portal upload, automatic publication, and a GitHub Release for the same tag.
- Central and signing credentials are read only by the release workflow. A clone, normal CI run, Composite Build, or consumer build does not require them.
- A normal branch push or unversioned `./gradlew publish` must not activate remote Central publication tasks.

## No Private Or Snapshot Channel

- Remove the Aliyun workflow, repository, credentials, version override, task policy, and dedicated tests.
- Do not retain `com.only4` compatibility. Public, release, and local substitution coordinates use `io.github.ldmoxeii`.
- Do not introduce Central Snapshot or another remote Snapshot repository in this change.
- If a real future requirement appears for another machine or CI system to consume an unreleased build, make that a separate design decision. Do not recreate a long-lived publish branch by default.

## Local Composite Development

The maintained opt-in settings and command examples are recorded in [Local Composite Development](../analysis/local-composite-development.md).

- A consumer explicitly opts into a local cap4k checkout; the mere presence of a sibling directory must not change normal resolution.
- The accepted form must resolve the `io.github.ldmoxeii.cap4k.pipeline` plugin through the included build's `java-gradle-plugin` declaration.
- It must also substitute the official default runtime modules from local projects:
  - `io.github.ldmoxeii:ddd-core`;
  - `io.github.ldmoxeii:ddd-domain-repo-jpa`;
  - `io.github.ldmoxeii:cap4k-ddd-jpa-starter`.
- The verification consumer declares an unavailable version such as `999.0.0-local`, does not use TestKit `withPluginClasspath()`, and proves `cap4kPlan`, compilation, tests, and dependency substitution.
- Runtime substitution relies on `group = io.github.ldmoxeii` and `artifactId = project.name`. Publication-coordinate changes require renewed evidence or minimal explicit substitution.
- A workspace-local Maven repository is only a future fallback if real Composite Build evidence proves publication metadata cannot be substituted faithfully. Global `mavenLocal()` is not the official path.

## Public Consumer And Template Contract

- Public consumers and the independent GitHub Template use `mavenCentral()` and an explicit stable cap4k version.
- Default configuration contains no local path, private repository, Snapshot repository, or local Maven cache dependency.
- A clean clone builds without publication credentials or a cap4k source checkout once the declared stable artifacts are available in Maven Central.

## Migration And Historical Releases

- Existing `v2.0.1`, its release, and its historical promotion merge remain immutable.
- Repository code, CI, governance, current analysis, and Composite evidence land through a normal pull request to `master`.
- Before deleting old remote branches, record both tips and confirm that all required stable-release behavior exists on `master`; do not migrate Aliyun coordinates, private repository logic, or obsolete fixtures.
- After the new flow is merged and smoke-tested, remove protection/rules for the two publish branches, delete their remote refs, prune local refs/worktrees, and delete Aliyun repository secrets.
- Historical specs and plans retain their original facts but carry a clear superseded pointer to this document.

## Acceptance Examples

- `v2.0.2` points to a commit contained in `origin/master`: the workflow derives `2.0.2`, publishes signed artifacts to Central, uploads for automatic publication, and creates the matching GitHub Release.
- `v2.0.2` points outside `origin/master`: the workflow fails its containment gate before publication.
- A pull request targets `master` with docs-only files: required `check` succeeds without Gradle.
- A pull request targets any other base: required `check` rejects it, and `scripts/create-pr.ps1` refuses to create it.
- A normal consumer does not enable a local included build: it resolves the declared stable version from Maven Central.
- The Composite verification consumer requests `999.0.0-local`: plugin marker and the three runtime modules resolve from the local included cap4k build, and its plan/build/test evidence succeeds without `withPluginClasspath()`.
- Current workflow, Gradle, scripts, templates, issue forms, AGENTS, and release map contain no active Aliyun, Snapshot, or publish-promotion lane.
