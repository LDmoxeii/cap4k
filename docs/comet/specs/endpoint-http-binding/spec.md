# Endpoint HTTP Provider Binding

## Purpose

Cap4k MUST support Spring MVC servlet applications as the first production Provider binding over an existing transport-neutral Endpoint operation. The binding MUST expose a real HTTP route while preserving the accepted Request + Handler + Response model and MUST NOT redefine the published operation contract.

## Independent operation, implementation and binding

An Endpoint operation, its local Provider implementation and its transport bindings MUST remain separate concerns:

- the dependency-leaf contract module owns stable operation identity and published Request/Response;
- one binding-neutral local `EndpointHandler<Request, Response>` owns translation to local Command or Query behavior;
- each Provider transport binding owns only its protocol entry and invokes `Mediator.endpoints`;
- one operation MAY have zero, one or multiple Provider bindings of different kinds at the same time;
- adding, removing or replacing an HTTP, RPC or other binding MUST NOT change the Endpoint contract or duplicate the local Provider Handler.

The framework MUST NOT make one annotated adapter class the universal identity of an Endpoint operation.

## Provider Handler

The Provider Handler MUST be a checked-in adapter-owned source implementing `EndpointHandler<Request, Response>`. It MUST:

- depend on the published contract and the local application Command/Query contracts;
- explicitly map published Request fields to one authored local Command or Query invocation;
- explicitly map the local result to the published Response;
- contain no Spring MVC route, HTTP method, status, header, authentication or error-mapping metadata;
- contain no inferred field-copy or name-based Command/Query selection.

The Handler MAY exist before any Provider transport binding is selected. It MAY be created manually or by an optional authoring Skill scaffold, but an existing checked-in file MUST be treated as `SKIP` and MUST NOT be overwritten, merged or patched by later framework/template changes.

## Module ownership

The capability MUST introduce two focused modules:

- `ddd-endpoint-http`: the typed Spring MVC binding descriptor, request-reader and response-policy contracts plus protocol-specific runtime implementation;
- `cap4k-ddd-endpoint-http-starter`: Spring Boot auto-configuration that collects registrations, validates the effective set and materializes WebMvc.fn routes.

The modules MAY depend on `ddd-core`, `cap4k-contract-api` and Spring MVC as required by their responsibility. The dependency-leaf contract module MUST NOT depend on either module. This capability MUST NOT be added to `ddd-core` as remote lookup, discovery or HTTP fallback behavior.

## Typed Spring MVC binding

Each selected HTTP Provider binding MUST be an adapter-owned typed code-first `EndpointMvcBinding<Request, Response>` registration made available to the Spring application as a bean. Multiple registration beans MAY be grouped in one adapter configuration class; the API MUST NOT require one Controller, class or physical file per operation.

Each immutable registration MUST explicitly preserve:

- the generated non-blank `OPERATION_NAME`;
- published Request and Response `KClass` evidence;
- an explicitly supported HTTP method normalized to uppercase;
- an absolute normalized route-template path;
- one runtime-owned request reader or preset;
- one runtime-owned response policy or preset.

A representative ordinary registration is:

```kotlin
@Bean
fun createBookingHttpBinding() = EndpointMvcBinding.json(
    operationName = CreateBookingEndpoint.OPERATION_NAME,
    requestType = CreateBookingEndpoint.Request::class,
    responseType = CreateBookingEndpoint.Response::class,
    method = HttpMethod.POST,
    path = "/api/bookings",
)
```

The exact factory names and Kotlin surface are implementation choices, but the production declaration MUST remain immutable, typed and statically recognizable. Core binding identity and method/path evidence MUST NOT be hidden in an arbitrary opaque builder callback.

## Operation and route activation validation

The effective binding set MUST be validated before route activation without introducing a runtime or generated operation catalog. For every registration, Runtime MUST validate using the supplied types and the existing generated outer-object structure that:

- `operationName` is non-blank and equals the outer operation object's public `OPERATION_NAME`;
- Request and Response belong to the same generated operation owner;
- Request implements `EndpointRequest<Response>` with a coherent Response type;
- method and route are supported and normalized;
- the operation has not already selected another HTTP binding;
- normalized method plus route does not collide with another effective registration.

A missing/mismatched operation owner or `OPERATION_NAME`, incoherent Request/Response types, malformed method/path, duplicate operation binding or route collision MUST fail before activation with deterministic actionable diagnostics. Compile-time Analyzer evidence MUST additionally validate generated Endpoint metadata/provenance; Runtime structural validation and Analyzer provenance validation are complementary and MUST NOT require Generator changes.

## Spring MVC route materialization

The starter MUST collect the typed binding registrations and use Spring MVC Functional Endpoints (`RouterFunction` / `HandlerFunction`, WebMvc.fn) to materialize real routes. For every invocation, the materialized route MUST:

- read or construct the published Endpoint Request through the selected request preset/mapper;
- invoke `Mediator.endpoints.send` or `sendAsync`;
- map the published Endpoint Response through the selected response policy;
- never call an `EndpointHandler` directly;
- never invoke local Command, Query or Capability families directly.

