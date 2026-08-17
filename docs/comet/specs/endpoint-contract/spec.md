# Endpoint Published Contract

## Purpose

Cap4k MUST model every externally invokable Actor operation as an explicit, transport-neutral Endpoint published contract. HTTP and RPC are bindings over this contract; internal Command, Query and Capability remain distinct application semantics and MUST NOT become published automatically.

Endpoint invocation MUST preserve cap4k's Request + Handler + Response model and MUST be dispatched through Mediator. A Consumer RPC proxy is a local Handler selected by Mediator, not a service object directly called by business code.

The published language MUST live in a dependency-leaf `contract` module so a consumer can depend on operation and event shapes without depending on the Provider service implementation.

## Naming boundary

The Design JSON tactical tag MUST be `endpoint`. `actor` identifies the Analyzer/Flow trigger-source family, while `endpoint` identifies the authored invokable operation. The framework MUST NOT use an `actor` tag, because an Actor may enter through HTTP, RPC, CLI, admin or another real binding and is not itself a Request/Response contract.

The primary artifact family MUST also be `endpoint`.

Product code, capability names, Public Docs and Skill MUST use `Endpoint` rather than the compound `ActorEndpoint`. `Actor` remains only the Analyzer/Flow trigger-source classification. The canonical capability identity MUST be `endpoint-contract`; the former `actor-endpoint-contract` capability is removed without alias or compatibility bridge.

## Canonical operation

An Endpoint declaration MUST represent exactly one operation and MUST contain:

- a canonical Kotlin package and type identity;
- a non-blank stable logical `operationName`;
- a description and optional aggregate/context associations used only as design metadata;
- one ordered Request semantic value definition;
- one ordered Response semantic value definition.

Request and Response MUST use dedicated Endpoint semantic roles. They MUST NOT reuse Command, Query, Capability or API payload roles even when their field shapes happen to be equal.

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
- when an Endpoint or Integration Event payload artifact is selected, absence MUST fail with a stable diagnostic naming the required role/property and selected artifact;
- setting only `basePackage` plus `contractModulePath` MUST count as an explicitly configured project layout when the selected operation can be planned without other roles.

All Endpoint primary artifacts and Integration Event payload artifacts MUST plan with:

- `moduleRole = "contract"`;
- `outputKind = CHECKED_IN_SOURCE`;
- AUTHORING lane ownership;
- the existing `cap4kPlan` and `cap4kGenerate` tasks.

The framework MUST NOT add a new public generation task and MUST NOT route these artifacts through the generated-source lane.

## Package and layout ownership

The DSL MUST expose a dedicated Endpoint contract package layout and a published Integration Event contract package layout. Defaults MUST be deterministic and MUST not place published payloads under application or adapter package roots.

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

The canonical Endpoint identity, generated Request/Response and Endpoint Mediator family support independent transport bindings without moving protocol metadata into the contract.

The shipped Spring MVC Provider binding MUST:

- reference the existing generated `OPERATION_NAME` plus Request/Response type evidence;
- keep method, route, codec, status, headers, security and error mapping outside the contract module;
- materialize a real route that invokes `Mediator.endpoints.send(request)`;
- reuse the same binding-neutral local Provider Handler that the shipped RPC binding also uses.

The shipped RPC Provider dispatcher MUST invoke the same Mediator family, and the shipped generated Consumer proxy MUST implement `EndpointHandler<Request, Response>`. RPC service identity and explicit operation selection belong to the binding/client projection; service discovery, address, timeout, retry, topology, authentication, codec and protocol client/stub details MUST remain absent from the canonical contract and `ddd-core`.

The shipped RPC client artifact MUST be generated outside the contract module and depend on the published contract in one direction. Consumer business code MUST invoke the remote operation only through `Mediator.endpoints`; Feign/gRPC interfaces, `EndpointTransportInvoker` and generated remote Handlers remain transport/runtime implementation details.

One operation MAY have zero or multiple Provider bindings of different kinds. Binding identity and lifecycle MUST NOT redefine operation identity. HTTP and RPC protocol-specific cardinality and service grouping belong to their binding capabilities, not the canonical Endpoint contract.

## Analyzer and Flow

