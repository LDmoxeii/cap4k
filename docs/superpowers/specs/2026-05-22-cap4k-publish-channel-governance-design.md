# cap4k Publish Channel Governance Design

Date: 2026-05-22

## Purpose

This document records the current effective release-channel governance for `cap4k`.

It is the forward-looking policy for day-to-day branch promotion and Maven Central publication. Older release standardization specs and plans are historical design records and are not retroactively maintained when this policy changes.

## Branch Roles

- `feature/*`: normal framework, documentation, skill, and verification work.
- `master`: the only mainline integration branch for framework development.
- `publish/maven-central`: the Maven Central release channel branch.
- `publish/aliyun-private`: optional private release channel branch.
- `verify/*`: temporary release-pipeline or publication-flow verification branches.

`publish/maven-central` is not a feature branch and is not a mirror that accepts arbitrary working branches. It may contain release-channel-only publishing configuration, but ordinary framework code must reach it only through `master`.

## Allowed Promotion Paths

The normal promotion path is:

1. `feature/* -> master`
2. `master -> publish/maven-central`
3. `publish/maven-central` commit -> `v*` tag -> Maven Central release

Allowed pull request paths:

- `feature/* -> master` for normal work.
- `master -> publish/maven-central` for release promotion.
- `verify/* -> publish/*` only for release-pipeline or publication-flow changes.

`master -> publish/maven-central` must use a pull request even when the change is only a clean promotion of already verified mainline code. This keeps branch protection, required checks, and review history on the release-channel transition.

## Forbidden Paths

Do not use these paths for normal work:

- direct commits on `master`;
- direct commits on `publish/maven-central`;
- `feature/* -> publish/maven-central` pull requests;
- issue branch, docs branch, ad-hoc branch, or other ordinary iteration branch -> `publish/maven-central` pull requests;
- `release/vX.Y.Z` or similar intermediate release-promotion branches as the default path from `master` to Maven Central;
- a `feature -> develop -> master -> publish` flow.

If a branch is not specifically a `verify/*` branch for release-pipeline work, treat it like `feature/*`: it must land on `master` first.

## Maven Central Release Policy

Maven Central publication is tag-driven, not branch-push-driven.

The `Maven Central Release` workflow listens for pushed tags matching `v*`, then applies stricter runtime gates:

- the tag must have exact `v<major>.<minor>.<patch>` format;
- the tagged commit must be contained in `origin/publish/maven-central`;
- `buildSrc` tests and `./gradlew check` must pass;
- Central credentials and signing material are required only for the publish workflow.

Pushing `publish/maven-central` by itself must not publish artifacts. The publish branch only makes a commit eligible for a later release tag.

## Release-Pipeline Changes

Changes to Maven Central publishing behavior, release workflow guards, Central Portal upload behavior, signing, or release-only Gradle conventions belong on a `verify/*` branch first.

After verification, promote that `verify/*` branch into the relevant `publish/*` branch by pull request.

Do not route release-pipeline-only implementation into `master` unless the change is intentionally part of the normal development surface and does not introduce mandatory Central or private-repository credentials to mainline builds.

## Protection Posture

The desired repository posture is:

- `master` requires status checks and receives normal work only through PRs.
- `publish/maven-central` requires status checks and receives release promotions through PRs.
- release tags are protected by repository rules.
- release workflow guards enforce that Central release tags point at commits contained in `origin/publish/maven-central`.

This posture is intentionally stricter than a local merge-and-push workflow because it keeps release promotion auditable and prevents ordinary work from bypassing `master`.

## Non-Goals

- Do not maintain old release standardization specs and plans as live process documents.
- Do not create a long-lived `develop` branch.
- Do not make `publish/maven-central` the default base for framework work.
- Do not require Central credentials for normal `master` development.
- Do not publish to Maven Central from branch pushes.