Binding authors MUST NOT directly implement a Controller or `HandlerFunction` for the ordinary path. WebMvc.fn types, route registration, codec integration, protocol conversion and dispatch are runtime/starter implementation details.

The capability MUST remain a thin registration API rather than a complete FastEndpoints-style `Configure + Handle` framework. It MUST NOT create a universal cross-transport base class or DSL.

## JSON codec authority

Whole-body decoding and response-body encoding MUST use the current Spring application's configured MVC `HttpMessageConverter` chain and Jackson customizations. The Endpoint HTTP capability MUST NOT use `RuntimeJson`, create a private default `ObjectMapper`, or silently bypass application modules, naming strategy, date/time handling, Strong ID codecs or other MVC JSON configuration.

`RuntimeJson` remains the existing Runtime persistence/Integration Event codec boundary and MUST NOT become the application HTTP API codec.

## Request mapping

The first version MUST provide:

1. a whole-body JSON preset that decodes directly to the published Endpoint Request; and
2. a typed special request-mapping API for explicit body, path, query and header access when the HTTP shape differs.

Path, query and header sources used by a special mapper MUST be explicitly named in authored typed code. The mapper MUST construct the published Request directly and MUST NOT assign target fields through string property names, infer field copies or dispatch Command, Query, Capability or Endpoint operations.

A special HTTP body MAY use an adapter-private handwritten intermediate type only when the actual wire shape cannot directly reuse the published Request. Such a type MUST remain an implementation detail; it MUST NOT become a Design JSON tag, canonical role, generated artifact, Analyzer node, AgentFact or competing published schema.

Protocol parsing and conversion MUST complete before construction of the published Request. Missing required values, malformed bodies and conversion failures MUST produce deterministic HTTP 400 binding failures. The framework MUST NOT rely on Spring implicit parameter-name binding or route-template coincidence.

After Request construction, contract Bean Validation MUST continue through `Mediator.endpoints` and the Endpoint Supervisor. Endpoint Request constraint failures at the HTTP boundary MUST map to HTTP 400; they MUST NOT be reclassified as local Command/Query validation.

## Response mapping and schema authority

The published Endpoint Response MUST be the actual HTTP response schema. The first version MUST provide response presets/policies without requiring `ResponseEntity`, `ServerResponse` or another HTTP type in the Endpoint Response or Provider Handler. It MUST support:

- direct whole-response encoding through the application MVC converters;
- a fixed success HTTP status, defaulting to `200`;
- `body = RESPONSE` or `body = NONE`;
- response headers whose values are fixed literals or typed direct references to Endpoint Response properties;
- an optional fixed response content type.

Typed response property references MUST support deterministic header-value conversion. The policy API MUST express a redirect as fixed `302`, empty body and `Location` sourced from a Response property without exposing Spring response objects to the Provider Handler.

The capability MUST NOT add a `DEFAULT|RAW` envelope switch or an implicit framework response wrapper. WebMvc.fn routes MUST NOT claim annotated-controller `ResponseBodyAdvice` as part of their contract. If a published API requires an envelope, that envelope MUST be modeled explicitly in the Endpoint Response. Arbitrary business dispatch, dynamic status selection and framework-native return objects are outside the first version.

## Security and exception boundary

The first version MUST NOT define binding-level authentication or authorization metadata. Materialized routes MUST participate in the application's existing Servlet filter and Spring Security chain. Public route exceptions, roles, permissions and deployment-specific security policy remain application security configuration keyed by the real route.

The Runtime MUST provide only the minimum deterministic HTTP failure mapping required by this binding:

- malformed body, missing required binding input and conversion failure -> HTTP 400;
- Endpoint Request Bean Validation failure -> HTTP 400;
- activation/configuration defects -> application startup failure before route activation.

Other Handler, domain and application exceptions MUST propagate to the application's configured Spring MVC exception resolvers. This capability MUST NOT introduce a global business exception taxonomy or application-wide error envelope.

## Multipart, binary and streaming boundary

The first version MUST NOT support multipart upload, buffered binary response, download attachment or streaming materialization. It MUST NOT permit Spring `MultipartFile`, `Resource`, `ResponseEntity`, servlet types or other transport-native types in published Endpoint Request/Response contracts.

The HTTP runtime MAY reserve request-part resolver and response-writer extension seams, but no built-in binary/content-reference lifecycle is implied. A future binary capability MUST separately define transport-neutral content/reference semantics, ownership, size limits, repeatability, temporary storage and cleanup before multipart or streaming is accepted as supported Endpoint behavior.

## Multiple bindings

One `operationName` MUST have at most one HTTP binding in the first version. HTTP binding identity MUST therefore be the referenced `operationName`; no separate `bindingId` is introduced. The same operation MAY still have bindings of different kinds, such as one HTTP binding and one future RPC binding.

Binding planning/observation keys MUST include binding kind plus operation identity so different protocol projections do not collide. Provider Handler identity MUST remain independent of the number and kind of selected bindings.

