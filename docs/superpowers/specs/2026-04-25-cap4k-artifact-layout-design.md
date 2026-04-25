# 2026-04-25 cap4k Artifact Layout Design

## Background

The pipeline generator currently decides generated package names, output directories, and output paths in several different places.

The problem is not that design artifacts and DB artifacts need different routing systems. The problem is that all generated artifact families lack one placement contract.

Different sources currently enter the pipeline differently:

- `design.json` provides semantic package segments
- DB snapshots provide table names that become semantic table segments
- enum manifests provide enum package segments
- IR analysis snapshots provide flow and drawing-board graph data

After source normalization, all generated artifacts should use the same family layout resolver. Source origin must not create a second placement mechanism.

This caused a visible code-generation defect in `only-danmaku-next`:

- `UserMessageCreatedDomainEvent` was generated under `edu.only4.danmaku.domain.message.events`
- the project convention expects `edu.only4.danmaku.domain.aggregates.message.events`

The defect is not only a `designDomainEvent` bug. It exposes a broader missing abstraction: generated artifact placement is not centralized, configurable, or consistently explainable in `cap4kPlan`.

The same rule also applies to analysis outputs such as `flow` and `drawing-board`. They do not have Kotlin packages, but their output directories are still artifact layout. They must not remain configured through a separate generator-specific location mechanism.

## Current Problems

### 1. Defaults Are Hidden In Implementation Code

The current defaults are scattered across canonical assembly, generator planner code, and generator-specific Gradle options.

Examples:

- aggregate entity: `${basePackage}.domain.aggregates.$segment`
- aggregate schema: `${basePackage}.domain._share.meta.$segment`
- aggregate repository: `${basePackage}.adapter.domain.repositories`
- design command: `${basePackage}.application.commands.${command.packageName}`
- design query: `${basePackage}.application.queries.${query.packageName}`
- design domain event: `${basePackage}.domain.${event.packageName}.events`
- flow output: generator option `generators.flow.outputDir`, defaulting to `flows`
- drawing-board output: generator option `generators.drawingBoard.outputDir`, defaulting to `design`

The user cannot inspect or override these defaults as one coherent configuration surface.

### 2. Template Override Cannot Fix Placement

Template override can change file content, but it cannot reliably change artifact placement.

The planner decides `ArtifactPlanItem.outputPath` before rendering. Other generated artifacts also receive imports from planner or render-model code.

If a user changes only the template package declaration, these values can diverge:

- file path
- Kotlin `package`
- imports from related generated files
- `cap4kPlan` output path

That is worse than having no override, because it produces inconsistent generated output.

### 3. Every Artifact Family Needs The Same Capability

Aggregate artifacts are not driven by `design.json`, but they still need the same artifact-family layout mechanism as every other generated artifact.

Analysis output artifacts are not Kotlin source artifacts, but they still need the same artifact-family layout mechanism for output directories.

For example, entity placement is currently finalized during canonical assembly:

```kotlin
EntityModel.packageName = "${config.basePackage}.domain.aggregates.$segment"
```

Later planners use that complete package name directly:

```kotlin
outputPath = "$domainRoot/src/main/kotlin/${entity.packageName.replace(".", "/")}/${entity.name}.kt"
```

Therefore, entity placement cannot be solved by changing design providers only. If the entity root changes, related references must change together:

- entity type FQN
- Q entity FQN
- factory FQN
- specification FQN
- aggregate wrapper FQN
- local enum FQN
- schema references
- repository entity imports
- relation target imports

### 4. Old Runtime Flexibility Should Not Return As-Is

The old codegen line had `templatePackage` and runtime context based placement.

That was flexible, but it also allowed too much dynamic behavior:

- placement rules were not strongly typed
- generator semantics and template runtime were tightly coupled
- plan output was harder to reason about
- package and path correctness depended on convention spread across templates and runtime context

The new pipeline should keep deterministic typed planning, but it needs a bounded placement configuration layer.

## Goals

- Provide explicit default layout configuration for generated artifact families
- Support overriding package roots or output roots by artifact family
- Keep module ownership fixed by framework convention in the first version
- Use one resolver for package names, output directories, output paths, and generated imports
- Make `cap4kPlan` reflect the final resolved output paths
- Cover every current generated artifact family through the same layout contract, regardless of source or artifact type
- Fix the default `designDomainEvent` route to `domain.aggregates.<package>.events`
- Avoid bringing back the old unified JSON runtime

