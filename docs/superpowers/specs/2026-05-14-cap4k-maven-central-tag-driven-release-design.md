# cap4k Maven Central Tag-Driven Release Design

Date: 2026-05-14

## Context

`cap4k` already has a separated Maven Central publishing branch, but the current release channel is still shaped like a mixed snapshot-and-release setup:

- the convention plugin fixes `version = "0.5.0-SNAPSHOT"`
- the release workflow allows `workflow_dispatch`
- the workflow publishes first and then calls the Central compatibility upload endpoint
- repository secrets are not yet configured

This does not match the intended release posture for `cap4k`. The user has now narrowed the requirements:

- Maven Central support is for formal releases only
- release versions must come from Git tags
- GitHub Actions must only publish on `v*` tags
- validation work must happen on an isolated verification branch and worktree, not directly on the publishing branch

## Goals

- Keep `publish/maven-central` as the canonical Central release channel for `cap4k`.
- Make the release version derive from a `v*` Git tag, for example `v0.5.0 -> 0.5.0`.
- Remove snapshot publishing behavior from the Central release channel.
- Remove manual GitHub Actions triggering for Central release.
- Make non-tag builds safe for local verification only.
- Keep the mainline branch free of Central publishing requirements and credentials.

## Non-Goals

- Migrating package names from `com.only4...`.
- Publishing `only-engine` to Maven Central.
- Publishing `cap4k-reference-content-studio` to Maven Central.
- Solving Gradle plugin marker publication under legacy non-verified coordinates in this change.
- Designing an automated long-lived release branch strategy such as `release/0.5.x`.

## Branch and Worktree Roles

The release topology is:

- `master` or normal development branches: no remote Central publishing capability
- `publish/maven-central`: canonical Central release channel branch
- `verify/maven-central`: isolated branch for validating and adjusting the Central release channel before merging back into `publish/maven-central`

Worktree roles are:

- `publish/maven-central` worktree: stable publishing branch checkout, not used for trial release edits
- `verify/maven-central` worktree: place for release-channel experiments, documentation, validation, and pre-merge fixes

This keeps the publishing branch readable and reduces accidental release drift from temporary verification changes.

## Chosen Release Model

The chosen model is tag-driven release only.

The source tree keeps a baseline development version for local builds, but Central release publication does not trust that baseline for the final artifact version. Instead:

1. GitHub Actions receives a pushed tag matching `v*`.
2. The workflow extracts the tag name.
3. The `v` prefix is removed.
4. The resulting value becomes the effective project version for the publish run.

Examples:

```text
v0.5.0   -> 0.5.0
v1.2.3   -> 1.2.3
```

Tags that do not resolve to a plain release version are invalid and must fail before publication starts.

## Versioning Rules

The Central channel only supports release versions.

Rules:

- effective release versions must not end with `-SNAPSHOT`
- remote publish tasks must only run when a valid `v*` tag is present
- local development builds may continue to use a baseline version value, but that value is not considered a releasable Central version
- local `publishToMavenLocal` remains available for structure validation

This design deliberately drops snapshot deployment to Sonatype from the Central branch. If snapshot distribution is needed later, it should be introduced as a separate, explicit channel rather than hidden behind the formal release workflow.

## Build Logic Design

The version resolution logic should move from a fixed constant to a small release-aware resolver in the Gradle convention layer.

The resolver should support two modes:

1. Local or non-tag mode
   - use the baseline development version for normal builds
   - allow `check` and `publishToMavenLocal`
   - block remote Central release publication

2. Tag release mode
   - read the tag-provided release version
   - validate that it is a plain release version
   - use that value for all `MavenPublication` coordinates
   - enable signing and remote publish tasks

The release version should be injected explicitly from CI, for example through a Gradle property or environment variable derived from `github.ref_name`, instead of teaching Gradle to inspect Git state directly. That keeps local builds simple and makes CI behavior auditable.

## GitHub Actions Design

The Maven Central release workflow should become a strict tag workflow:

- trigger: `push.tags = v*`
- no `workflow_dispatch`

Workflow stages:

1. checkout repository at the tagged commit
2. set up JDK and Gradle
3. derive `RELEASE_VERSION` from the tag
4. validate the derived version
5. run `./gradlew check`
6. run `./gradlew publish` with the derived release version and signing credentials
7. submit the staged release to Central through the supported Sonatype endpoint already used by this branch
8. create the GitHub Release from the tag

The workflow must fail early if:

- the ref is not a tag
- the tag does not match `v<release-version>`
- required credentials are missing
- signing material is missing
- publication tasks attempt to use a snapshot version

## Secrets

The `cap4k` repository requires these GitHub Actions secrets for the Central channel:

```text
CENTRAL_USERNAME
CENTRAL_PASSWORD
SIGNING_KEY
SIGNING_PASSWORD
```

These secrets are only relevant to the publishing branch workflow. They must not be required by `master` CI or by normal contributor builds.

## Publication Metadata

The existing Central publication metadata remains the right shape:

- `groupId = io.github.ldmoxeii`
- `artifactId = module name`
- `url = https://github.com/LDmoxeii/cap4k`
- MIT license metadata
- developer metadata for `LDmoxeii`
- SCM metadata for the GitHub repository
- sources jar and javadoc jar

This design does not change the metadata model unless the implementation uncovers a concrete Central validation failure.

## Gradle Plugin Marker Limitation

The branch currently disables publication tasks whose names end with:

```text
PluginMarkerMavenPublicationToCentralPortalRepository
```

That stays in place.

Reason:

- regular module publications use the verified namespace `io.github.ldmoxeii`
- Gradle plugin marker publications still map to legacy plugin IDs that do not line up with the verified Central namespace

This means the Central release channel is currently designed to publish the normal Maven artifacts and plugin implementation artifacts, not a full plugin-marker story for `plugins { id(...) }` consumption from Central.

## Verification Strategy

Verification is split into two levels.

### Local Structural Verification

Run on `verify/maven-central`:

```text
./gradlew check publishToMavenLocal
```

Expected outcomes:

- all modules build and test
- publications can be generated locally
- generated POM files contain required metadata
- local artifacts appear under `~/.m2/repository/io/github/ldmoxeii/...`

This verifies publication structure, not remote Central acceptance.

### Remote Release Verification

Run only after the validation branch is ready to push:

1. merge or cherry-pick validated changes into `publish/maven-central`
2. push `publish/maven-central`
3. create a test release tag such as `v0.5.0`
4. let GitHub Actions run the full release workflow
5. verify the workflow logs for:
   - derived release version
   - successful signing
   - successful publish
   - successful Central submission call
6. verify the artifact appears in Central Portal
7. verify the GitHub Release is created from the same tag

The first successful remote release is the real proof that the channel works. Local validation alone is not enough.

## Error Handling

Missing or malformed tag:

- fail before running `publish`
- do not fall back to the baseline development version

Missing Central credentials:

- fail the release workflow
- do not affect `master` CI or local builds

Missing signing credentials:

- fail the release workflow before upload

Unexpected snapshot version during release:

- fail the workflow explicitly
- do not attempt remote publication

Central submission failure after publish:

- treat the run as failed
- require operator review before retrying with another tag

Plugin marker publication pressure:

- do not silently re-enable marker publication in this work
- treat that as a separate namespace and distribution design problem

## Testing and Review Boundaries

This design only covers the release-channel mechanics for `cap4k`.

Implementation should stay focused on:

- version resolution
- workflow trigger tightening
- workflow release version injection
- remote publish gating
- verification documentation

It should not pull unrelated refactors into `buildSrc`, module metadata, or repository structure unless required to make the release channel coherent.

## Acceptance Criteria

The design is satisfied when all of the following are true:

- `publish/maven-central` no longer models Central publication as a snapshot-capable branch
- Central release workflow can only start from a `v*` tag
- the effective release version is derived from the tag, not from a fixed source constant
- non-tag builds can still run `check` and `publishToMavenLocal`
- remote Central publication cannot run accidentally in non-tag mode
- required secrets are documented and isolated to the release workflow
- plugin marker publication remains intentionally disabled and documented
- there is a documented end-to-end verification path for the first real Central release
