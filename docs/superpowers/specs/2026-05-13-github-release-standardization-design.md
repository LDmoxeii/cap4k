# GitHub Release Standardization Design

Date: 2026-05-13

## Context

The `cap4k`, `only-engine`, and `cap4k-reference-content-studio` repositories need a more consistent public GitHub release posture. Current gaps include missing `LICENSE` files, missing README status badges, no GitHub CI/CD workflows, no GitHub releases, no visible DeepWiki entry point, and no Maven Central release path for `cap4k`.

The implementation must avoid making normal clones of the main branch require publishing credentials. In particular, `cap4k` mainline should remain friendly to contributors who only want to build, test, or publish to `mavenLocal()`.

## Scope

All three repositories get GitHub-facing standardization:

- `LICENSE` with MIT terms.
- README badges for CI, GitHub release, license, and DeepWiki.
- GitHub Actions CI.
- GitHub Release creation workflow.

Only `cap4k` gets Maven Central publishing support.

`only-engine` and `cap4k-reference-content-studio` do not publish to Maven Central in this work.

## Repository Identity

The GitHub remotes remain under the current owner for now:

- `LDmoxeii/cap4k`
- `LDmoxeii/only-engine`
- `LDmoxeii/cap4k-reference-content-studio`

Repository migration to the `only-moxeii` organization is out of scope for this work.

The Maven Central namespace for `cap4k` is:

```text
io.github.ldmoxeii
```

The source package namespace remains unchanged:

```text
com.only4...
```

Changing source package names is out of scope.

## License

Each repository will use the standard MIT License with this copyright line:

```text
Copyright (c) 2026 LDmoxeii
```

The same license identity is used in README badges and Maven POM metadata where applicable.

## Branch Strategy

The main branch must not contain any remote publishing capability.

The main branch may keep local development publishing through `publishToMavenLocal`, but it must not require:

- Maven Central credentials.
- GPG signing credentials.
- Aliyun private repository credentials.
- Remote publish repository configuration.

Publishing is isolated by channel branches:

```text
publish/maven-central
publish/aliyun-private
```

`publish/maven-central` contains the Maven Central publishing setup for `cap4k`:

- `io.github.ldmoxeii` Maven group.
- POM metadata.
- signing configuration.
- Central Portal repository configuration.
- Maven Central release workflow.

This branch may be pushed to GitHub and used for public releases. Release tags such as `v0.5.0` are created from this branch.

`publish/aliyun-private` contains the Aliyun private repository publishing setup. It is a local/private publishing channel and is not pushed to the public GitHub repository by default.

Future version maintenance branches should use `release/*`, for example:

```text
release/0.5.x
release/0.6.x
```

This keeps `publish/*` for publishing channels and `release/*` for version maintenance.

## cap4k Maven Central Publishing

The `cap4k` publishing setup should be implemented in the publishing channel branch, not on main.

The publishing setup uses the existing Gradle convention plugin structure. The current shared Kotlin/JVM convention in `buildSrc` is the right place to configure common publication metadata for publishable modules.

Publication coordinates:

- `groupId`: `io.github.ldmoxeii`
- `artifactId`: existing module name, such as `ddd-core` or `cap4k-plugin-pipeline-api`
- `version`: managed centrally by the Gradle convention

The first release can use `0.5.0` after replacing the current `0.5.0-SNAPSHOT` version on the publishing branch.

Maven Central requirements:

- sources jar
- javadoc jar
- POM metadata:
  - name
  - description
  - project URL
  - MIT license
  - developer entry for `LDmoxeii`
  - SCM URL for `https://github.com/LDmoxeii/cap4k`
- signing for published artifacts
- Central Portal credentials from GitHub Actions secrets

The `cap4k-plugin-pipeline-gradle` module may already have publications created by `java-gradle-plugin`. The implementation must avoid creating duplicate publications for Gradle plugin modules.

