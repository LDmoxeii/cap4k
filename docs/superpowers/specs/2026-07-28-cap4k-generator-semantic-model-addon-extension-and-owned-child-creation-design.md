# cap4k Issue #115 Semantic Value and Owned-Child Creation Design

**Date:** 2026-07-28
**Status:** Approved replacement design
**Repository baseline:** `master@d976c409`
**Scope:** GitHub issue #115 only

## Replacement Notice

This document replaces the earlier discussion draft that combined three different subjects:

- owned-child creation through aggregate Factory;
- a general external source/compiler/generator addon architecture;
- migration of Unique artifacts into an addon.

Only the first subject is part of issue #115. The addon/provider architecture and a possible Unique addon are deferred to independent changes. Their old decisions, open questions, and proposed `CompilationResult`/provider-registration architecture are not requirements for this change.

## Problem

The current aggregate Factory payload is derived only from the aggregate root scalar creation surface. An aggregate such as `BookingDemand` therefore cannot express creation of owned `DemandCargoLine` children without hand-editing the checked-in Factory.

The missing capability is a first-class aggregate creation graph. It is not a reason to expose owned-child persistence IDs, accept child Entity instances, or add application-side counters.

At the same time, the generator has several duplicated and weak structured-value representations. Commands, queries, clients, events, Value Objects, and Factory payloads repeatedly carry raw type strings and generator-local type resolution. This makes a reusable child creation value difficult to express safely.

Issue #115 therefore introduces one canonical structured-value/type system and uses it to model owned-child creation inputs. The semantic roles remain distinct.

## Goals

- Generate reusable immutable creation values for all reachable owned Entities.
- Add owned `ONE` and `MANY` creation fields to the existing root Factory payload.
- Construct and attach the complete owned graph inside the root Factory.
- Keep child IDs, parent references, ORM relations, and managed fields out of creation inputs.
- Preserve the existing application-side `uuid7`/`snowflake` and database-identity lifecycle.
- Make Value Object semantics independent of persistence.
- Resolve structured field types exactly once before generator planning.
- Preserve the current generated artifact families and their user-facing hand feel.

## Non-goals

- No external `SourceProvider`, compiler, `GeneratorProvider`, or addon registration redesign.
- No addon model catalog, provider dependency graph, or new addon template contract.
- No Unique addon and no restoration of removed Unique artifact families.
- No top-level aggregate-root `<RootName>Creation` type.
- No public or independently registered child Factory.
- No child Entity input, child ID input, parent-FK input, or independent child Unit of Work entry.
- No relational, embedded, or flattened queryable Value Object persistence.
- No compatibility layer for old `storage`, field-level `nullable`, or implicit JSON persistence.
- No automatic synchronization, merge region, patching, or freshness promise for an existing checked-in Factory or Creation file.
- No new Saga response/result-field surface.

## Artifact Ownership

### Checked-in source

The following artifacts are checked-in and always use `SKIP`:

- `<RootName>Factory`;
- top-level `<OwnedEntityName>Creation`;
- the Value Object body;
- existing checked-in command/query/client/event/behavior skeletons.

For checked-in output, the framework guarantees only first materialization. Once the file exists, it belongs to the project and is not automatically refreshed.

Deleting and regenerating a file, reconstructing it with Git, or applying a patch is a user-owned workflow. The framework does not promise that such a workflow is lossless or current.

There is intentionally no checked-in conflict strategy other than `SKIP`. If the framework fully owns repeated regeneration, the artifact belongs in generated source instead.

### Generated source

Build-owned projections use generated source and are rebuilt with overwrite semantics. In this change, the new example is `<ValueObjectName>JsonAttributeConverter`.

Controlled Cap4k generated roots are cleaned before complete generation so removing a projection also removes its stale generated artifact. Cleanup is limited to the canonical module build path `build/generated/cap4k/main/kotlin`.

## Shared Semantic Value Model

Structured building blocks share one canonical definition made from:

- a stable `CanonicalTypeIdentity`;
- a distinct semantic role;
- resolved fields;
- nested value definitions;
- an optional special envelope;
- explicit, validated defaults.

Roles include Command request/response, Query request/response, Client request/response, API payload request/response, Domain Event, Integration Event, Saga request/response where already present, Value Object, owned-Entity Creation, and root Factory payload.

Sharing a definition does not reclassify every structure as a Domain Value Object. Intent, fact, routing, persistence, and artifact behavior remain properties of the owning role.

DB/Entity `FieldModel` remains separate because it describes physical columns, ID policy, managed fields, enum evidence, and JPA concerns.

## Semantic Type Contract

