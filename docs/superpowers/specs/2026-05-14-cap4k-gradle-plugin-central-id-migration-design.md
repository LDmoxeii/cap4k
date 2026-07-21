# cap4k Gradle Plugin Central ID Migration Design

Date: 2026-05-14

## Context

`cap4k` now publishes regular Maven artifacts to Maven Central under the verified namespace `io.github.ldmoxeii`, and `v0.5.4` has already proven that the Central release line works for normal module artifacts.

That is not yet enough for downstream Gradle consumers that rely on `plugins {}` resolution.

The current blocking facts are:

- `cap4k-plugin-pipeline-gradle` still declares the plugin id `com.only4.cap4k.plugin.pipeline`
- `cap4k-plugin-code-analysis-flow-export` still declares the plugin id `com.only4.cap4k.plugin.codeanalysis.flow-export`
- Gradle plugin marker publications use coordinates derived from the plugin id itself
- Maven Central only accepts coordinates under the verified namespace, which is `io.github.ldmoxeii`
- the Central release line currently disables all plugin marker publication tasks to avoid publishing invalid marker coordinates
- `cap4k-reference-content-studio` currently depends on local or prebuilt `cap4k` artifacts because it cannot resolve the pipeline plugin from Central with `plugins {}`

The user decided not to keep a compatibility track for the legacy plugin ids. The migration is a full cutover to new plugin ids.

## Goals

- Move all externally published `cap4k` Gradle plugins to plugin ids under `io.github.ldmoxeii.*`.
- Publish valid plugin markers to Maven Central for those new ids.
- Stop treating legacy `com.only4.*` plugin ids as supported release-time identifiers.
- Make `cap4k-reference-content-studio` able to consume the pipeline plugin from Maven Central on `master`, without `mavenLocal()` or private mirrors.
- Use a new release version that reflects a consumer-visible break in plugin usage.

## Non-Goals

- Preserving plugin id compatibility with `com.only4.*`.
- Introducing a long-lived dual-id transition window.
- Publishing `cap4k-reference-content-studio` itself to Maven Central.
- Redesigning unrelated library coordinates that already work under `io.github.ldmoxeii`.
- Reworking the `master -> publish/maven-central` branch model.

## Scope

This change covers:

- `cap4k`
  - published Gradle plugin modules
  - Central publication filtering logic
  - plugin-related functional tests and fixtures
  - documentation that advertises plugin ids
- `cap4k-reference-content-studio`
  - build entrypoint plugin resolution
  - framework dependency coordinates
  - repository policy on `master`

This change does not cover:

- downstream third-party projects outside the workspace
- a compatibility shim for old plugin ids

## Published Plugin Inventory

The repository currently has two externally published Gradle plugin modules:

1. `cap4k-plugin-pipeline-gradle`
2. `cap4k-plugin-code-analysis-flow-export`

`buildSrc` also uses `java-gradle-plugin`, but it is internal build logic and not part of the external Central plugin story.

## Chosen Migration Model

The migration is a full, forward-only cutover.

- Every published external plugin id moves to the verified `io.github.ldmoxeii.*` namespace.
- Legacy `com.only4.*` plugin ids are removed from the formal release line.
- Maven Central becomes the only supported public source for these plugin markers on `master`.
- The first release that carries this change is `0.6.0`.

This model is intentionally simpler than dual-track compatibility. It aligns the plugin marker coordinates, the Maven namespace, and the public consumption story around one stable identity.

## Plugin ID Mapping

The migration uses these exact replacements.

### Pipeline plugin

- old: `com.only4.cap4k.plugin.pipeline`
- new: `io.github.ldmoxeii.cap4k.pipeline`

### Flow export plugin

- old: `com.only4.cap4k.plugin.codeanalysis.flow-export`
- new: `io.github.ldmoxeii.cap4k.codeanalysis.flow-export`

## Mapping Rule

The repository should follow one public naming rule for external Gradle plugins:

```text
com.only4.cap4k.plugin.<suffix>
-> io.github.ldmoxeii.cap4k.<suffix>
```

This rule keeps `cap4k` visible in the id, drops the old organization prefix, and makes the marker coordinates legal for the verified Central namespace.

## cap4k Release-Line Design

### Plugin declarations

Each external `java-gradle-plugin` module should declare only the new plugin id.

There should be no release-time declaration for the old plugin id in the final design.

### Plugin marker publication policy

The current Central release line globally disables all plugin marker publications. That policy must change.