Analyzer Design Projection MUST recover Endpoint operation identity, Request/Response semantic shapes and selected artifacts from framework-owned Kotlin contract structure and compile-time metadata. Drawing Board MUST emit an ordinary `endpoint` Design JSON block that the Design JSON source accepts directly.

Analyzer Graph and Flow MUST treat a contract declaration, Provider Handler existence and local Endpoint Mediator dispatch as non-transport-entry evidence. Generating, compiling or locally dispatching an Endpoint Request alone MUST NOT create:

- an Actor graph node;
- an Endpoint HTTP or RPC Provider binding-to-Command/Query relationship;
- a Flow root;
- a flow count change.

Real typed Spring MVC and Endpoint RPC Provider bindings are production Actor evidence. Analyzer MUST join each binding to the independent Provider Handler through generated operation/Request evidence without assuming a shared class or file. Command-oriented HTTP or RPC Provider evidence can create a default Flow root; Query-oriented evidence remains raw Graph-only. Consumer RPC proxy, contract-only, Handler-only and local Endpoint dispatch remain non-entry evidence.

## Capability projections

Production descriptors and registries MUST declare the Endpoint authoring/runtime capabilities, tactical carrier, required source, tasks, output ownership and module roles. The shipped Endpoint HTTP capability MUST declare its Runtime implementation/starter ownership and Analyzer detector/relationship evidence. The shipped Endpoint RPC capability MUST declare Provider/Consumer Runtime ownership, explicit binding selection, generated Provider/client outputs, the `endpoint-client` role and RPC Provider Analyzer evidence. AgentFacts MUST derive project module, artifact and Runtime ownership facts from these production contracts rather than a second handwritten catalog.

Public Docs and the authoring Skill MUST describe only shipped behavior. They MUST describe the published contract, Endpoint Mediator family, independent Provider Handler, Spring MVC Provider binding and the generated RPC client artifact whose remote Handlers remain behind `Mediator.endpoints`. Feign/gRPC backends, dynamic discovery, automatic retry and local/remote Handler coexistence remain explicit non-goals.

Runtime, Generator, Analyzer, AgentFacts, Public Docs and Skill impact MUST each be recorded as modified, verified-no-change or not-applicable with evidence. HTTP binding authoring leaves Generator/Renderer unchanged; RPC binding adds generated Provider/client projections while preserving the existing Endpoint contract generator and Design Projection.

## Verification

Focused and functional evidence MUST demonstrate:

1. conditional `contractModulePath` projection and diagnostics;
2. first-class canonical Endpoint identity and dedicated semantic roles;
3. deterministic checked-in contract planning and rendering;
4. compile-valid generated Request/Response using `EndpointRequest` from a leaf contract module;
5. EndpointSupervisor sync/async dispatch, unique Handler selection, validation, scope and failure semantics;
6. Provider business Handler and generated Consumer remote Handler semantics both remain mediated rather than directly invoked, including direct Consumer Endpoint use and Capability anti-corruption mapping;
7. Provider/client single-direction dependency on contract and absence of Spring/HTTP/RPC metadata from generated contracts;
8. typed Spring MVC Provider registration and WebMvc.fn route materialization invoke the independent Provider Handler only through `Mediator.endpoints`;
9. typed RPC Provider registration and generated Consumer Handler/client artifact complete a real remote roundtrip while business code still invokes only `Mediator.endpoints`;
10. one operation can exist Handler-only or expose HTTP and RPC independently without changing contract/Handler identity;
11. Integration Event payload placement in contract and subscriber placement in application;
12. unchanged event direction, topology and runtime delivery semantics;
13. Analyzer/Drawing Board semantic round-trip remains unchanged while real HTTP/RPC Provider binding evidence creates only the accepted Graph/Flow Actor entries;
14. descriptor, registry, AgentFacts, docs and Skill propagation;
15. unchanged Endpoint contract rendering and unchanged existing domain/application artifact ownership outside the declared RPC generated Provider/client projections.

## Compatibility

This is an intentional breaking architecture change. The former `actor-endpoint-contract` capability name and `ActorEndpoint` product vocabulary are removed. No alias, fallback capability, duplicate API, fallback package, duplicate annotation, old application-owned Integration Event generation or migration bridge is required.