## Non-Goals

- Do not support per-table placement overrides
- Do not support per-entity placement overrides
- Do not support per-file output path overrides
- Do not let users move domain artifacts into adapter or application modules
- Do not make templates responsible for file placement
- Do not add arbitrary path expressions or scripting
- Do not change source formats such as `design.json` or DB snapshots in this slice
- Do not introduce compatibility shims for long-term use on `master`

## Design Summary

Add a project-level artifact layout configuration.

The layout expresses where each artifact family lives.

There is one layout contract with location shapes based on artifact type:

- Kotlin source families use package layout relative to `basePackage`
- project resource families use output-root layout relative to the project root

It is not a source. It is not a template concern. It is not a replacement for generator enablement.

The Kotlin source contract is:

```text
final package = basePackage + packageRoot + semanticPackage + packageSuffix
output path   = module root + src/main/kotlin + final package path + type name
```

The project resource contract is:

```text
output path = project root + outputRoot + semantic relative path
```

Not every artifact family uses all segments:

- aggregate entity uses `packageRoot + tableSegment`
- aggregate factory uses `entityPackage + factory`
- aggregate schema uses `packageRoot + tableSegment`
- aggregate repository uses only `packageRoot`
- design command uses `packageRoot + design package`
- design domain event uses `packageRoot + design package + packageSuffix`
- flow uses `outputRoot + flow file name`
- drawing-board uses `outputRoot + drawing board file name`

The key point is that every package and path must be resolved by the same layout resolver.

## DSL Contract

Add a top-level `layout` block under `cap4k`.

Example with default-equivalent values:

```kotlin
cap4k {
    layout {
        aggregate {
            packageRoot.set("domain.aggregates")
        }

        aggregateSchema {
            packageRoot.set("domain._share.meta")
        }

        aggregateRepository {
            packageRoot.set("adapter.domain.repositories")
        }

        aggregateSharedEnum {
            packageRoot.set("domain")
            defaultPackage.set("shared")
            packageSuffix.set("enums")
        }

        aggregateEnumTranslation {
            packageRoot.set("domain.translation")
        }

        aggregateUniqueQuery {
            packageRoot.set("application.queries")
            packageSuffix.set("unique")
        }

        aggregateUniqueQueryHandler {
            packageRoot.set("adapter.queries")
            packageSuffix.set("unique")
        }

        aggregateUniqueValidator {
            packageRoot.set("application.validators")
            packageSuffix.set("unique")
        }

        flow {
            outputRoot.set("flows")
        }

        drawingBoard {
            outputRoot.set("design")
        }

        designCommand {
            packageRoot.set("application.commands")
        }

        designQuery {
            packageRoot.set("application.queries")
        }

        designClient {
            packageRoot.set("application.distributed.clients")
        }

        designQueryHandler {
            packageRoot.set("adapter.queries")
        }

        designClientHandler {
            packageRoot.set("adapter.application.distributed.clients")
        }

        designValidator {
            packageRoot.set("application.validators")
        }

        designApiPayload {
            packageRoot.set("adapter.portal.api.payload")
        }

        designDomainEvent {
            packageRoot.set("domain.aggregates")
            packageSuffix.set("events")
        }

        designDomainEventHandler {
            packageRoot.set("application")
            packageSuffix.set("events")
        }
    }
}
```

The default values must be provided by the DSL/config model, even when the user does not write the block.

Implementation code should not keep these defaults scattered as string concatenation or generator-specific output directory defaults.

## Supported Configuration Shape

Each first-version Kotlin source layout entry supports:

```kotlin
packageRoot: Property<String>
packageSuffix: Property<String>
defaultPackage: Property<String>
```

`packageSuffix` is optional and defaults to an empty string unless the family needs a suffix by convention.

`defaultPackage` is optional and is used only for families whose source may omit a semantic package, such as shared enums.

Each first-version project resource layout entry supports:

```kotlin
outputRoot: Property<String>
```

`outputRoot` is a relative filesystem path from the project root.

The first version does not expose:

- `moduleRole`
- raw `outputDir`
- custom file name
- per-table rules
- per-entity rules
- arbitrary package expressions
- arbitrary output path expressions