New behavior:

- allow plugin marker publications for external plugins whose ids already live under `io.github.ldmoxeii.*`
- do not carry any old `com.only4.*` plugin marker declarations forward

This means the release line no longer needs a blanket "disable all plugin marker publications" rule. It needs a targeted policy that only suppresses invalid marker publications if any legacy plugin ids still exist during intermediate work, but the final merged state should publish the two new marker families normally.

### Version policy

The first Central release that includes the new plugin ids is `0.6.0`.

Reason:

- plugin id is a user-facing consumption contract
- switching the required plugin id is a breaking public change
- continuing with `0.5.x` would understate the migration impact

## cap4k Test and Fixture Migration

The `cap4k` repository must migrate all plugin-id-sensitive verification surfaces to the new ids, including:

- TestKit and functional test sample builds
- plugin application assertions
- generated fixture inputs that use `plugins {}`
- docs or examples inside the repository that show plugin application

The goal is that repository-local tests validate the same plugin ids that Central consumers will use after `0.6.0`.

## cap4k-reference-content-studio Consumption Design

After `cap4k 0.6.0` is published, `cap4k-reference-content-studio master` should move to a contributor-friendly public-consumption baseline.

### Plugin application

Use:

```kotlin
plugins {
    id("io.github.ldmoxeii.cap4k.pipeline") version "0.6.0"
}
```

### Repository policy

`master` should keep only public repositories needed for normal builds.

Required outcome:

- no `mavenLocal()`
- no Aliyun mirror
- no private repository
- no local-prepublish prerequisite

The default policy should be plain `mavenCentral()`, plus Gradle Plugin Portal only where Gradle itself still needs it for plugin resolution.

### Dependency coordinates

All old framework coordinates like:

- `com.only4:ddd-core:0.5.0-SNAPSHOT`
- `com.only4:ddd-domain-repo-jpa:0.5.0-SNAPSHOT`
- `com.only4:cap4k-ddd-starter:0.5.0-SNAPSHOT`

must move to released `io.github.ldmoxeii:*:0.6.0` coordinates.

The outcome must be a fresh clone that builds against public artifacts, not a workspace-local build chain.

## Branching and Delivery Strategy

The work should land in two phases.

### Phase 1: cap4k

1. implement new plugin ids in `cap4k`
2. restore valid plugin marker publication for the new ids on `publish/maven-central`
3. verify the release line locally
4. publish `v0.6.0`

### Phase 2: cap4k-reference-content-studio

1. switch `master` to the new plugin id and Central-only dependencies
2. remove local and mirror repository assumptions
3. verify that the project builds from public repositories only

This order matters. `cap4k-reference-content-studio` should not be switched before `cap4k 0.6.0` exists publicly.

## Verification Strategy

Verification is split by repository.

### cap4k verification

Before release:

- `check`
- `publishToMavenLocal`
- explicit inspection that plugin marker publications are generated for the new ids
- confirmation that no release-time plugin marker uses `com.only4.*`

After release:

- confirm Central contains the implementation artifacts
- confirm Central contains the new plugin marker artifacts
- confirm the markers resolve to the expected implementation modules at `0.6.0`

### cap4k-reference-content-studio verification

After `cap4k 0.6.0` is public:

- build on `master` with public repositories only
- no local `cap4k` prepublish
- `check` must pass from a clean clone scenario

The proof condition is simple: a normal user must be able to clone `cap4k-reference-content-studio` and build it without first building `cap4k`.

## Risks

### Consumer break

Any existing consumer using old plugin ids will break when adopting `0.6.0`.

That is accepted by design. The release note must call this out explicitly.

### Incomplete test migration

If internal fixtures still reference old plugin ids, local verification may pass incompletely or fail late in release preparation.

The implementation must search broadly for old plugin-id references, not only adjust module declarations.

### Partial publication logic

If the publication filter still suppresses the new marker tasks, Central will publish only implementation artifacts and the migration goal will not be met.

This is the main release-line regression risk and must be covered by targeted verification.

## Success Criteria

This design is complete only when all of the following are true:

1. `cap4k 0.6.0` is published to Central.
2. The new plugin markers for the migrated external plugins are available from Central.
3. No formal public docs in the workspace still advertise the old plugin ids as the supported path.
4. `cap4k-reference-content-studio master` builds against public repositories and released `io.github.ldmoxeii` artifacts only.
5. Users no longer need to build `cap4k` locally before trying `cap4k-reference-content-studio`.