Kotlin javadoc output may be minimal. A basic javadoc jar is acceptable for the first release; Dokka can be added later if richer API documentation becomes necessary.

## GitHub Actions

### CI

Each repository gets `.github/workflows/ci.yml`.

For `cap4k`:

```text
./gradlew check
```

For `only-engine`:

```text
./gradlew check
```

For `cap4k-reference-content-studio`, CI must first publish `cap4k` snapshots to `mavenLocal()` because the reference project depends on local `cap4k` snapshot artifacts:

```text
cap4k: ./gradlew publishToMavenLocal
cap4k-reference-content-studio: ./gradlew check
```

CI uses JDK 17 and Gradle wrapper setup with caching.

### GitHub Releases

Each repository gets a GitHub Release workflow that creates a GitHub release from tags matching:

```text
v*
```

The release workflow must run the relevant checks before creating the release.

Main branch release workflows do not publish packages to remote Maven repositories.

The Maven Central publishing workflow exists only on `cap4k`'s `publish/maven-central` branch.

## GitHub Secrets

Maven Central publishing requires these secrets on the `cap4k` repository when using the `publish/maven-central` branch:

```text
CENTRAL_USERNAME
CENTRAL_PASSWORD
SIGNING_KEY
SIGNING_PASSWORD
```

Main branch CI must not require these secrets.

## README Badges

README badge order is:

1. CI
2. Maven Central, only for `cap4k`
3. GitHub Release
4. License
5. DeepWiki

`cap4k` uses `ddd-core` as the Maven Central badge artifact because it is a stable foundational runtime module:

```md
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ldmoxeii/ddd-core)](https://central.sonatype.com/artifact/io.github.ldmoxeii/ddd-core)
```

DeepWiki badges link to:

- `https://deepwiki.com/LDmoxeii/cap4k`
- `https://deepwiki.com/LDmoxeii/only-engine`
- `https://deepwiki.com/LDmoxeii/cap4k-reference-content-studio`

DeepWiki automation is not implemented as a GitHub workflow. Current practice is to expose the badge and rely on DeepWiki's own indexing behavior. Release validation includes opening the DeepWiki page and confirming that an index exists or is updating.

## Release Flow

For normal development:

1. Work on main.
2. Run CI.
3. Do not require publishing secrets.

For `cap4k` Maven Central release:

1. Update or rebase `publish/maven-central` from main.
2. Set the release version, for example `0.5.0`.
3. Run checks locally.
4. Push the publishing branch.
5. Create a release tag such as `v0.5.0` from `publish/maven-central`.
6. Let GitHub Actions publish to Maven Central and create the GitHub Release.
7. Move the publishing branch to the next snapshot version, for example `0.5.1-SNAPSHOT`.

For private Aliyun release:

1. Update or rebase `publish/aliyun-private` from main.
2. Keep Aliyun publishing configuration only on that branch.
3. Publish manually or through a private workflow.

## Error Handling

Missing Central credentials fail only the Maven Central publishing workflow on the publishing branch. They must not affect main branch CI.

Missing signing credentials fail Maven Central publishing. The workflow must not publish unsigned remote artifacts.

If `cap4k-reference-content-studio` cannot resolve `cap4k` snapshots in CI, fix the CI checkout or `publishToMavenLocal` step instead of weakening project dependencies.

If DeepWiki does not update immediately, the release still proceeds. DeepWiki is a visibility check, not a release gate.

Existing uncommitted business changes in `cap4k` and `cap4k-reference-content-studio` must not be reverted or overwritten while implementing this design.

## Verification

Main branch verification commands:

```text
cap4k: ./gradlew check publishToMavenLocal
only-engine: ./gradlew check
cap4k-reference-content-studio: run cap4k publishToMavenLocal first, then ./gradlew check
```

Publishing branch verification includes generating Maven publications, signing them, and publishing to a local or staging target before using the tag-triggered workflow.