Source manifests use Kotlin-style type expressions. Nullability is carried by `?` at each node:

```text
String
Money?
List<Money?>
Map<String, List<Money?>?>?
com.acme.shared.Money
```

The first type algebra is closed:

- supported built-ins;
- resolved named symbols;
- `List<T>`;
- `Set<T>`;
- `Map<K, V>`;
- recursive nullability.

The following are unsupported and fail before rendering:

- mutable collections;
- `Collection`, `Iterable`, and `Sequence`;
- arrays;
- `Pair` and `Triple`;
- arbitrary generic constructors;
- function types;
- variance and star projections.

Named short-type resolution remains conservative. Canonical identity and explicit FQN are authoritative. Generators receive resolved type trees and may only choose imports versus explicit FQNs; they must not parse type expressions or guess symbols.

### Page envelope exception

Existing `PageData<Item>` behavior is preserved only as a query/API response `Page` envelope. It remains outside the general generic algebra and is valid only in the established root `page` response shape.

## Default Contract

Defaults are emitted only when explicitly supplied and safely projectable to Kotlin.

Supported initial forms include:

- `null` only for nullable types;
- safe built-in literals;
- explicit empty immutable collection expressions matching the field type;
- named constants belonging to the resolved named type.

Nullability alone never implies `= null`. A collection type alone never implies an empty default.

Owned-relation creation fields are the deliberate exception:

- owned `ONE`: nullable child Creation with `= null`;
- owned `MANY`: `List<ChildCreation> = emptyList()`.

Unsupported expressions fail with field-path evidence. Raw SQL default expressions are not copied into Kotlin source.

## Value Object and Persistence

A Value Object is a semantic immutable value. Persistence is an optional projection rather than its definition.

Manifest shape:

```json
{
  "name": "Money",
  "package": "com.acme.shared.values",
  "fields": [
    { "name": "amount", "type": "BigDecimal" },
    { "name": "currency", "type": "Currency" }
  ],
  "persistence": { "kind": "json" }
}
```

Rules:

- omitted `persistence` means a pure non-persistent value;
- JSON persistence is explicit as `{ "kind": "json" }`;
- old `storage` is rejected;
- field-level `nullable` is rejected;
- omitted persistence never falls back to JSON;
- there is no compatibility alias because there are no external users to preserve.

The checked-in Value Object body contains only the immutable value and project-owned domain behavior. It contains no Jackson or JPA code.

For JSON persistence the generator emits build-owned `<ValueObjectName>JsonAttributeConverter` in the same package. JPA planning references that converter by explicit FQN. Binding a pure Value Object to a database column without a persistence projection fails before rendering.

Queryable nested/relational Value Object persistence is deferred. JSON persistence preserves whole-attribute query behavior only; it does not make nested members portable Criteria paths.

## Canonical Aggregate Creation Graph

The assembler builds one creation graph per aggregate root after these facts are resolved:

- aggregate membership and Entity identity;
- owned relations and cardinality;
- per-Entity creation write surfaces;
- ID and managed-field policy;
- attachment accessor names;
- semantic type identities and defaults.

The graph contains:

- the aggregate-root identity;
- the existing nested `<RootName>Factory.Payload` semantic definition;
- root constructor field names;
- one node for every reachable owned Entity;
- one top-level Creation definition per owned Entity;
- direct and recursive relation edges;
- the generated attachment accessor for each relation.

The graph is canonical evidence. Factory and Creation planners do not rediscover ownership or derive field policy from templates.

## Creation Value Contract

Every reachable owned Entity receives one top-level immutable type:

```kotlin
data class DemandCargoLineCreation(
    val cargoType: CargoType,
    val quantity: Int,
)
```

Its stable identity is:

```text
<owned entity package>.<OwnedEntityName>Creation
```

It is deliberately not nested under the Factory. Aggregate Behavior and other domain code can accept and reuse it without depending on a Factory-specific DTO.

Creation values are pure data. They do not contain `create()`, `toEntity()`, persistence annotations, converter logic, ID allocation, relation mutation, or child Factory behavior.

Fields are derived from the Entity's resolved creation write surface. Child IDs are always removed. Parent references, ORM relation fields, provider-assigned fields, read-only fields, system-transition fields, and other excluded write-surface fields never appear.

Creation graphs recurse through all owned descendants. A child Creation references its descendant Creation values using the same relation rules as the root payload.

## Root Factory Contract

The public entry remains:

```kotlin
class BookingDemandFactory :
    AggregateFactory<BookingDemandFactory.Payload, BookingDemand>
```