This is intentional. Artifact family layout is configurable, module ownership is not.

For non-Kotlin project resource artifacts, output root is configurable, but individual file names are still framework-owned.

## Artifact Family Layout Rules

All generated output is resolved by artifact family.

Source data may provide the semantic segment for an artifact, but it does not choose the final location.

### Aggregate Families

DB source data should remain factual:

- table name
- column names
- primary keys
- relations
- comments
- annotations

DB source data must not decide Kotlin package layout.

Canonical assembly is the correct place to turn DB facts into aggregate semantic models, but it must use layout configuration instead of hardcoded package roots.

#### Aggregate Entity

Default:

```text
basePackage.domain.aggregates.<tableSegment>.<EntityName>
```

With custom root:

```kotlin
cap4k {
    layout {
        aggregate {
            packageRoot.set("domain.model")
        }
    }
}
```

The entity becomes:

```text
basePackage.domain.model.<tableSegment>.<EntityName>
```

The table segment remains generated from the DB table name. This slice does not allow overriding it per table.

#### Entity-Attached Aggregate Artifacts

These artifacts follow the resolved entity package:

- aggregate wrapper
- factory
- specification
- local enum

Default examples:

```text
entity        -> basePackage.domain.aggregates.user_message.UserMessage
wrapper       -> basePackage.domain.aggregates.user_message.AggUserMessage
factory       -> basePackage.domain.aggregates.user_message.factory.UserMessageFactory
specification -> basePackage.domain.aggregates.user_message.specification.UserMessageSpecification
local enum    -> basePackage.domain.aggregates.user_message.enums.Status
```

If the aggregate entity root changes, these generated FQNs must move with it.

They should not each require a separate first-version package root unless there is a proven need.

#### Aggregate Schema

Schema is not entity-attached in the same way. It is a shared metadata family.

Default:

```text
basePackage.domain._share.meta.<tableSegment>.S<EntityName>
```

Custom root:

```kotlin
cap4k {
    layout {
        aggregateSchema {
            packageRoot.set("domain.meta")
        }
    }
}
```

Result:

```text
basePackage.domain.meta.<tableSegment>.S<EntityName>
```

`Schema.kt` base support follows the schema family root:

```text
basePackage.domain._share.meta.Schema
```

If `aggregateSchema.packageRoot` is changed to `domain.meta`, the base schema package becomes:

```text
basePackage.domain.meta.Schema
```

#### Aggregate Repository

Repository has a separate family root because it belongs to the adapter module by framework convention.

Default:

```text
basePackage.adapter.domain.repositories.<RepositoryName>
```

Custom root:

```kotlin
cap4k {
    layout {
        aggregateRepository {
            packageRoot.set("adapter.persistence.repositories")
        }
    }
}
```

Result:

```text
basePackage.adapter.persistence.repositories.<RepositoryName>
```

Repository remains in the adapter module. The first version must not allow moving it to domain or application.

#### Unique Constraint Families

Unique query, unique query handler, and unique validator are aggregate families, but they are not attached to the entity package.

Their defaults should be represented in layout:

```text
aggregateUniqueQuery        -> application.queries.<tableSegment>.unique
aggregateUniqueQueryHandler -> adapter.queries.<tableSegment>.unique
aggregateUniqueValidator    -> application.validators.<tableSegment>.unique
```

They must be exposed in layout with fixed module ownership:

```text
aggregateUniqueQuery        -> application module
aggregateUniqueQueryHandler -> adapter module
aggregateUniqueValidator    -> application module
```

The table segment remains generated from the DB table name.

#### Shared Enum And Enum Translation

Shared enum placement is a family-level layout concern, but enum manifest entries may still provide a semantic package segment.

Default:

```text
aggregateSharedEnum -> basePackage.domain.shared.enums.<EnumName>
```

The default can be described as:

```text
packageRoot    = domain
defaultPackage = shared
packageSuffix  = enums
```

If an enum manifest entry provides a short package such as `quality`, the final package becomes:

```text
basePackage.domain.quality.enums.<EnumName>
```

Explicit fully qualified enum packages remain source-owned and are not rewritten by layout.

Enum translation is generated in the adapter module but keeps the existing package convention under `domain.translation`.

Default:

```text
aggregateEnumTranslation -> basePackage.domain.translation.<scope>.<EnumName>Translation
```

The `<scope>` is `shared` for shared enum translations and the aggregate table segment for local enum translations.

### Design Families

`design.json` `package` remains a semantic subpackage.

It must not become a full generated package root.

Example:

```json
{
  "tag": "domain_event",
  "package": "message",
  "name": "UserMessageCreated"
}
```

The final package is resolved by layout:

```text
basePackage + designDomainEvent.packageRoot + message + designDomainEvent.packageSuffix
```

Default:

```text
basePackage.domain.aggregates.message.events.UserMessageCreatedDomainEvent
```

This fixes the current wrong route:

```text
basePackage.domain.message.events.UserMessageCreatedDomainEvent
```

#### Design Request Families

Default packages:

```text
designCommand -> basePackage.application.commands.<designPackage>
designQuery   -> basePackage.application.queries.<designPackage>
designClient  -> basePackage.application.distributed.clients.<designPackage>
```

The source `package` value remains the same. Only the family root changes.

#### Design Handler Families

Default packages:

```text
designQueryHandler  -> basePackage.adapter.queries.<designPackage>
designClientHandler -> basePackage.adapter.application.distributed.clients.<designPackage>
```

Handlers keep their framework-owned module roles.

#### Design Domain Event Families

Default packages:

```text
designDomainEvent        -> basePackage.domain.aggregates.<designPackage>.events
designDomainEventHandler -> basePackage.application.<designPackage>.events
```

The event handler must import the event type through the same resolver.

It must not independently reconstruct:

```text
basePackage.domain.<designPackage>.events.<Type>
```

#### Design Validator And API Payload

Default packages:

```text
designValidator  -> basePackage.application.validators.<designPackage>
designApiPayload -> basePackage.adapter.portal.api.payload.<designPackage>
```

These families are included because template override and user project layout needs apply equally to them.

### Analysis Families

Analysis output families are not Kotlin source artifacts, so they do not use `packageRoot`.

They still use the same artifact family layout contract through `outputRoot`.

The current generator-specific options:

```kotlin
generators {
    flow {
        outputDir.set("flows")
    }
    drawingBoard {
        outputDir.set("design")
    }
}
```

must move to layout:

```kotlin
layout {
    flow {
        outputRoot.set("flows")
    }
    drawingBoard {
        outputRoot.set("design")
    }
}
```

Generator blocks should keep enablement and generator-specific behavior only. They should not own artifact location.

#### Flow

Default:

```text
flow -> flows
```

Generated files:

```text
flows/<flowSlug>.json
flows/<flowSlug>.mmd
flows/index.json
```

Custom output root:

```kotlin
cap4k {
    layout {
        flow {
            outputRoot.set("build/cap4k/flows")
        }
    }
}
```

Result:

```text
build/cap4k/flows/<flowSlug>.json
build/cap4k/flows/<flowSlug>.mmd
build/cap4k/flows/index.json
```

#### Drawing Board

Default:

```text
drawingBoard -> design
```

Generated files:

```text
design/drawing_board_cmd.json
design/drawing_board_qry.json
design/drawing_board_cli.json
design/drawing_board_payload.json
design/drawing_board_de.json
```

Custom output root:

```kotlin
cap4k {
    layout {
        drawingBoard {
            outputRoot.set("build/cap4k/design")
        }
    }
}
```

Result:

```text
build/cap4k/design/drawing_board_cmd.json
build/cap4k/design/drawing_board_qry.json
build/cap4k/design/drawing_board_cli.json
build/cap4k/design/drawing_board_payload.json
build/cap4k/design/drawing_board_de.json
```

The individual file names stay framework-owned. The user controls the family output root only.

## Resolver Design

Introduce a central resolver, tentatively named `ArtifactPackageLayoutResolver`.

The resolver owns all package-root defaults, output-root defaults, package construction, and path construction.

It should expose intention-level functions, not raw string helpers:

```kotlin
interface ArtifactPackageLayoutResolver {
    fun aggregateEntityPackage(tableSegment: String): String
    fun aggregateSchemaPackage(tableSegment: String): String
    fun aggregateSchemaBasePackage(): String
    fun aggregateRepositoryPackage(): String
    fun aggregateSharedEnumPackage(enumPackage: String): String
    fun aggregateEnumTranslationPackage(scope: String): String
    fun aggregateUniqueQueryPackage(tableSegment: String): String
    fun aggregateUniqueQueryHandlerPackage(tableSegment: String): String
    fun aggregateUniqueValidatorPackage(tableSegment: String): String

    fun aggregateFactoryPackage(entityPackage: String): String
    fun aggregateSpecificationPackage(entityPackage: String): String
    fun aggregateWrapperPackage(entityPackage: String): String
    fun aggregateLocalEnumPackage(entityPackage: String): String

    fun designCommandPackage(designPackage: String): String
    fun designQueryPackage(designPackage: String): String
    fun designClientPackage(designPackage: String): String
    fun designQueryHandlerPackage(designPackage: String): String
    fun designClientHandlerPackage(designPackage: String): String
    fun designValidatorPackage(designPackage: String): String
    fun designApiPayloadPackage(designPackage: String): String
    fun designDomainEventPackage(designPackage: String): String
    fun designDomainEventHandlerPackage(designPackage: String): String

    fun flowOutputRoot(): String
    fun drawingBoardOutputRoot(): String
}
```

The concrete API can be adjusted during implementation, but call sites should express artifact intent instead of manually concatenating package fragments.

## Output Path Resolution

Package resolution and output path resolution must be connected.

For Kotlin source artifacts, the resolver should provide or support a shared helper:

```text
moduleRoot/src/main/kotlin/<resolvedPackagePath>/<typeName>.kt
```

For project resource artifacts, the resolver should provide or support a shared helper:

```text
<outputRoot>/<relativeFileName>
```

This helper must be used by planners instead of duplicating:

```kotlin
"$root/src/main/kotlin/${packageName.replace(".", "/")}/$typeName.kt"
```

or:

```kotlin
"$outputDir/$fileName"
```

Centralizing this is important because `cap4kPlan` is only useful if it reports the same final path that generation will write.

## Configuration Model

Add layout configuration to the pipeline API config model.

Suggested shape:

```kotlin
data class ProjectConfig(
    ...
    val layout: ArtifactPackageLayoutConfig = ArtifactPackageLayoutConfig(),
)

data class ArtifactPackageLayoutConfig(
    val aggregate: PackageLayout = PackageLayout("domain.aggregates"),
    val aggregateSchema: PackageLayout = PackageLayout("domain._share.meta"),
    val aggregateRepository: PackageLayout = PackageLayout("adapter.domain.repositories"),
    val aggregateSharedEnum: PackageLayout = PackageLayout(
        packageRoot = "domain",
        defaultPackage = "shared",
        packageSuffix = "enums",
    ),
    val aggregateEnumTranslation: PackageLayout = PackageLayout("domain.translation"),
    val aggregateUniqueQuery: PackageLayout = PackageLayout(
        packageRoot = "application.queries",
        packageSuffix = "unique",
    ),
    val aggregateUniqueQueryHandler: PackageLayout = PackageLayout(
        packageRoot = "adapter.queries",
        packageSuffix = "unique",
    ),
    val aggregateUniqueValidator: PackageLayout = PackageLayout(
        packageRoot = "application.validators",
        packageSuffix = "unique",
    ),
    val flow: OutputRootLayout = OutputRootLayout("flows"),
    val drawingBoard: OutputRootLayout = OutputRootLayout("design"),
    val designCommand: PackageLayout = PackageLayout("application.commands"),
    val designQuery: PackageLayout = PackageLayout("application.queries"),
    val designClient: PackageLayout = PackageLayout("application.distributed.clients"),
    val designQueryHandler: PackageLayout = PackageLayout("adapter.queries"),
    val designClientHandler: PackageLayout = PackageLayout("adapter.application.distributed.clients"),
    val designValidator: PackageLayout = PackageLayout("application.validators"),
    val designApiPayload: PackageLayout = PackageLayout("adapter.portal.api.payload"),
    val designDomainEvent: PackageLayout = PackageLayout(
        packageRoot = "domain.aggregates",
        packageSuffix = "events",
    ),
    val designDomainEventHandler: PackageLayout = PackageLayout(
        packageRoot = "application",
        packageSuffix = "events",
    ),
)

data class PackageLayout(
    val packageRoot: String,
    val packageSuffix: String = "",
    val defaultPackage: String = "",
)

data class OutputRootLayout(
    val outputRoot: String,
)
```

