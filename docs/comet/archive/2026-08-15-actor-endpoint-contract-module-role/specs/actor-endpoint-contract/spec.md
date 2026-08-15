# ActorEndpoint Published Contract

## Purpose

Cap4k MUST model every externally invokable Actor operation as an explicit, transport-neutral ActorEndpoint published contract. HTTP and RPC are bindings over this contract; internal Command, Query and Capability remain distinct application semantics and MUST NOT become published automatically.

ActorEndpoint invocation MUST preserve cap4k's Request + Handler + Response model and MUST be dispatched through Mediator. A Consumer RPC proxy is a local Handler selected by Mediator, not a service object directly called by business code.

The published language MUST live in a dependency-leaf `contract` module so a consumer can depend on operation and event shapes without depending on the Provider service implementation.

## Naming boundary

The Design JSON tactical tag MUST be `endpoint`. `actor` identifies the Analyzer/Flow trigger-source family, while `endpoint` identifies the authored invokable operation. The framework MUST NOT use an `actor` tag, because an Actor may enter through HTTP, RPC, CLI, admin or another real binding and is not itself a Request/Response contract.

The primary artifact family MUST also be `endpoint`.

## Canonical operation

An ActorEndpoint declaration MUST represent exactly one operation and MUST contain:

- a canonical Kotlin package and type identity;
- a non-blank stable logical `operationName`;
- a description and optional aggregate/context associations used only as design metadata;
- one ordered Request semantic value definition;
- one ordered Response semantic value definition.

Request and Response MUST use dedicated ActorEndpoint semantic roles. They MUST NOT reuse Command, Query, Capability or API payload roles even when their field shapes happen to be equal.

Canonical assembly MUST reject duplicate logical operation names across the assembled design, duplicate canonical type identities, blank operation names, invalid semantic fields and unresolved/unsupported types before artifact planning.

The canonical unit is one operation. A multi-operation service interface, facade or client grouping MAY be added later as a projection, but MUST NOT replace or redefine operation identity.

## Published Kotlin form

The framework-owned default artifact MUST generate one Kotlin object per operation. The object MUST:

- own a Request implementing the lightweight `EndpointRequest<Response>` marker;
- own its Response data contract;
- expose the stable logical operation name as a framework-owned constant;
- preserve ordered fields, nested DTOs, resolved canonical types, nullability and supported defaults;
- carry lossless compile-time Design metadata needed for Analyzer/Drawing Board round-trip.

A representative shape is:

```kotlin
object CreateBookingEndpoint {
    const val OPERATION_NAME: String = "booking.create"

    data class Request(/* fields */) : EndpointRequest<Response>

    data class Response(/* result fields */)
}
```

The generated contract MUST NOT contain Spring stereotypes, Mediator implementation, Handler implementation, HTTP/RPC annotations, service discovery, retry, timeout, routing or persistence behavior.

The generated Request MUST NOT implement internal `Command`, `Query` or `CapabilityCall` markers. Provider-side translation to local application messages is the Provider Endpoint Handler's responsibility.

## Lightweight contract API

Cap4k MUST publish a minimal `cap4k-contract-api` artifact containing only stable primitives required by shared published contracts, including:

- `EndpointRequest<RESULT>`;
- the shared Integration Event identity annotation required by runtime catalogs/codecs.

This artifact MUST NOT contain Endpoint Handler dispatch, Supervisor or Mediator implementations and MUST NOT depend on Spring, Gradle, compiler APIs, persistence APIs, HTTP/RPC transports, broker clients or full `ddd-core`.

The Provider service, Consumer service and generated business contract module MAY all depend on this lightweight artifact without acquiring cap4k's application/runtime implementation surface.

No compatibility facade for the former Integration Event annotation package is required.

## Endpoint Mediator family

`ddd-core` MUST provide a dedicated Endpoint application dispatch family:

```text
EndpointRequest<Response>
        ↓
EndpointSupervisor.send(request)
        ↓
EndpointHandler<Request, Response>
        ↓
Response
```

