# cap4k Generator Input Boundary And Advanced Skeletons Design

Date: 2026-05-25

Status: Proposed

Scope: implement #84 as a final large generator-boundary iteration across `cap4k` and `only-engine`, using the existing artifact addon SPI direction from the enum-translation migration and adding the `types` value-object manifest as an in-scope generator input.

## Backlog Source

This design covers:

- #84: generator input-boundary realignment and advanced design families.

It also absorbs the value-object generation portion of:

- #41: value-object persistence and generation contract.

It also links to:

- #88: deferred internal pipeline input naming cleanup.

#88 is intentionally out of scope for this implementation. This iteration changes the public authoring and generation contract, but does not expand into a full internal `SourceProvider` / `SourceSnapshot` naming rewrite.

## Positioning

The cap4k generator produces auditable skeletons and structural wiring, not business logic.

It standardizes artifact family, package placement, naming, runtime interfaces, ownership, conflict policy, plan visibility, and reviewable boundaries. It must not generate domain decisions, release policy logic, domain-service behavior, Saga steps, compensation decisions, retry strategy, callback-resume workflow, or cross-artifact field annotation injection.

The result should make generated code easier to audit and harder for AI or humans to drift away from cap4k conventions, while keeping all real business judgment handwritten.

## Chosen Approach

Use a direct boundary cleanup without compatibility shims, while avoiding a full internal pipeline naming rewrite.

Public language after this change:

- `db` expresses aggregate, entity, relation, persistence, unique constraint, and DB-derived type facts.
- `design` expresses cap4k core skeleton declarations.
- `types` expresses enum manifest, value-object manifest, and custom type registry inputs.
- `addons` express post-canonical external artifact contribution.
- KSP metadata and IR analysis remain compile-time observation or enhancement inputs.

This is the chosen middle path:

- stricter than a minimal hidden-compatibility patch;
- smaller than renaming all internal pipeline source abstractions in the same iteration;
- enough to complete the public contract correction and add the missing skeleton families.

## Public DSL

Design inputs are generation intent. A design entry should not require a second public generator-family switch.

Target shape:

```kotlin
cap4k {
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
        db {
            enabled.set(true)
            url.set("jdbc:...")
            username.set("...")
            password.set("...")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("demo-domain/build/generated/ksp/main/resources/metadata")
        }
        irAnalysis {
            enabled.set(true)
            inputDirs.from("demo-application/build/cap4k-code-analysis")
        }
    }

    types {
        registryFile.set("design/type-registry.json")
        enumManifest {
            files.from("design/enums.json")
        }
        valueObjectManifest {
            files.from("design/value-objects.json")
        }
    }

    generators {
        aggregate {
            enabled.set(true)
            artifacts {
                factory.set(true)
                specification.set(false)
                unique.set(true)
            }
        }
        flow {
            enabled.set(true)
        }
        drawingBoard {
            enabled.set(true)
        }
    }

    addons {
        provider("only-engine-validator") {
            option("manifestFile", "validation/validators.json")
        }
    }
}
```

Remove public design-family generator switches:

- `designCommand`
- `designQuery`
- `designQueryHandler`
- `designClient`
- `designClientHandler`
- `designValidator`
- `designApiPayload`
- `designDomainEvent`
- `designDomainEventHandler`
- `designIntegrationEvent`
- `designIntegrationEventSubscriber`

Keep explicit switches for capabilities whose input is external, broad, expensive, or opaque:

- `aggregate`
- aggregate sub-artifacts such as `factory`, `specification`, and `unique`
- `aggregateProjection`, if retained
- `flow`
- `drawingBoard`
- addon installation and provider options

The existing `sources.designJson.enabled`, `sources.db.enabled`, `sources.kspMetadata.enabled`, and `sources.irAnalysis.enabled` remain for this iteration. The deeper internal and public terminology cleanup around `source` naming is tracked by #88.

## Types Input

`types.registryFile` remains the custom type FQN and converter contract.

`sources.enumManifest` moves to `types.enumManifest`. Enum manifest is a types input contract, not a source family. It participates in enum binding and enum generation, but does not express use-case surfaces, behavior, validation, or artifact-family switches.

`types.valueObjectManifest` is part of this implementation. Value-object manifest is a structured types input contract, not a design tag and not a registry entry. It participates in type resolution, value-object class generation, nested converter generation, and aggregate field converter mapping.

Manifest-managed enums and value objects do not need to be repeated in `types.registryFile`. Duplicate simple names across enum manifest, value-object manifest, and registry entries fail fast unless a future explicit qualification rule is designed separately.

