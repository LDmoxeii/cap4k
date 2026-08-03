# Design Round-Trip Contract

## Purpose

Cap4k MUST preserve one normalized tactical design across explicit Design JSON authoring, framework-owned generated Kotlin skeletons, real compiler Analyzer recovery, Drawing Board export, and explicit regeneration from Drawing Board files.

The contract is semantic rather than byte-for-byte. Physical JSON organization may normalize, but framework-owned tactical declarations and runtime semantics may not be lost, invented, or require manual structural repair.

## Supported design blocks

The normal Design JSON language supports exactly these tags:

- `command`
- `query`
- `capability`
- `api_payload`
- `domain_event`
- `integration_event`
- `domain_service`

Unknown tags and removed public fields MUST fail fast. Compatibility aliases and legacy dialects MUST NOT be retained.

`fields` expresses the primary payload for every payload-bearing tag. `resultFields` is allowed only for `command`, `query`, `capability`, and `api_payload`. `domain_service` is a metadata-only anchor and MUST reject non-empty `fields` or `resultFields`; business operations remain handwritten and Analyzer MUST NOT infer them from method bodies.

## Artifact selection

An omitted `artifacts` property expands to the current documented defaults. A present property is complete and authoritative.

Every accepted selection MUST:

- be non-empty;
- contain the tag's primary structural carrier;
- contain only families compatible with that tag;
- contain no duplicate or contradictory family/variant pair.

The compatible primary/secondary families are:

| Tag | Primary | Optional secondary |
| --- | --- | --- |
| `command` | `command` | none |
| `query` | `query` | `query-handler` |
| `capability` | `capability` | `capability-handler` |
| `api_payload` | `api-payload` | none |
| `domain_event` | `domain-event` | `domain-subscriber` |
| `integration_event` | `integration-event` | `integration-subscriber` for inbound only |
| `domain_service` | `domain-service` | none |

`query` and `api-payload` primary artifacts optionally use variant `page`. `integration-event` MUST use `inbound` or `outbound`. Other family variants are invalid. A handler/subscriber without its primary carrier is invalid. `integration-subscriber` with outbound is invalid and MUST NOT auto-add or auto-convert an inbound carrier.

## Framework-derived page structure

The `page` variant implies runtime `PageRequest` structure:

- `pageNum: Int = 1`
- `pageSize: Int = 10`

These are framework-owned derived properties, not authoring `fields`. A page block explicitly declaring either name MUST fail. Non-page blocks may use either name as an ordinary field.

Analyzer may exclude these properties only after proving that the analyzed primary carrier is the page variant, implements the PageRequest contract, and has the required types/defaults. A mismatch MUST fail instead of silently dropping fields.

## Event semantics

Domain Event and Integration Event fields and nested DTOs use the same supported stable default-expression subset as other design payloads. Generator MUST render defaults and Analyzer MUST recover their normalized semantics.

An Integration Event MUST have a non-blank `eventName`. A Domain Event with `persist: true` MUST have a non-blank `eventName`; transient Domain Events may omit it.

When a Domain Event name is present, Generator MUST emit it in runtime `@DomainEvent(value = ..., persist = ...)` as well as compile-time analysis metadata. Analyzer MUST compare metadata and runtime views and fail on a conflict. Persisted Domain Events MUST NOT produce an empty runtime event type.

Integration Event direction, event name, and subscriber semantics MUST likewise agree between metadata and runtime annotations. Analyzer MUST fail on contradictory views rather than choosing one.

The identifier `entity` is not a reserved payload field name. Canonical assembly and Analyzer MUST retain it. Reliable-event payload rejection MUST depend only on the resolved recursive semantic type graph: actual Entity/Aggregate types remain invalid at any nested container position, preserving PR #152's runtime history boundary.

## Type and default recovery

Drawing Board type expressions MUST be stable and recursively canonical:

- Design builtins and standard containers use their normalized short names.
- Nested DTOs declared by the current design block use their current-block short names.
- Strong IDs, Value Objects, enums, other project/context types, and external types use resolved canonical FQNs.
- Container element/key/value identities apply the same rule recursively.

`List<T>`, `Set<T>`, `Map<K,V>`, and `Array<T>` are supported with container and element nullability. `emptyList()`, `emptySet()`, `emptyMap()`, and `emptyArray()` are normalized supported defaults. Primitive arrays such as `IntArray` and `ByteArray` are outside this contract and MUST fail rather than degrade.