It MUST expose:

- `EndpointHandler<REQUEST : EndpointRequest<RESPONSE>, RESPONSE>` with one `handle(request)` operation;
- `EndpointSupervisor.send(request)`;
- `EndpointSupervisor.sendAsync(request)`;
- `Mediator.endpoints` as the static access surface.

The Supervisor MUST follow the established synchronous application dispatcher constraints for concrete Request-to-Handler resolution, duplicate/missing Handler failure, Bean Validation, invocation policy, invocation scope, execution-context propagation, causal frame management and asynchronous failure reporting.

Endpoint is a dedicated category. It MUST NOT restore an untyped/general Request dispatcher and MUST NOT apply Command, Query or Capability policy merely because field shapes are similar.

## Provider and Consumer perspectives

The published Request/Response and operation identity are shared, while Handler ownership is local to each process.

On a Provider service:

- the local `EndpointHandler<Request, Response>` is the business-facing implementation;
- it MAY translate the published Request into local Command/Query calls through their existing Mediator families;
- an HTTP/RPC Provider binding deserializes the Request and invokes `Mediator.endpoints.send(request)`;
- the binding MUST NOT bypass the Endpoint Supervisor by calling the Handler directly.

On a Consumer service:

- the local `EndpointHandler<Request, Response>` MAY be a generated RPC proxy;
- that proxy serializes the Request, invokes the remote Provider using the stable operation identity and returns the Response;
- application code MUST NOT directly inject or call the generated proxy;
- application code MAY directly invoke `Mediator.endpoints.send(publishedRequest)` when accepting the Provider's published language as a local dependency;
- when an anti-corruption boundary is required, application code MAY instead invoke a local `CapabilityCall` through `Mediator.capabilities`; its adapter-owned Capability Handler maps the local Request/Response to the published Endpoint Request/Response and invokes `Mediator.endpoints`.

The same Request type therefore participates in the same Mediator contract on both sides, while the locally registered Handler differs:

```text
Provider process: Request -> Mediator -> local business Handler
Consumer direct: published Request -> Endpoint Mediator -> RPC proxy Handler -> network
Consumer ACL: local Capability -> Capability Mediator -> mapping Handler -> Endpoint Mediator -> RPC proxy Handler -> network
```

A process MUST still have at most one applicable Endpoint Handler for a concrete Request. Supporting both a local Provider implementation and remote proxy for the same Request in one application requires an explicit future routing decision and is outside this Change.

## Contract module role

The Gradle project DSL MUST expose `project.contractModulePath`. The assembled `ProjectConfig.modules` MUST project it as role `contract` with a repository-relative module path.

The role is conditionally required:

- when no selected planner owns a contract artifact, `contractModulePath` MAY be absent;
- when an ActorEndpoint or Integration Event payload artifact is selected, absence MUST fail with a stable diagnostic naming the required role/property and selected artifact;
- setting only `basePackage` plus `contractModulePath` MUST count as an explicitly configured project layout when the selected operation can be planned without other roles.

All ActorEndpoint primary artifacts and Integration Event payload artifacts MUST plan with:

- `moduleRole = "contract"`;
- `outputKind = CHECKED_IN_SOURCE`;
- AUTHORING lane ownership;
- the existing `cap4kPlan` and `cap4kGenerate` tasks.

The framework MUST NOT add a new public generation task and MUST NOT route these artifacts through the generated-source lane.

## Package and layout ownership

The DSL MUST expose a dedicated ActorEndpoint contract package layout and a published Integration Event contract package layout. Defaults MUST be deterministic and MUST not place published payloads under application or adapter package roots.

Output path, canonical FQN, template identity and ownership metadata MUST remain stable for equal canonical input. Collision detection MUST run before rendering/writing.

## Integration Event published language

Inbound and outbound Integration Event payloads are published contracts and MUST be generated into the contract module. Their application subscriber artifacts MUST remain in the application module.