## Consumer and Capability boundary

This capability implements Provider HTTP binding only. A future Consumer transport capability MAY publish a separate client artifact derived from Provider-owned binding descriptors. That client artifact MUST depend on the published contract; the contract MUST NOT depend on Spring, Feign, RPC or the client artifact.

Consumer application code MUST NOT be required to author, inject or directly call a Feign/RPC transport interface. Consumer assembly MAY select a client artifact and transport configuration, and that artifact MUST auto-register one concrete remote `EndpointHandler<Request, Response>` for the published Request. Application code MAY then invoke the published Endpoint only through `Mediator.endpoints`.

If Feign is selected as a future backend, any annotated client interface MUST remain an internal implementation detail rather than a published contract or Consumer application API. A future client artifact MAY instead implement the remote Handler over a generic `EndpointTransportInvoker` without generating a Feign interface.

`Mediator.endpoints` MUST continue to resolve the unique applicable Handler in the current process. The remote Handler, not `ddd-core`, owns service discovery, route resolution and network invocation. Consumer assembly remains responsible for topology, authentication, timeout and retry configuration.

When a Consumer requires a local anti-corruption language, a local Capability and adapter-owned CapabilityHandler MAY map the local Capability Request/Response to the published Endpoint Request/Response and invoke `Mediator.endpoints`. The CapabilityHandler does not replace the transport proxy's EndpointHandler role.

## Analyzer evidence

Analyzer MUST observe each typed production binding registration for a selected Spring MVC route and associate it with the referenced generated Endpoint operation. It MUST separately observe the binding-neutral Provider Handler and its explicit local Command or Query invocation. Detection MUST NOT assume that binding and Handler are in the same class or file.

The typed binding registration is the concrete Actor entry identity. Runtime route materialization and Analyzer projection MUST preserve its stable operation-based identity. Contract declaration, Provider Handler existence and local `Mediator.endpoints` dispatch without a real transport binding MUST remain non-entry evidence.

For a real HTTP binding:

- operation-to-Command evidence MUST produce an Actor entry and one entry-centered default Flow when otherwise eligible;
- operation-to-Query evidence MAY remain in raw Graph but MUST NOT increase default Flow count;
- one binding MUST NOT be double-counted by the generic annotated Spring Controller detector and the Endpoint-specific detector;
- ordinary handwritten Controllers MUST retain their existing behavior.

Analyzer MUST validate generated Endpoint metadata and operation/type provenance at compile time. It MUST emit stable Endpoint HTTP binding nodes and explicit binding-to-Command / binding-to-Query relationship types rather than reusing Controller identities or guessing from method/class names.

## Generator and API Payload boundary

This capability MUST NOT add a custom manifest, Pipeline source, canonical binding family, planner, renderer, generated binding source, per-operation Controller or operation catalog. Existing generated `OPERATION_NAME`, nested Request/Response structure and compile-time metadata are sufficient; Generator, Renderer, Design JSON and Design Projection MUST remain unchanged unless Build proves a target-contract impossibility and returns to Shape.

Framework-level API Payload remains retired. `NodeType.apipayload` and the separator-free IR Drawing Board candidate `apipayload` MUST be removed as Analyzer residue. Ordinary bindings reuse published Endpoint types; exceptional transport-local DTOs remain private handwritten implementation details.

OpenAPI is neither the authoring source nor an output of this capability. A future projection MAY derive OpenAPI from the same production binding registry and generated Endpoint model, but MUST NOT create a second unmanaged schema authority.

## Capability propagation

Runtime production descriptors/facts MUST declare the Endpoint HTTP Provider capability and its implementation/starter ownership. Analyzer descriptors and Graph wire vocabulary MUST declare the typed Spring MVC Endpoint detector and relationships. Flow, AgentFacts, Public Docs and the repo-local authoring Skill MUST accurately project the shipped binding, independent Handler, exact Response schema, security/error boundary and non-goals.

Public surfaces MUST continue to state that RPC Provider, Consumer proxy, service discovery, timeout, retry, WebFlux, HTTP client generation, multipart/binary/streaming and universal transport DSL behavior are not provided by this capability.

## Verification

Verification MUST include:

- focused registration/runtime tests for generated-structure coherence, no-catalog diagnostics, method/path normalization, duplicate operation, route collision, request mapping and response policies;
- Spring Boot starter tests for bean collection, WebMvc.fn activation, application MVC converters, Mediator-only dispatch, 400 mapping, security/filter participation and resolver propagation;
- a real multi-module application fixture with dependency-leaf contract, adapter Handler, ordinary JSON binding, special query/redirect binding, Command and Query variants;
- compiler Analyzer and IR source tests for real binding nodes, cross-file Handler association, generated provenance, local-only negatives, Controller coexistence and API Payload residue removal;
- Flow tests for Command root, Query non-root, stable entry identity, zero false positives and zero duplicate roots;
- Runtime/Analyzer descriptors, capability facts, Agent snapshot, Public Docs, Skill, full Gradle check and repository validation.
