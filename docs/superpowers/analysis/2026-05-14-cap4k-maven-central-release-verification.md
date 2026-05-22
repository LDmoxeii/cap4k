# cap4k Maven Central Release Verification

Date: 2026-05-14
Updated: 2026-05-22
Current policy reference: `docs/superpowers/specs/2026-05-22-cap4k-publish-channel-governance-design.md`

## Current Release Channel Rules

The effective release-channel policy is:

- `feature/*` and ordinary issue/docs/ad-hoc branches must land on `master` first.
- `master -> publish/maven-central` must use a pull request.
- `verify/* -> publish/*` is allowed only for release-pipeline or publication-flow changes, and must use a pull request.
- Ordinary work must not open pull requests directly into `publish/maven-central`.
- `release/vX.Y.Z` and similar intermediate release-promotion branches are not the normal Maven Central path.
- Maven Central release is tag-driven from `publish/maven-central`, not branch-push-driven.

The release path is:

1. `feature/* -> master`
2. `master -> publish/maven-central`
3. `publish/maven-central` commit -> `v*` tag -> Maven Central release

`publish/maven-central` may contain release-channel-specific Gradle and workflow configuration. It is not a general feature branch, and it is not a place to bypass `master` for ordinary code.

## Why `master -> publish/maven-central` Uses PRs

The current branch protection and CI shape make pull requests the stable promotion mechanism.

- `publish/maven-central` requires the `check` status.
- The normal CI workflow runs on pull requests and pushes to `master`.
- A new local merge commit pushed directly to `publish/maven-central` may not have the required status before branch protection evaluates it.
- A PR gives the promotion commit review history and required-check evidence.

This is an operational rule, not just a preference. Direct publish-branch commits weaken the release audit trail and can create confusion about whether mainline code came through `master`.

## Required GitHub Repository Secrets

Configure these repository secrets on `LDmoxeii/cap4k` before a Maven Central release:

- `CENTRAL_USERNAME`
- `CENTRAL_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

Value format notes:

- `CENTRAL_USERNAME`: the actual Sonatype Central Portal username used for the compatibility upload and publish credentials, not a display name.
- `CENTRAL_PASSWORD`: the matching Sonatype Central Portal password or token value consumed as the Gradle/Central password, not a file path.
- `SIGNING_KEY`: the full armored ASCII private PGP key block consumed by `useInMemoryPgpKeys(...)`, including the `-----BEGIN PGP PRIVATE KEY BLOCK-----` and `-----END PGP PRIVATE KEY BLOCK-----` lines. Do not store a key id, fingerprint, or file path here.
- `SIGNING_PASSWORD`: the passphrase for the private key stored in `SIGNING_KEY`. Leave it empty only if the private key itself has no passphrase.

Operational prerequisite:

- Publish the matching public key to a Sonatype-supported public key server before the first Central release. The Sonatype signing requirements list `keyserver.ubuntu.com`, `keys.openpgp.org`, and `pgp.mit.edu` as supported key servers. Do not assume that having the private key in GitHub secrets is enough for Central signature validation.

## Local Structural Verification Commands

Run these checks before promoting to `publish/maven-central` or tagging a release.

### 1. Run the `buildSrc` release-version resolver tests

```powershell
cd buildSrc
..\gradlew.bat test --tests "buildsrc.convention.CentralReleaseVersionTest"
```

Expected outcome:

- Gradle exits successfully.
- `CentralReleaseVersionTest` passes.
- The release-version resolver still accepts baseline local builds and rejects malformed release inputs.

### 2. Run the branch-wide local build and local publication verification

```powershell
cd ..
.\gradlew.bat check publishToMavenLocal
```

Expected outcome:

- Gradle exits successfully.
- All configured checks pass.
- Local Maven publications are generated without requiring Central credentials or signing material.
- No remote Central publication is attempted.

### 3. Run an explicit release-version local publication check

```powershell
.\gradlew.bat :ddd-core:publishMavenPublicationToMavenLocal "-Prelease.version=0.7.0"
```

Expected outcome:

- Gradle exits successfully.
- `ddd-core` publishes to Maven Local with the requested release version.
- The branch proves that release coordinates can be injected from `-Prelease.version=...` without using the remote Central repository.

## Plugin Marker Verification

When plugin marker coordinates change, generate marker publications locally and verify the marker POMs before tagging.

Generate the marker POMs with the intended release coordinate:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:generatePomFileForCap4kPipelinePluginMarkerMavenPublication :cap4k-plugin-code-analysis-flow-export:generatePomFileForCap4kFlowExportPluginMarkerMavenPublication "-Prelease.version=0.7.0"
```

Then run a release-shaped local publish:

```powershell
.\gradlew.bat check publishToMavenLocal "-Prelease.version=0.7.0"
```

Confirm Maven Local contains the implementation module and expected marker publications.

## Remote Central Verification Steps

The real release is tag-driven. The workflow runs from the commit referenced by the pushed tag, and the workflow enforces that the tagged commit must be contained in `origin/publish/maven-central`.