`types.registryFile` is for external handwritten types and converter policy. It must not be required for manifest-managed enum or value-object types.

Internal names such as `EnumManifestSnapshot` may remain temporarily if the public contract and behavior are correct. That cleanup is deferred to #88.

## Value-Object Manifest

Value-object manifest entries use explicit scope. File location must not define domain scope.

Example:

```json
[
  {
    "name": "Money",
    "scope": "shared",
    "package": "shared.values",
    "storage": "json",
    "fields": [
      { "name": "amount", "type": "BigDecimal" },
      { "name": "currency", "type": "String" }
    ]
  },
  {
    "name": "PublishWindow",
    "scope": "aggregate",
    "aggregate": "Content",
    "package": "content.values",
    "storage": "json",
    "fields": [
      { "name": "startAt", "type": "Instant", "nullable": true },
      { "name": "endAt", "type": "Instant", "nullable": true }
    ]
  }
]
```

Rules:

- `scope = "shared"` defines a context-shared value object.
- `scope = "aggregate"` defines a value object local to one aggregate and requires `aggregate`.
- shared value-object simple names are globally unique.
- aggregate-local value-object simple names are unique within `(aggregate, name)`.
- DB `@T=<TypeName>` resolves aggregate-local value objects first for the current aggregate, then shared value objects.
- ambiguous candidates fail fast; cap4k must not guess.
- value-object manifest entries do not require `types.registryFile` entries.
- `storage = "json"` is the first supported persistence strategy.
- table-backed value objects remain DB `@VO` table modeling and are not the default for manifest-managed value objects.

Generated value-object source is `CHECKED_IN_SOURCE`. It follows `templates.conflictPolicy`, whose default is `SKIP`, and can still be overridden by `templateConflictPolicies`. It must not be generated as build-owned `GENERATED_SOURCE` because value objects are domain type skeletons that authors may extend with factories, methods, normalization, annotations, or documentation.

Default JSON value-object output:

```kotlin
package com.acme.demo.domain.shared.values

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.math.BigDecimal

data class Money(
    val amount: BigDecimal,
    val currency: String,
) {
    @Converter(autoApply = false)
    class Converter : AttributeConverter<Money, String> {
        override fun convertToDatabaseColumn(attribute: Money?): String? {
            return attribute?.let { objectMapper.writeValueAsString(it) }
        }

        override fun convertToEntityAttribute(dbData: String?): Money? {
            return dbData
                ?.takeIf { it.isNotBlank() }
                ?.let { objectMapper.readValue<Money>(it) }
        }

        companion object {
            private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()
        }
    }
}
```

Aggregate fields using manifest-managed JSON value objects receive the nested converter:

```kotlin
@Convert(converter = PublishWindow.Converter::class)
@Column(name = "publish_window")
var publishWindow: PublishWindow? = null
```

The converter FQN is the value-object FQN plus `.Converter`, matching the existing nested-converter mental model used by custom type registry entries.

## Design Input

Supported `design.json` tags become:

- `command`
- `query`
- `client`
- `api_payload`
- `domain_event`
- `integration_event`
- `domain_service`
- `saga`

Unsupported tags produce the normal unsupported-tag error. The parser should not use historical migration language in the long-term error message.

`validator` is removed from cap4k core design JSON. Validation artifacts can be generated by addons through their own manifests and provider options.

## Domain Service Skeleton

`domain_service` is a core design skeleton family.

Input uses only structural declaration fields:

- `tag`
- `package`
- `name`
- `desc`
- `aggregates`

It does not consume `requestFields` or `responseFields`.

Canonical model:

```kotlin
data class DomainServiceModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val aggregateRefs: List<AggregateRef> = emptyList(),
)
```

Generated artifact:

- checked-in source;
- domain module;
- standard domain-service package layout;
- `@DomainService` class skeleton;
- no generated methods;
- no repository access;
- no orchestration;
- no domain judgment logic.

## Saga Skeleton

`saga` is a core design skeleton family.

It generates the minimum cap4k runtime skeleton:

- `XxxSagaParam : SagaParam<XxxSagaResult>`
- `XxxSagaResult`
- `XxxSagaHandler : SagaHandler<XxxSagaParam, XxxSagaResult>`

Canonical model:

```kotlin
data class SagaModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val aggregateRefs: List<AggregateRef> = emptyList(),
    val requestFields: List<FieldModel> = emptyList(),
    val responseFields: List<FieldModel> = emptyList(),
)
```