The root payload remains nested as `BookingDemandFactory.Payload`. There is no top-level `BookingDemandCreation`.

On first materialization, every owned relation is included automatically and recursively:

```kotlin
data class Payload(
    val customerId: CustomerId,
    val cargoLines: List<DemandCargoLineCreation> = emptyList(),
) : AggregatePayload<BookingDemand>
```

Owned persistence cardinality does not prove business requiredness. The initial skeleton therefore uses optional `ONE` and empty `MANY`. Because the Factory is checked-in and later skipped, project code may make the contract stricter, remove relations, normalize inputs, or add business validation without future generation silently widening it.

The root Factory exclusively owns Entity materialization and graph attachment. It creates child Entities from their Creation values and attaches them through the generated forward relation facade. Input order for `MANY` is preserved.

No child is registered independently with the Unit of Work. The returned root remains the aggregate creation and persistence entry.

## ID Lifecycle

Child IDs are not authoring inputs and are not exposed through command, event, integration, callback, idempotency, result, or follow-up lookup contracts.

The existing generated ID lifecycle remains authoritative:

- `uuid7` and `snowflake` application-side Strong IDs are allocated by generated typed accessors when the child is attached or persisted;
- database identity remains unassigned during Factory construction and is supplied by the existing JPA root-save lifecycle;
- no counter, sentinel, parent-ID copy, or ad hoc generator is introduced.

Issue #115 updates the older issue premise that database identity was the only expected child strategy. Current code supports internal-only application-side Strong IDs as well, without exposing them to callers.

## Fail-fast Rules

Assembly or planning fails deterministically before rendering for:

- missing creation graph for an aggregate root;
- unresolved owned relation owner, target, or cardinality;
- ownership cycles;
- a child with multiple owned parents;
- duplicate root graph identity;
- duplicate Entity, Creation, helper, or canonical FQN identity;
- a Creation identity that is not top-level, not in the Entity package, or not named `<EntityName>Creation`;
- a root payload identity other than `<RootFactoryPackage>.<RootName>Factory.Payload`;
- a constructor field absent from the compiled semantic definition;
- a relation field absent from its source Creation definition;
- an attachment target without a canonical child Creation node;
- an unresolved or ambiguous semantic type;
- unsupported type algebra or unsafe default;
- database binding to a Value Object without persistence projection.

Generators do not synthesize a legacy scalar-only Factory when canonical creation evidence is absent.

## Source Syntax Migration

Design JSON, Value Object manifests, IR-recovered design input, code-analysis output, drawing-board input, validation tooling, examples, and functional fixtures migrate together.

There is no parallel legacy semantic path:

- source snapshots may retain unresolved `typeExpression` only until assembly;
- canonical structured roles use `SemanticValueDefinition` and `SemanticTypeRef`;
- generator planners consume only resolved definitions;
- templates receive only render-ready fields.

Saga request fields participate in the shared semantic model where the current role exists. This change does not invent or reopen Saga result fields.

## Verification

The change is accepted only when tests prove:

- recursive closed type parsing and node-level nullability;
- conservative named-type resolution and collision diagnostics;
- safe explicit defaults and failure evidence;
- response-only `PageData<Item>` envelope behavior;
- all existing design artifact families consume resolved semantic definitions;
- pure Value Objects generate no converter;
- explicit JSON Value Objects generate a separate build-owned converter;
- pure Value Object database binding fails;
- stale generated converters are removed by controlled generated-root rebuild;
- every owned Entity receives a reusable checked-in Creation value;
- root payload keeps its nested identity;
- child IDs and managed fields are excluded;
- owned `ONE`, owned `MANY`, ordering, and recursive attachment render correctly;
- application-side and database-side child IDs preserve their current lifecycle;
- checked-in artifacts remain `SKIP` even when template conflict overrides request another strategy;
- missing/cyclic/ambiguous creation evidence fails before rendering;
- generated fixtures compile without hand-editing Factory payloads.

## Deferred Work

The following require separate issues and specs:

- external source/compiler/generator provider registration;
- addon-owned source/model catalogs;
- addon dependency/version contracts;
- Unique addon design;
- relational/embedded/flattened queryable Value Object projections;
- any managed regeneration or merge mechanism for checked-in source.

## Final Decision Summary

Issue #115 is solved with one canonical semantic value/type system, explicit optional Value Object persistence, reusable top-level owned-Entity Creation values, and the existing single checked-in root Factory.

The Factory and Creation scaffolds are first-materialization-only. The root Factory creates and attaches the owned graph while persistence identity remains internal and governed by the current ID lifecycle. General addon architecture is not a prerequisite and is not part of this change.