1. Promote `master` to `publish/maven-central` by pull request.
2. Confirm the merged `publish/maven-central` commit contains the intended release content.
3. Identify the exact commit to release and tag that commit explicitly.
4. Verify locally that the tag points to the intended commit before pushing the tag.
5. Verify that the tagged commit is reachable from `origin/publish/maven-central`.
6. Push the release tag such as `v0.7.0`.
7. Watch the release workflow with `gh`.
8. Verify the Central artifact page.
9. Verify the GitHub release page.

Suggested commands:

```powershell
git fetch origin publish/maven-central
git switch publish/maven-central
git pull --ff-only
git rev-parse HEAD
git tag v0.7.0 <commit-sha>
git show --no-patch --decorate v0.7.0
git branch -r --contains <commit-sha>
git push origin v0.7.0
gh run list --repo LDmoxeii/cap4k --workflow "Maven Central Release" --limit 5
gh run view <run-id> --repo LDmoxeii/cap4k
```

Operator checks:

- If the intended release commit is `HEAD` on `publish/maven-central`, replace `<commit-sha>` with the value from `git rev-parse HEAD`.
- If the intended release commit is not `HEAD`, tag that exact commit instead of assuming the branch tip is correct.
- Do not push the tag until `git show --no-patch --decorate v0.7.0` confirms the tag target is the commit you intend to release.
- Do not push the tag until `git branch -r --contains <commit-sha>` shows `origin/publish/maven-central`.

Expected remote verification results:

- The `Maven Central Release` workflow starts from the pushed `v<major>.<minor>.<patch>` tag.
- The workflow runs the repository state from the commit referenced by the tag.
- The workflow derives `RELEASE_VERSION=<major>.<minor>.<patch>`.
- The workflow fails before publish if the tagged commit is not contained in `origin/publish/maven-central`.
- The workflow completes `buildSrc` tests, `check`, `publish`, uploads the compatibility repository to Central Portal with automatic publishing, and creates the GitHub Release.
- Central shows the published artifact page for `io.github.ldmoxeii/ddd-core`.
- GitHub shows a release page for the tag.

Reference pages:

- Central artifact page: `https://central.sonatype.com/artifact/io.github.ldmoxeii/ddd-core`
- Maven repository page: `https://repo1.maven.org/maven2/io/github/ldmoxeii/ddd-core/`
- GitHub releases: `https://github.com/LDmoxeii/cap4k/releases`

## Workflow Trigger And Tag-Format Behavior

`maven-central-release.yml` listens broadly on `push.tags = "v*"`, but the job itself is intentionally stricter.

- Workflow trigger: any pushed tag beginning with `v`.
- Job gate: `Derive release version` requires exact `v<major>.<minor>.<patch>` format.
- Branch gate: `Verify tagged commit is on publish branch` requires the tagged commit to be contained in `origin/publish/maven-central`.

Examples:

- accepted: `v0.7.0`
- accepted: `v1.2.3`
- rejected by the job: `v0.7`
- rejected by the job: `v0.7.0-rc1`
- rejected by the job: `version-0.7.0`

If a correctly formatted tag points to a commit outside `origin/publish/maven-central`, the workflow should fail immediately before any publish or upload step runs.

## Failure Triage Notes

### Resolver test failure

- Re-run `..\gradlew.bat test --tests "buildsrc.convention.CentralReleaseVersionTest"` from `buildSrc`.
- Inspect changes in `buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt`.
- Confirm release validation still matches the exact `major.minor.patch` format expected by the workflow.

### `check publishToMavenLocal` failure

- Inspect the first failing module or test instead of assuming a publication problem.
- Confirm the branch still allows local publication without Central credentials.
- Treat any remote-publication attempt during this command as a regression.

### Remote workflow fails before publish

- Inspect the `Derive release version` step first.
- Confirm the pushed tag is exactly `v<major>.<minor>.<patch>`.
- Confirm the tag points to the intended release commit.
- Inspect `Verify tagged commit is on publish branch` next.
- Confirm `origin/publish/maven-central` contains the tagged commit before the tag is pushed.

### Remote workflow publish or upload failure

- Verify `CENTRAL_USERNAME` and `CENTRAL_PASSWORD`.
- Verify `SIGNING_KEY` and `SIGNING_PASSWORD`.
- Verify the matching public key is already retrievable from a Sonatype-supported public key server before retrying the tag.
- Inspect the `Publish` step logs before retrying with another tag.
- Inspect the compatibility upload call to `https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/io.github.ldmoxeii?publishing_type=automatic`.
- Verify the compatibility API uses `Authorization: Bearer <base64(username:password)>` instead of Basic Auth.
- If the upload call returns an error body, inspect that payload before retrying with another tag.

### Release page or Central page missing after successful workflow completion

- Confirm the workflow reached both `Upload staging repository to Central Portal` and `Create GitHub Release`.
- Re-check Central indexing delay before assuming publication failure.
- Treat a missing GitHub release after a successful run as a workflow defect, not as proof that artifact publication failed.