The handler body contains only an implementation placeholder. It must not generate step declarations, compensation logic, retry/recovery policy, callback-resume orchestration, or a workflow language.

## Core Validator Removal

Remove generic design validator support from cap4k core:

- `ValidatorModel`
- `CanonicalModel.validators`
- `DesignValidatorArtifactPlanner`
- validator render models
- `design/validator.kt.peb`
- `layout.designValidator`
- `generators.designValidator`
- design validator tests and functional fixtures
- public docs and skills that list `validator` as a core design tag

Keep aggregate unique validation adapter support as part of the aggregate unique helper family:

- `aggregate.artifacts.unique`
- unique query
- unique query handler
- unique validation adapter

Public docs should describe this as an aggregate unique helper derived from DB unique constraints, not as a generic validator family.

## Addon SPI And Options

Addon installation remains explicit build-time configuration through `cap4kAddon`.

cap4k adds provider-scoped addon options:

```kotlin
cap4k {
    addons {
        provider("some-addon") {
            option("manifestFile", "...")
            option("mode", "...")
        }
    }
}
```

These options are generic. cap4k does not understand their business semantics; it only passes them to the matching provider:

```kotlin
ArtifactAddonContext(
    config = config,
    model = model,
    options = config.addons["some-addon"].options,
)
```

Rules:

- provider options do not install an addon; `cap4kAddon` still installs providers;
- configured provider options without a loaded provider fail fast;
- duplicate provider ids fail fast;
- provider exceptions include provider id context;
- addon template ids must stay under `addons/<providerId>/...`;
- addons return normal `ArtifactPlanItem`s;
- addons cannot mutate `CanonicalModel`;
- addons cannot mutate built-in plan items or built-in render contexts;
- addons cannot inject annotations or fields into built-in command/API payload artifacts;
- addon artifacts use the normal renderer, exporter, template override, and conflict policy mechanisms.

The addon SPI is a general artifact-contribution capability. It is not limited to enum translation or validator artifacts.

## only-engine Reference Addons

This implementation is cross-repository.

`cap4k` owns:

- public DSL changes;
- canonical model changes;
- core design validator removal;
- domain-service and saga skeleton generation;
- addon options contract;
- docs and skills for the cap4k side.

`only-engine` owns:

- preserving the existing enum-translation addon against the updated cap4k SPI;
- adding a validator addon as a reference implementation of provider-scoped options and external manifests.

The validator addon should live in `engine-cap4k-addon` unless implementation reveals a stronger only-engine module boundary.

Suggested provider id:

```text
only-engine-validator
```

Suggested template id:

```text
addons/only-engine-validator/validator.kt.peb
```

Suggested bundled resource:

```text
cap4k/addons/only-engine-validator/validator.kt.peb
```

The validator manifest is owned by only-engine, not cap4k design JSON. It may reuse the structural shape of the removed cap4k design validator, but it is an addon manifest:

```json
[
  {
    "package": "content.validation",
    "name": "ValidAuthor",
    "desc": "valid author",
    "message": "invalid author",
    "targets": ["FIELD", "VALUE_PARAMETER"],
    "valueType": "AuthorId",
    "parameters": [
      { "name": "allowDeleted", "type": "Boolean", "defaultValue": "false" }
    ]
  }
]
```

only-engine provider responsibilities:

- read its manifest;
- validate target/value/parameter structure;
- resolve strong IDs and type registry entries from `ArtifactAddonContext`;
- create standard addon `ArtifactPlanItem`s;
- own Jakarta Bean Validation imports and generated validator shape;
- provide clear errors for missing application module or missing manifest when required.

## Planning And Module Requirements

Internally enabled design planners must be empty-slice tolerant.

Examples:

- no `domain_service` entries means domain-service planner returns an empty list and does not require `project.domainModulePath`;
- no `saga` entries means saga planner returns an empty list and does not require `project.applicationModulePath`;
- non-empty domain-service slice without domain module path fails with a family-specific message;
- non-empty saga slice without application module path fails with a family-specific message.

This avoids replacing public generator switches with eager module requirements.

## Error Handling

Expected failure behavior:

- unsupported design tags fail with the normal unsupported-tag error;
- `types.enumManifest.files` must not be empty when configured;
- stale `sources.enumManifest` DSL is not preserved;
- addon options for an unloaded provider fail fast;
- duplicate addon provider ids fail fast;
- addon provider planning failures identify the provider;
- addon template ids outside `addons/<providerId>/...` fail fast;
- missing addon templates fail fast;
- output path conflicts continue through the standard exporter and conflict policy behavior.

