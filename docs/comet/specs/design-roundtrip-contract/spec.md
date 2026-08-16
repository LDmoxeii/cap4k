# Design Round-Trip Contract

## Purpose

Cap4k MUST preserve one normalized tactical design across explicit Design JSON authoring, framework-owned generated Kotlin skeletons, real compiler Analyzer recovery, Drawing Board export, and explicit regeneration from Drawing Board files.

The contract is semantic rather than byte-for-byte. Physical JSON organization may normalize, but framework-owned tactical declarations and runtime semantics may not be lost, invented, normalized differently from runtime, or require manual structural repair.

## Supported design blocks

The normal Design JSON language supports exactly these tags:

- `command`
- `query`
- `capability`
- `endpoint`
- `domain_event`
- `integration_event`
- `domain_service`

Unknown tags and removed public fields MUST fail fast. Compatibility aliases and legacy dialects MUST NOT be retained.

The removed `api_payload` tag, `api-payload` artifact family, `page` variant under that family, and all spelling aliases MUST be rejected as unsupported. Adapter-private DTOs are ordinary implementation types and MUST NOT be recovered as tactical design blocks.

`fields` expresses the primary payload for every payload-bearing tag. `resultFields` is allowed only for `command`, `query`, `capability`, and `endpoint`. `endpoint` additionally requires a non-blank `operationName`; `operationName` is invalid for every other tag. `domain_service` is a metadata-only anchor and MUST reject non-empty `fields` or `resultFields`; business operations remain handwritten and Analyzer MUST NOT infer them from method bodies.

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
| `endpoint` | `endpoint` | none |
| `domain_event` | `domain-event` | `domain-subscriber` |
| `integration_event` | `integration-event` | `integration-subscriber` for inbound only |
| `domain_service` | `domain-service` | none |

The `query` primary artifact optionally uses variant `page`. `endpoint` has no variant. `integration-event` MUST use `inbound` or `outbound`. Other family variants are invalid. A handler/subscriber without its primary carrier is invalid. `integration-subscriber` with outbound is invalid and MUST NOT auto-add or auto-convert an inbound carrier.

## Framework-derived page structure

The `page` variant implies runtime `PageRequest` structure:

- `pageNum: Int = 1`
- `pageSize: Int = 10`

These are framework-owned derived properties, not authoring `fields`. A page block declaring a flat field path whose root segment is `pageNum` or `pageSize` MUST fail. Root comparison removes a trailing `[]` from the first segment before the first `.`; therefore `pageNum.value` and `pageSize[].value` collide, while `filter.pageNum` and `filters[].pageSize` do not. Non-page blocks may use either root name as an ordinary field.

Analyzer may exclude these properties only after proving that the analyzed primary carrier is the page variant, implements the PageRequest contract, and has the required types/defaults. A mismatch MUST fail instead of silently dropping fields.

## Endpoint semantics

An `endpoint` block represents exactly one published Actor operation. `endpoint` is the tactical building block name; Actor remains the Analyzer/Flow trigger-source interpretation and is not encoded into the tag name. `operationName` is the required stable logical identity and is distinct from its Kotlin package/type identity. The source and canonical assembler MUST reject blank or duplicate operation names and duplicate canonical type identities.

`fields` defines the ordered Request shape and `resultFields` defines the ordered Response shape. Both use dedicated Endpoint semantic roles. Endpoint MUST NOT be inferred from Command, Query or Capability declarations, and its Request/Response MUST NOT implement those internal application markers.

The default generated carrier is one Kotlin operation object. Its nested Request implements the lightweight `EndpointRequest<Response>` marker; the object owns the nested Response and exposes the stable operation identity as framework-owned constant metadata. Handler implementation and dispatch belong to the Endpoint Mediator family, not the published contract artifact. The carrier MUST contain no HTTP/RPC route, provider, client, retry, timeout, discovery, Spring or persistence behavior.

Endpoint declaration and compile-time Design metadata are Design Projection evidence only. They MUST NOT create Analyzer Graph Actor nodes, causal relationships or Flow roots without a real transport binding detector.
## Event semantics

Domain Event and Integration Event fields and nested DTOs use the same supported stable default-expression subset as other design payloads. Generator MUST render defaults and Analyzer MUST recover their normalized semantics.

An Integration Event MUST have a non-blank `eventName`. A Domain Event with `persist: true` MUST have a non-blank `eventName`; transient Domain Events may omit it.

When a Domain Event name is present, Generator MUST emit it in runtime `@DomainEvent(value = ..., persist = ...)` as well as compile-time analysis metadata. Analyzer MUST compare the canonical metadata name with the unmodified runtime annotation literal and fail unless both are exactly equal. It MUST NOT trim a runtime literal or fill missing metadata from runtime. Only a transient Domain Event with both names absent may use an empty name. Persisted Domain Events MUST NOT produce an empty runtime event type.

Integration Event `eventName` MUST agree between metadata and the unmodified runtime `@IntegrationEvent.value` literal. Direction remains explicit Design/Analyzer metadata only: the runtime annotation does not encode inbound/outbound direction, and Analyzer MUST NOT infer or contradict direction from runtime annotation fields.

The identifier `entity` is not a reserved payload field name. Canonical assembly and Analyzer MUST retain it. Reliable-event payload rejection MUST depend only on the resolved recursive semantic type graph: actual Entity/Aggregate types remain invalid at any nested container position, preserving PR #152's runtime history boundary.

## Type and default recovery

Drawing Board type expressions MUST be stable and recursively canonical:

- Design builtins and standard containers use their normalized short names.
- Nested DTOs declared by the current design block use their current-block short names.
- Strong IDs, Value Objects, enums, other project/context types, and external types use resolved canonical FQNs.
- Container element/key/value identities apply the same rule recursively.