Moving the payload owner MUST NOT change existing Integration Event semantics:

- `eventName` remains the stable transport-neutral identity;
- direction remains explicit Design/Analyzer metadata and is not inferred from the runtime annotation;
- outbound remains the default variant;
- a subscriber remains legal only for inbound;
- an outbound declaration MUST NOT automatically create or convert an inbound contract;
- provider route topology remains runtime configuration keyed by `eventName`;
- subscription derives only from a real local Handler;
- ack, retry, completion boundaries, idempotency, inbox/dedup and per-consumer state remain owned by their existing runtime/application boundaries.

The runtime Integration Event catalog, codec and transports MUST consume the shared annotation from `cap4k-contract-api` without adding transport dependencies to the contract module.

## Binding extension boundary

The canonical ActorEndpoint identity, generated Request/Response and Endpoint Mediator family MUST be sufficient for later bindings to:

- generate an HTTP adapter that invokes `Mediator.endpoints.send(request)`;
- generate an RPC Provider dispatcher that invokes the same Mediator family;
- generate a Consumer RPC proxy as an `EndpointHandler<Request, Response>`.

This Change MUST NOT implement those bindings or proxies. Binding-specific route names, protocol metadata, status/error mapping, service discovery, timeout, retry and topology MUST remain absent from the ActorEndpoint canonical contract.

## Analyzer and Flow

Analyzer Design Projection MUST recover ActorEndpoint operation identity, Request/Response semantic shapes and selected artifacts from framework-owned Kotlin contract structure and compile-time metadata. Drawing Board MUST emit an ordinary `endpoint` Design JSON block that the Design JSON source accepts directly.

Analyzer Graph and Flow MUST treat a contract declaration and local Endpoint Mediator dispatch as non-transport-entry evidence. Generating, compiling or locally dispatching an ActorEndpoint Request alone MUST NOT create:

- an Actor graph node;
- an Endpoint-to-Command/Query relationship;
- a Flow root;
- a flow count change.

Real HTTP and RPC Actor evidence belongs to their binding changes and requires production detectors observing actual binding/provider code.

## Capability projections

Production descriptors and registries MUST declare the new ActorEndpoint authoring/runtime capabilities, tactical carrier, required source, tasks, output ownership and module role. AgentFacts MUST derive project module and artifact ownership facts from these production contracts rather than a second handwritten catalog.

Public Docs and the authoring Skill MUST describe only shipped behavior. They MUST state that Change 1 provides the published contract, Endpoint Mediator family and module boundary but no HTTP/RPC binding.

Runtime, Generator, Analyzer, AgentFacts, Public Docs and Skill impact MUST each be recorded as modified, verified-no-change or not-applicable with evidence.

## Verification

Focused and functional evidence MUST demonstrate:

1. conditional `contractModulePath` projection and diagnostics;
2. first-class canonical ActorEndpoint identity and dedicated semantic roles;
3. deterministic checked-in contract planning and rendering;
4. compile-valid generated Request/Response using `EndpointRequest` from a leaf contract module;
5. EndpointSupervisor sync/async dispatch, unique Handler selection, validation, scope and failure semantics;
6. Provider Handler and Consumer proxy Handler both invoked through `Mediator.endpoints` rather than direct calls, including direct Consumer Endpoint use and Capability anti-corruption mapping;
7. consumer compilation with contract-only plus selected endpoint/RPC runtime dependencies;
8. Provider-side single-direction dependency on contract;
9. Integration Event payload placement in contract and subscriber placement in application;
10. unchanged event direction, topology and runtime delivery semantics;
11. Analyzer/Drawing Board semantic round-trip and no new Graph/Flow entry evidence;
12. descriptor, registry, AgentFacts, docs and Skill propagation;
13. unchanged existing domain/application/adapter artifact ownership outside the declared migration.

## Compatibility

This is an intentional breaking architecture change. No alias, fallback package, duplicate annotation, old application-owned Integration Event generation or migration bridge is required.