This is illustrative. The exact Gradle extension classes will use Gradle `Property<String>`, but the pipeline API should receive immutable values.

## Validation Rules

Layout validation must fail fast before planning.

Rules:

- package root must be blank or a valid relative Kotlin package fragment
- package suffix must be blank or a valid relative Kotlin package fragment
- default package must be blank or a valid relative Kotlin package fragment
- output root must be a non-blank relative filesystem path
- package fragments must not start or end with `.`
- package fragments must not contain `..`
- package fragments must not contain path separators
- package fragments must not contain wildcard or expression syntax
- package fragments must not include `basePackage`
- package fragments must not escape module ownership
- output roots must not be absolute
- output roots must not contain `..`
- output roots must normalize to slash-separated relative paths
- output roots must not contain wildcard or expression syntax

Blank `packageRoot` is allowed only if it produces a valid package with `basePackage`.

For normal cap4k projects, the recommended configuration should keep non-blank package roots.

## Placement Ownership Rules

Module ownership remains framework-defined.

First-version ownership:

```text
aggregate entity        -> domain module
aggregate schema        -> domain module
aggregate repository    -> adapter module
aggregate factory       -> domain module
aggregate specification -> domain module
aggregate wrapper       -> domain module
aggregate local enum    -> domain module
aggregate shared enum   -> domain module
aggregate enum translation -> adapter module
aggregate unique query  -> application module
aggregate unique query handler -> adapter module
aggregate unique validator -> application module
design command          -> application module
design query            -> application module
design client           -> application module
design query handler    -> adapter module
design client handler   -> adapter module
design validator        -> application module
design api payload      -> adapter module
design domain event     -> domain module
design event handler    -> application module
flow                    -> project root
drawing board           -> project root
```

The DSL controls package root inside that module. It does not control the module.

This prevents accidental dependency-direction breakage.

For project resource artifacts, the DSL controls the output root inside the project. It does not control individual file names.

## Migration Strategy

This slice does not need long-term compatibility aliases because the new pipeline has not become a stable external contract.

The implementation should still be incremental:

1. Add config model and Gradle DSL defaults.
2. Add validation.
3. Add resolver.
4. Route canonical assembly through resolver wherever it creates generated package names.
5. Route aggregate planners through resolver-derived package names and path helpers.
6. Route design planners and render-model imports through resolver.
7. Route flow and drawing-board planners through resolver-derived output roots.
8. Move generator-specific `outputDir` into layout so analysis outputs do not keep a second location configuration path.
9. Update tests and README.
10. Regenerate `only-danmaku-next` and verify compile.

During implementation, temporary adapters are acceptable on the feature branch if needed to keep tests running.

They must not remain as public compatibility shims on `master`.

## Expected Default Behavior Changes

Most defaults should preserve current behavior.

The intentional default behavior change is:

```text
designDomainEvent
before: basePackage.domain.<designPackage>.events
after:  basePackage.domain.aggregates.<designPackage>.events
```

This aligns domain events with the same aggregate package convention used by aggregate entity artifacts.

Handler imports must change accordingly.

Analysis artifact defaults stay behaviorally the same, but their configuration owner changes:

```text
flow
before config owner: generators.flow.outputDir
after config owner:  layout.flow.outputRoot
default output:      flows

drawingBoard
before config owner: generators.drawingBoard.outputDir
after config owner:  layout.drawingBoard.outputRoot
default output:      design
```

## Test Requirements

### Unit Tests

Add resolver tests for default package resolution:

- aggregate entity package
- aggregate schema package
- aggregate schema base package
- aggregate repository package
- aggregate shared enum package
- aggregate enum translation package
- aggregate unique query package
- aggregate unique query handler package
- aggregate unique validator package
- entity-attached factory package
- entity-attached specification package
- design command package
- design query package
- design domain event package
- design domain event handler package
- flow output root
- drawing-board output root

Add custom layout tests:

- custom `aggregate.packageRoot`
- custom `aggregateSchema.packageRoot`
- custom `aggregateRepository.packageRoot`
- custom `aggregateSharedEnum.packageRoot`
- custom `aggregateSharedEnum.defaultPackage`
- custom `aggregateSharedEnum.packageSuffix`
- custom `aggregateEnumTranslation.packageRoot`
- custom `aggregateUniqueQuery.packageRoot`
- custom `designCommand.packageRoot`
- custom `designDomainEvent.packageRoot`
- custom `designDomainEvent.packageSuffix`
- custom `flow.outputRoot`
- custom `drawingBoard.outputRoot`

Add validation tests:

- rejects package roots containing `/`
- rejects package roots containing `\`
- rejects package roots containing `..`
- rejects package roots starting with `.`
- rejects package roots ending with `.`
- rejects package roots containing wildcard syntax
- rejects package roots that include `basePackage`
- rejects blank output roots
- rejects absolute output roots
- rejects output roots containing `..`
- normalizes output roots to slash-separated relative paths

### Planner Tests

Aggregate planner tests must assert both context package and output path for:

- entity
- schema
- schema base
- repository
- factory
- specification
- aggregate wrapper
- unique query
- unique query handler
- unique validator
- shared enum
- local enum
- enum translation

Design planner tests must assert both context package and output path for:

- command
- query
- client
- query handler
- client handler
- validator
- api payload
- domain event
- domain event handler

Domain event handler tests must also assert the imported domain event FQN.

Analysis planner tests must assert output path for:

- flow entry JSON
- flow entry Mermaid
- flow index JSON
- drawing-board JSON documents
- custom flow output root
- custom drawing-board output root

### Functional Tests

Functional tests should include one custom-layout project where at least these roots are overridden:

```kotlin
layout {
    aggregate {
        packageRoot.set("domain.model")
    }
    aggregateSchema {
        packageRoot.set("domain.meta")
    }
    aggregateRepository {
        packageRoot.set("adapter.persistence.repositories")
    }
    flow {
        outputRoot.set("build/cap4k/flows")
    }
    drawingBoard {
        outputRoot.set("build/cap4k/design")
    }
    designDomainEvent {
        packageRoot.set("domain.model")
        packageSuffix.set("events")
    }
}
```

The test should run `cap4kPlan`, `cap4kGenerate`, and Kotlin compilation where feasible.

It must verify that:

- generated files land under the resolved package path
- generated Kotlin package declarations match the output path
- imports match the moved packages
- `cap4kPlan` reports the same final output paths
- analysis generated files land under the configured output roots

### Real Project Verification

`only-danmaku-next` must be regenerated after implementation.

Expected domain event file:

```text
only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/message/events/UserMessageCreatedDomainEvent.kt
```

Expected package:

```kotlin
package edu.only4.danmaku.domain.aggregates.message.events
```

The old wrong file path must not remain after regeneration:

```text
only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/message/events/UserMessageCreatedDomainEvent.kt
```

## README Updates

Update the Gradle plugin README to explain:

- placement is controlled by `layout`, not templates
- `design.json` `package` is a semantic subpackage
- DB table segment is generated from table name and is not per-table configurable
- flow and drawing-board output roots are controlled by `layout`, not generator `outputDir`
- module ownership is fixed in the first version
- `cap4kPlan` is the source for reviewing final output paths

The README should show default-equivalent layout values so users can understand the default convention.

## Acceptance Criteria

This slice is complete when:

- all Kotlin source package-root defaults are visible in layout config
- all project resource output-root defaults are visible in layout config
- no planner owns hidden package-root or output-root defaults for covered families
- generated Kotlin package names are produced through the resolver regardless of source
- generated project resource output roots are produced through the resolver regardless of source
- output paths and Kotlin package declarations are consistent
- project resource output paths are resolved through the same layout contract
- imports are derived from the same resolved packages
- `designDomainEvent` defaults to `domain.aggregates.<package>.events`
- tests cover default and custom package roots
- `only-danmaku-next` generation and build succeed

## Explicit Anti-Drift Rules

Do not weaken this design during implementation by adding:

- per-table placement
- per-entity placement
- raw output path hooks
- template-controlled output paths
- module override in layout
- fallback string concatenation for covered package roots in individual planners
- generator-specific output directory defaults outside layout
- separate import package logic that bypasses the resolver

If a future artifact family is added later, it must either join the resolver contract immediately or be explicitly documented as out of scope. Do not add more local hardcoded package roots while implementing this feature.