`List<T>`, `Set<T>`, `Map<K,V>`, and `Array<T>` are supported with container and element nullability. `emptyList()`, `emptySet()`, `emptyMap()`, and `emptyArray()` are normalized supported defaults.

Kotlin primitive arrays are outside this contract and MUST fail after the final canonical identity is resolved, regardless of whether the input used a direct FQN, alias, short-name evidence, or recursive container position. The rejected FQNs are `kotlin.BooleanArray`, `kotlin.ByteArray`, `kotlin.CharArray`, `kotlin.DoubleArray`, `kotlin.FloatArray`, `kotlin.IntArray`, `kotlin.LongArray`, `kotlin.ShortArray`, `kotlin.UByteArray`, `kotlin.UIntArray`, `kotlin.ULongArray`, and `kotlin.UShortArray`. A different business type whose simple name resembles a primitive array is not rejected.

Analyzer MUST recover stable supported defaults for null, strings, primitive values, enum/object constants, and supported empty containers. Unsupported or unstable initializer expressions MUST fail with context rather than be silently omitted when they belong to framework-owned payload structure.

Kotlin string literal recovery MUST use Kotlin-supported escapes. U+000C form-feed is recovered as `\\u000c`, not `\\f`, and the recovered Drawing Board value MUST compile through the Design JSON default compiler and second-generation Kotlin compiler.

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

It MUST compare tag, package, name, description, aggregate ownership, effective artifact family/variant, ordered payload/result/nested fields, resolved type/nullability/default semantics, page semantics, Endpoint operationName/EndpointRequest semantics, event direction, persist/eventName, and runtime annotations/interfaces.

## Analysis metadata and completeness

`DesignBlockMetadata` from the dedicated `cap4k-analysis-metadata` compile-only module is the lossless authoring carrier. It has BINARY retention and no runtime meaning. Default templates emit it; a custom template may omit it only by intentionally opting the symbol out of Drawing Board and metadata-dependent analysis.

When Drawing Board is requested, completeness MUST be evaluated for each configured analysis input directory. A directory with complete design elements MUST NOT mask candidate symbols or missing-metadata evidence in another directory. Missing metadata MUST fail the combined request before planning/rendering and identify the affected symbols, requested capability, required annotation, and restoration action.

Analyzer output is observation evidence. It MUST NOT become an automatically registered Generator source. Drawing Board files are ordinary Design JSON only when a human or Agent explicitly registers them.

## Drawing Board compatibility

Every emitted Drawing Board block MUST be directly accepted by the Design JSON source without an additional normalization exporter or dedicated recovery input API.

Drawing Board MUST omit fields that are not legal for the emitted tag, while preserving all legal tactical semantics. It may omit `artifacts` only when the recovered set equals the tag's effective defaults; variants, explicit primary-only selections that differ from defaults, and non-default secondary selections require an explicit complete artifact list.

Cross-context reuse may explicitly copy an outbound published-language event and change its variant to inbound. That edit is a new bounded-context decision and MUST NOT occur automatically.

## Generated skeleton completeness

Every accepted design declaration MUST generate a compile-valid, runtime-contract-complete framework-owned skeleton. Required fields, nested types, annotations, interfaces, artifact carriers, and structural wiring MUST be generated. A human or Agent remains responsible only for business policy and bodies such as handlers, subscribers, Domain Service algorithms, repositories, transactions, and compensation.

Changing handwritten method bodies, injected dependencies, or repository calls without changing framework-owned structure MUST NOT change recovered Drawing Board semantics. Focused evidence MUST compare recovered design blocks from structurally equal carriers with different business implementations.

## Verification gate

One dedicated real functional gate MUST use two clean temporary project copies:

1. Immediately after fixture copy and before any Gradle, Generator, compiler, or Analyzer operation, Project A freezes the original Design JSON bytes/hash and builds canonical projection `C0`.
2. Project A generates all selected artifacts, compiles every generated module, and runs the real `Cap4kCodeAnalysisCompilerRegistrar` on generated sources in domain/application/adapter dependency order.
3. Project A merges the real per-module analysis outputs, generates Drawing Board files without any hand-written `design-elements.json` fixture, and proves that the original Design JSON bytes/hash are unchanged.
4. Project B disables/removes its original Design JSON and registers only Project A's Drawing Board files as ordinary explicit Design JSON inputs.
5. Project B builds canonical projection `C1`, asserts normalized `C0 == C1`, regenerates all artifacts, and compiles every generated module.
6. The gate compares both generations' framework-owned skeleton structure and runtime annotation/interface semantics so projection comparison omissions cannot create a false positive.

The rich fixture MUST cover all seven tags and explicitly assert meaningful shape coverage: ordinary and page Query; Endpoint operationName, Request/Response and EndpointRequest marker semantics; optional-secondary selected and explicit non-default primary-only forms; inbound and outbound Integration Events; persisted, transient-payload, and marker-without-fields Domain Events; Strong IDs; enums; Value Objects; external canonical FQNs; nested List/Set/Map/Array/nullability/defaults including U+000C; nested DTO order; and a legal `entity` field.

Focused module tests MUST cover invalid artifacts, Domain Service payloads, page root collisions and non-root counterexamples, page mismatches, missing event names, exact runtime/metadata conflicts, subscriber direction literals, recursive semantic Entity payloads, final-identity primitive arrays, order preservation, incomplete per-directory analysis, U+000C recovery, and business-body invariance.

The functional fixture may use a temporary-project-unique H2 in-memory URL without `DB_CLOSE_DELAY=-1`. Production database source connection lifecycle MUST NOT change as part of test isolation.

## Compatibility

This is a breaking current-contract repair. No alias, fallback dialect, silent semantic loss, deprecation path, or compatibility adapter is required.