## Testing

cap4k unit tests:

- Gradle config factory removes design-family generator switches from public config behavior.
- Gradle config factory maps `types.enumManifest`.
- Gradle config factory maps `types.valueObjectManifest`.
- Gradle config factory maps `addons.provider("<id>").option(...)`.
- Design JSON parser supports `domain_service` and `saga`.
- Design JSON parser rejects `validator` as unsupported.
- Value-object manifest parser validates scope, aggregate ownership, storage, fields, and duplicate names.
- Canonical assembler produces `DomainServiceModel` and `SagaModel`.
- Canonical assembler produces manifest-managed value-object models.
- Canonical assembler no longer produces `ValidatorModel`.
- Domain-service and saga planners produce correct plan items.
- Value-object planner produces checked-in source plan items with nested converter context.
- Empty design slices do not require unrelated module paths.
- DB `@T` resolution can bind aggregate fields to manifest-managed value objects without registry entries.
- Aggregate entity planning applies nested value-object converter metadata to bound value-object fields.
- Addon runner passes provider-scoped options.
- Addon runner fails when options name an unloaded provider.
- Addon template namespace validation rejects cross-provider or core template ids.

cap4k functional tests:

- design entries generate artifacts without public `designX.enabled` switches.
- `types.enumManifest` participates in DB `@T` enum binding.
- `types.valueObjectManifest` participates in DB `@T` value-object binding.
- generated value-object source is checked-in source and defaults to `SKIP`.
- aggregate fields using manifest-managed JSON value objects compile with nested converters.
- no addon means no validator addon artifact.
- installed test addon appears in `cap4kPlan`.
- addon template override works.
- `templateConflictPolicies` works for addon template ids.
- aggregate unique helper still generates unique query, handler, and unique validation adapter.

only-engine tests:

- enum-translation addon remains loadable and still generates plan items.
- validator addon reads manifest from provider options.
- validator addon resolves strong IDs and type registry entries.
- validator addon bundles templates under the cap4k addon namespace.
- composite build verifies only-engine against the local cap4k SPI.

## Documentation And Skills

Update cap4k public docs:

- generator guide;
- input sources / input contracts;
- generator DSL reference;
- addon usage docs;
- design JSON tag docs;
- enum manifest docs;
- aggregate unique helper wording.

Update cap4k skills:

- `cap4k-generation`;
- `cap4k-generated-output-review`;
- `cap4k-verification`;
- any authoring skill that lists design tags, enum manifest source ownership, validator generation, or addon behavior.

Update only-engine docs:

- `engine-cap4k-addon` README;
- validator addon usage;
- composite build verification notes.

## Non-Goals

- No backward compatibility requirement.
- Do not keep core design validator as a hidden or deprecated implementation.
- Do not introduce public source-provider SPI.
- Do not introduce canonical mutation by addons.
- Do not allow addons to mutate built-in artifact render contexts.
- Do not turn Saga generation into workflow language generation.
- Do not generate Saga steps, compensation details, retry strategy, or callback-resume logic.
- Do not generate domain-service methods or domain decisions.
- Do not move value-object manifest support into `design.json`.
- Do not complete the internal source/input naming cleanup tracked by #88.

## Acceptance Criteria

- `design.json` supports `domain_service` and `saga`.
- `design.json` no longer supports `validator`.
- Public design-family generator switches are removed.
- Design entries generate their core artifact families by presence.
- `types.enumManifest` replaces `sources.enumManifest`.
- `types.valueObjectManifest` is implemented as a first-class types input.
- `types.registryFile` remains supported.
- Manifest-managed enum and value-object types do not need registry entries.
- Duplicate type simple names across enum manifest, value-object manifest, and registry fail fast.
- JSON value objects generate checked-in `data class` skeletons with nested JPA converters.
- DB `@T` fields can resolve manifest-managed value objects and apply nested converters in aggregate entities.
- Addon provider options are configurable and provider-scoped.
- Addon options are passed to matching providers.
- Addon options for unloaded providers fail fast.
- Addon template ids are namespace-checked.
- cap4k core no longer contains generic design validator planner/template/model support.
- aggregate unique validation adapter remains available under aggregate unique helper generation.
- domain-service skeletons generate only `@DomainService` class shells.
- saga skeletons generate only minimum `SagaParam` / result / handler shells.
- only-engine enum-translation addon still works.
- only-engine validator addon demonstrates external manifest-driven validator generation.
- Public docs and skills reflect the new boundary.
- #88 remains the follow-up for internal historical naming cleanup.