Analyzer MUST recover stable supported defaults for null, strings, primitive values, enum/object constants, and supported empty containers. Unsupported or unstable initializer expressions MUST fail with context rather than be silently omitted when they belong to framework-owned payload structure.

## Ordering and normalization

Declaration order is tactical semantics for:

- `fields`;
- `resultFields`;
- every nested DTO's constructor fields.

Analyzer and Drawing Board MUST preserve this order. Artifact, file, and top-level entry order may be normalized for deterministic output.

Round-trip comparison may ignore:

- JSON formatting;
- output file names, count, and partitioning;
- file and top-level entry order;
- artifact order;
- omitted optional empty arrays;
- omitted values versus the same effective default;
- type spelling that resolves to the same canonical identity.

It MUST compare tag, package, name, description, aggregate ownership, effective artifact family/variant, ordered payload/result/nested fields, resolved type/nullability/default semantics, page semantics, event direction, persist/eventName, and runtime annotations/interfaces.

## Analysis metadata and completeness

`DesignBlockMetadata` from the dedicated `cap4k-analysis-metadata` compile-only module is the lossless authoring carrier. It has BINARY retention and no runtime meaning. Default templates emit it; a custom template may omit it only by intentionally opting the symbol out of Drawing Board and metadata-dependent analysis.

When Drawing Board is requested, completeness MUST be evaluated for each configured analysis input directory. A directory with complete design elements MUST NOT mask candidate symbols or missing-metadata evidence in another directory. Missing metadata MUST fail the combined request before planning/rendering and identify the affected symbols, requested capability, required annotation, and restoration action.

Analyzer output is observation evidence. It MUST NOT become an automatically registered Generator source. Drawing Board files are ordinary Design JSON only when a human or Agent explicitly registers them.

## Drawing Board compatibility

Every emitted Drawing Board block MUST be directly accepted by the Design JSON source without an additional normalization exporter or dedicated recovery input API.

Drawing Board MUST omit fields that are not legal for the emitted tag, while preserving all legal tactical semantics. It may omit `artifacts` only when the recovered set equals the tag's effective defaults; variants, primary-only selections, and non-default secondary selections require an explicit complete artifact list.

Cross-context reuse may explicitly copy an outbound published-language event and change its variant to inbound. That edit is a new bounded-context decision and MUST NOT occur automatically.

## Generated skeleton completeness

Every accepted design declaration MUST generate a compile-valid, runtime-contract-complete framework-owned skeleton. Required fields, nested types, annotations, interfaces, artifact carriers, and structural wiring MUST be generated. A human or Agent remains responsible only for business policy and bodies such as handlers, subscribers, Domain Service algorithms, repositories, transactions, and compensation.

Changing handwritten method bodies, injected dependencies, or repository calls without changing framework-owned structure MUST NOT change recovered Drawing Board semantics.

## Verification gate

One dedicated real functional gate MUST use two clean temporary project copies:

1. Project A reads the original rich seven-tag Design JSON, builds canonical projection `C0`, generates all selected artifacts, compiles every generated module, and runs the real `Cap4kCodeAnalysisCompilerRegistrar` on generated sources in domain/application/adapter dependency order.
2. Project A merges the real per-module analysis outputs and generates Drawing Board files without any hand-written `design-elements.json` fixture.
3. Project B disables/removes its original Design JSON and registers only Project A's Drawing Board files as ordinary explicit Design JSON inputs.
4. Project B builds canonical projection `C1`, asserts normalized `C0 == C1`, regenerates all artifacts, and compiles every generated module.
5. The gate compares both generations' framework-owned skeleton structure and runtime annotation/interface semantics so projection comparison omissions cannot create a false positive.

The rich fixture MUST cover all seven tags, meaningful primary/secondary and page/event variants, Strong IDs, enums, Value Objects, external canonical FQNs, nested List/Set/Map/Array/nullability/defaults, nested DTO order, a legal `entity` field, and persisted/transient/marker event forms.

Focused module tests MUST cover invalid artifacts, Domain Service payloads, page collisions/mismatches, missing event names, runtime/metadata conflicts, recursive semantic Entity payloads, primitive arrays, order preservation, incomplete per-directory analysis, and business-body invariance.

The functional fixture may use a temporary-project-unique H2 in-memory URL without `DB_CLOSE_DELAY=-1`. Production database source connection lifecycle MUST NOT change as part of test isolation.

## Compatibility

This is a breaking current-contract repair. No alias, fallback dialect, silent semantic loss, deprecation path, or compatibility adapter is required.
