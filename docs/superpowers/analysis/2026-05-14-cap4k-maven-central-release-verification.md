# cap4k Maven Central Release Verification

Date: 2026-05-14
Branch: `verify/maven-central`
Worktree: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\verify-maven-central`

## Required GitHub Repository Secrets

Configure these repository secrets on `LDmoxeii/cap4k` before the first real Maven Central release:

- `CENTRAL_USERNAME`
- `CENTRAL_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

Value format notes:

- `CENTRAL_USERNAME`: the actual Sonatype Central Portal username used for the compatibility upload and publish credentials, not a display name.
- `CENTRAL_PASSWORD`: the matching Sonatype Central Portal password or token value consumed as the Gradle/Central password, not a file path.
- `SIGNING_KEY`: the full armored ASCII private PGP key block consumed by `useInMemoryPgpKeys(...)`, including the `-----BEGIN PGP PRIVATE KEY BLOCK-----` and `-----END PGP PRIVATE KEY BLOCK-----` lines. Do not store a key id, fingerprint, or file path here.
- `SIGNING_PASSWORD`: the passphrase for the private key stored in `SIGNING_KEY`. Leave it empty only if the private key itself has no passphrase.

## Local Structural Verification Commands

Run these commands from the `verify/maven-central` worktree to validate the branch structure before the first real release:

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
.\gradlew.bat :ddd-core:publishMavenPublicationToMavenLocal "-Prelease.version=0.5.0"
```

Expected outcome:

- Gradle exits successfully.
- `ddd-core` publishes to Maven Local with version `0.5.0`.
- The branch proves that release coordinates can be injected from `-Prelease.version=...` without using the remote Central repository.

## First Remote Central Verification Steps

The first real release verification is tag-driven. The workflow runs from whatever commit the pushed tag points to. Keeping `publish/maven-central` as the canonical release channel branch is still recommended, and the workflow now enforces that the tagged commit must be contained in `origin/publish/maven-central`. Pushing that branch by itself still does not trigger a release.

1. Merge `verify/maven-central` into `publish/maven-central`.
2. Push `publish/maven-central` so the canonical release branch contains the intended release commit.
3. Identify the exact commit to release and tag that commit explicitly.
4. Verify locally that the tag points to the intended commit before pushing the tag.
5. Verify that the tagged commit is reachable from `origin/publish/maven-central`.
6. Push the release tag such as `v0.5.0`.
7. Watch the release workflow with `gh`.
8. Verify the Central artifact page.
9. Verify the GitHub release page.

Suggested commands:

```powershell
git checkout publish/maven-central
git merge --ff-only verify/maven-central
git push origin publish/maven-central
git rev-parse HEAD
git tag v0.5.0 <commit-sha>
git show --no-patch --decorate v0.5.0
git branch -r --contains <commit-sha>
git push origin v0.5.0
gh run list --repo LDmoxeii/cap4k --workflow "Maven Central Release" --limit 5
gh run view <run-id> --repo LDmoxeii/cap4k
```

Operator check:

- If the intended release commit is `HEAD` on `publish/maven-central`, replace `<commit-sha>` with the value from `git rev-parse HEAD`.
- If the intended release commit is not `HEAD`, tag that exact commit instead of assuming the branch tip is correct.
- Do not push the tag until `git show --no-patch --decorate v0.5.0` confirms the tag target is the commit you intend to release.
- Do not push the tag until `git branch -r --contains <commit-sha>` shows `origin/publish/maven-central`.

Expected remote verification results:

- The `Maven Central Release` workflow starts from the pushed `v0.5.0` tag.
- The workflow runs the repository state from the commit referenced by `v0.5.0`.
- The workflow derives `RELEASE_VERSION=0.5.0`.
- The workflow fails before publish if the tagged commit is not contained in `origin/publish/maven-central`.
- The workflow completes `buildSrc` tests, `check`, `publish`, uploads the compatibility repository to Central Portal with automatic publishing, and then creates the GitHub Release.
- Central shows the published artifact page for `io.github.ldmoxeii/ddd-core`.
- GitHub shows a release page for tag `v0.5.0`.

Reference pages:

- Central artifact page: `https://central.sonatype.com/artifact/io.github.ldmoxeii/ddd-core`
- GitHub release page: `https://github.com/LDmoxeii/cap4k/releases/tag/v0.5.0`

## Workflow Trigger And Tag-Format Behavior

`maven-central-release.yml` listens broadly on `push.tags = "v*"`, but the job itself is intentionally stricter.

- Workflow trigger: any pushed tag beginning with `v`
- Job gate: `Derive release version` requires exact `v<major>.<minor>.<patch>` format
- Branch gate: `Verify tagged commit is on publish branch` requires the tagged commit to be contained in `origin/publish/maven-central`

Examples:

- accepted: `v0.5.0`
- accepted: `v1.2.3`
- rejected by the job: `v0.5`
- rejected by the job: `v0.5.0-rc1`
- rejected by the job: `version-0.5.0`

This means a tag like `v0.5.0-rc1` can still trigger the workflow because it matches `v*`, but the job should fail immediately during version derivation instead of publishing anything.

If a correctly formatted tag points to a commit outside `origin/publish/maven-central`, the workflow should also fail immediately before any publish or upload step runs.

## Failure Triage Notes

### Resolver test failure

- Re-run `..\gradlew.bat test --tests "buildsrc.convention.CentralReleaseVersionTest"` from `buildSrc`.
- Inspect changes in `buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt`.
- Confirm release validation still matches the exact `major.minor.patch` format expected by the workflow.

### `check publishToMavenLocal` failure

- Inspect the first failing module or test instead of assuming a publication problem.
- Confirm the branch still allows local publication without Central credentials.
- Treat any remote-publication attempt during this command as a regression.

### `publishMavenPublicationToMavenLocal` with `-Prelease.version=0.5.0` failure

- Confirm the release version property is passed exactly as `0.5.0`.
- Inspect generated publication coordinates for `ddd-core`.
- Treat any signing or Central-credential requirement during Maven Local publication as a regression.

### Remote workflow fails before publish

- Inspect the `Derive release version` step first.
- Confirm the pushed tag is exactly `v<major>.<minor>.<patch>`.
- Confirm the tag points to the intended release commit.
- Inspect `Verify tagged commit is on publish branch` next.
- Confirm `origin/publish/maven-central` was pushed before the tag.
- Confirm the tagged commit contains the expected `publish/maven-central` content after the merge and is reachable from that remote branch.

### Remote workflow publish or upload failure

- Verify `CENTRAL_USERNAME` and `CENTRAL_PASSWORD`.
- Verify `SIGNING_KEY` and `SIGNING_PASSWORD`.
- Inspect the `Publish` step logs before retrying with another tag.
- Inspect the compatibility upload call to `https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/io.github.ldmoxeii?publishing_type=automatic`.
- Verify the compatibility API uses `Authorization: Bearer <base64(username:password)>` instead of Basic Auth.
- If the upload call returns an error body, inspect that payload before retrying with another tag.

### Release page or Central page missing after successful workflow completion

- Confirm the workflow reached both `Upload staging repository to Central Portal` and `Create GitHub Release`.
- Re-check Central indexing delay before assuming publication failure.
- Treat a missing GitHub release after a successful run as a workflow defect, not as proof that artifact publication failed.
