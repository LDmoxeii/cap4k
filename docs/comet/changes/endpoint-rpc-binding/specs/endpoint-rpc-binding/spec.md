# Endpoint RPC Binding

## Purpose

Cap4k MUST provide a production sibling-service RPC binding over the accepted transport-neutral Endpoint published contract. Provider ingress and Consumer egress MUST compose through the existing Endpoint Mediator family: Provider transport invokes `Mediator.endpoints`, while Consumer transport is represented locally by a concrete remote `EndpointHandler<Request, Response>` selected by the same Mediator API.

The first shipped backend MUST be synchronous unary HTTP/JSON with a versioned RPC envelope and fixed Provider endpoint. Feign, gRPC and discovery middleware MAY be added later only behind the transport runtime SPI; they MUST NOT become business APIs or alter the Endpoint contract.

## Module and dependency ownership

- `ddd-endpoint-rpc` owns transport-neutral RPC binding descriptors, invocation SPI, wire/envelope contracts, typed codec contract and remote failure taxonomy.
- `ddd-endpoint-rpc-http` owns the first HTTP/JSON route resolver, client invocation and protocol-specific failure mapping.
- `cap4k-ddd-endpoint-rpc-http-starter` owns Spring Boot Provider/Consumer assembly, fixed Servlet endpoint, application codec integration, configuration and bean materialization.
- `cap4k-contract-api` and generated Endpoint contracts MUST NOT depend on any RPC module, Spring, client artifact or Provider implementation.
- `ddd-core` MUST NOT gain discovery, endpoint address, network fallback, retry or protocol client behavior.

These modules form a hierarchy rather than three sibling binding kinds:

```text
Endpoint bindings
├── direct HTTP Provider binding
│   ├── ddd-endpoint-http
│   └── cap4k-ddd-endpoint-http-starter
└── RPC binding
    ├── ddd-endpoint-rpc
    └── HTTP/JSON backend
        ├── ddd-endpoint-rpc-http
        └── cap4k-ddd-endpoint-rpc-http-starter
```

At the binding-semantics level, `ddd-endpoint-rpc` is the RPC counterpart to the accepted Endpoint HTTP binding. `ddd-endpoint-rpc-http` is not another Endpoint HTTP binding; it is the first network backend selected underneath the RPC ABI. The existing direct HTTP binding remains intentionally HTTP-specific and is not renamed or split by this Change. North-South and East-West MAY describe typical deployment traffic in documentation, but MUST NOT replace protocol, Provider/Consumer ownership, artifact, package, capability or Analyzer identities.

Provider and Consumer runtime modules MAY depend on the published contract and `ddd-core` in the normal outward direction. A generated client artifact MUST depend on the published contract and `ddd-endpoint-rpc`; it MUST NOT compile against `ddd-endpoint-rpc-http` or the starter. The Consumer assembly selects the concrete backend by adding the starter, whose runtime graph provides `ddd-endpoint-rpc-http` and the neutral RPC ABI. The contract MUST remain a dependency leaf.

`endpoint-client` is a feature-scoped packaging role for a generated, provider-published Consumer outbound adapter artifact. It is not a fifth default business layer, does not change the official default `domain` / `application` / `adapter` / `start` topology, and MUST NOT contain checked-in business implementation. The concrete remote Handler remains logically a Consumer adapter even when packaged separately for cross-repository publication and reuse.

## Stable identities and authoring

RPC binding identity MUST be the tuple:

```text
bindingKind = endpoint-rpc
serviceId
operationName
```

`operationName` MUST be the generated Endpoint operation's existing `OPERATION_NAME`; RPC MUST NOT derive it from Kotlin type names, routes or service interfaces. `serviceId` is a stable logical service identity owned by RPC binding authoring. It MUST NOT be a base URI, host name, credential, environment name or runtime instance identity.

The Gradle DSL MUST provide:

- `project.endpointClientModulePath`, projected as feature-scoped packaging role `endpoint-client`;
- `generators.endpointRpc.serviceId`;
- `generators.endpointRpc.operationNames`.

Enabling `endpointRpc` MUST require non-blank `serviceId`, a non-empty duplicate-free explicit operation selection, `contractModulePath`, `adapterModulePath` and `endpointClientModulePath`. Unknown selected operations, duplicate identities, missing roles and conflicting output paths MUST fail before rendering. Authored Endpoint existence alone MUST NOT expose it over RPC.

Deployment address, instance topology, timeout, authentication and discovery configuration MUST remain outside Design JSON Endpoint blocks, canonical Endpoint models and generated contracts.

## Shared Endpoint binding kernel

HTTP and RPC Provider bindings MUST consume the same transport-neutral generated operation evidence and coherence rules:

- direct reference to the generated `OPERATION_NAME`;
- Request and Response ownership by the same generated operation;
- exact `EndpointRequest<Response>` generic coherence;
- Provider dispatch only through `Mediator.endpoints`;
- Analyzer association from authenticated operation/Request evidence to the independent Provider Handler and its handle-reachable Command/Query calls.

This shared kernel MUST NOT collapse the two public binding descriptors or Actor identities. HTTP continues to own method, path, request mapping, response policy, MVC codec, security and HTTP error behavior. RPC owns service identity, envelope, Consumer remote Handler, route resolution, timeout/auth/retry policy and remote failure semantics. Existing HTTP binding behavior and evidence remain unchanged.

## Generated Provider and client artifacts

From the same canonical Endpoint model and RPC binding configuration, `cap4kGenerateSources` MUST generate:

1. adapter-owned typed `EndpointRpcProviderBinding<Request, Response>` registrations for each selected operation;
2. one concrete remote `EndpointHandler<Request, Response>` per selected operation in the `endpoint-client` module;
3. one generated client auto-configuration that registers those handlers when the client artifact is selected;
4. stable managed auto-configuration metadata/resources required for registration.

Provider registrations and client artifacts are framework-owned generated outputs and MUST be overwritten deterministically from current source truth. The Endpoint contract object remains checked-in AUTHORING output and MUST NOT be modified by RPC generation.

The generated client artifact MUST NOT expose a service facade or transport interface intended for application injection. Generated remote Handler classes MAY be internal. Consumer application code MUST NOT be required to call or inject the Handler, auto-configuration, HTTP client, invoker or any Feign/gRPC type.

The generator MUST NOT create a second operation/schema catalog that can drift from the generated Endpoint object. Generated registrations MUST reference `OPERATION_NAME`, Request and Response types directly and retain compile-time provenance required by Analyzer.

The framework does not create the Gradle module, build script or publication configuration. The configured `endpoint-client` module and its dependencies/publishing remain repository build ownership. This module contains generated outbound adapter implementation, not a business-facing service facade or an additional DDD layer.

## Provider binding and dispatch

`EndpointRpcProviderBinding<Request, Response>` MUST be immutable and typed. Activation MUST validate:

- non-blank `serviceId` and `operationName`;
- generated Request/Response share one operation owner;
- owner exposes public String `OPERATION_NAME` equal to the binding operation;
- Request implements `EndpointRequest<Response>` with the exact registered Response;
- `(serviceId, operationName)` is unique;
- configured Provider service identity matches the generated binding service identity.

The HTTP/JSON backend MUST expose exactly one fixed Provider route:

```text
POST /cap4k/endpoints/rpc
```

Unsupported method, unsupported protocol version, malformed envelope, unknown service/operation, invalid context, request decode failure or response encode failure MUST fail deterministically without invoking the local Handler.

For a valid request the Provider MUST:

1. resolve the typed binding;
2. decode the Request using its registered type;
3. decode external ExecutionContext elements with `ExecutionContextBoundary.RPC` and install the scope;
4. invoke `Mediator.endpoints.send(request)`;
5. encode the published Response into the success envelope.

The Provider transport MUST NOT directly locate or invoke `EndpointHandler`, Command, Query or Capability. The independent local Provider Handler remains adapter-owned checked-in source and contains the explicit published-to-local mapping.

The fixed endpoint MUST participate in the application's existing Servlet filter and Spring Security chain. This capability adds no authorization DSL.

## Consumer dispatch and middleware isolation

A generated remote Handler MUST implement the exact concrete generic type:

```text
EndpointHandler<PublishedEndpoint.Request, PublishedEndpoint.Response>
```

Its `handle` operation MUST delegate to a generic `EndpointTransportInvoker` with stable service/operation identity and typed Request/Response evidence. It MUST NOT contain deployment addresses, credentials, timeout values or retry policy constants.

Consumer application code accepting the Provider's published language MUST invoke only:

```text
Mediator.endpoints.send(publishedRequest)
Mediator.endpoints.sendAsync(publishedRequest)
```

When a local anti-corruption language is required, the supported path is:

```text
Mediator.capabilities
  -> adapter-owned CapabilityHandler mapping
  -> Mediator.endpoints
  -> generated remote EndpointHandler
  -> EndpointTransportInvoker
```

Neither path may directly call the generated Handler or transport proxy. Selecting the client artifact and configuring the backend is Consumer assembly work, not business code.

A process MUST still have at most one applicable Endpoint Handler for each concrete Request. Local Provider Handler plus remote Handler, two client artifacts or two remote backends for the same Request MUST fail through deterministic existing uniqueness semantics. RPC MUST NOT add local/remote priority, fallback or dynamic routing to `EndpointSupervisor`.

## HTTP/JSON wire contract

The first backend MUST implement synchronous unary request/response. The request envelope MUST carry only transport control data required to dispatch safely, including:

- protocol version;
- `serviceId`;
- `operationName`;
- encoded Request payload;
- encoded allowlisted ExecutionContext elements.

The response envelope MUST distinguish success from transport/remote failure. On success, the payload MUST decode to the published Endpoint Response type. On failure, only a stable safe category/code and non-sensitive correlation evidence MAY cross the boundary.

The envelope is not a tactical semantic role, Design block, canonical value type, generated business DTO family or replacement API Payload. Published Request/Response remain the business wire schema. No implicit business response wrapper is introduced.

The runtime MUST expose an `EndpointRpcCodec` contract. The default HTTP/JSON implementation MAY use the application-configured Jackson `ObjectMapper`, but protocol version and codec identity MUST be explicit and mismatches MUST fail closed. Raw payloads and decoded values MUST NOT appear in diagnostics or exceptions.

Provider exception class, message, cause and stack trace MUST NOT be serialized. Consumer failures MUST surface as a sanitized `EndpointRemoteInvocationException` carrying safe category, serviceId, operationName and optional transport status/correlation only.

## Routing, timeout, authentication and retry

The Consumer backend MUST resolve logical `serviceId` through `EndpointRpcRouteResolver`. The default implementation MUST use an explicit assembly-owned static `serviceId -> absolute base URI` map with deterministic URI normalization and rejection of user info, query, fragment and invalid schemes.

Connect and response timeouts MUST be positive assembly configuration. Authentication and additional request headers MUST be supplied through an assembly-owned request customizer/interceptor and MUST NOT enter generated artifacts or contract metadata.

Automatic retry MUST be absent by default. Because a timeout may occur after a Command has executed, the first backend MUST NOT silently retry ambiguous requests. A Consumer may explicitly install an invoker decorator only when it owns an idempotency decision; this customization remains outside the canonical Endpoint contract and default runtime behavior.

Spring Cloud discovery/load-balancer, OpenFeign and gRPC are not shipped by this capability. A future backend MAY implement `EndpointTransportInvoker` or `EndpointRpcRouteResolver`, but business code and generated Endpoint contracts MUST remain unchanged.

## Analyzer and Flow evidence

Only a real Provider inbound registration is RPC Actor evidence. Analyzer MUST create node type:

```text
endpointrpcproviderbinding
```

with stable identity derived from:

```text
endpoint-rpc:<serviceId>:<operationName>
```

Analyzer MUST authenticate direct reference to the generated `OPERATION_NAME`, Request/Response owner and Endpoint design metadata, then join the binding to the independent Provider Handler through Request FQN and handle-reachable invocation evidence.

Command mapping MUST use `EndpointRpcProviderBindingToCommand`. Query mapping MUST use `EndpointRpcProviderBindingToQuery`. Capability invocation MUST NOT be projected as Command/Query mapping.

The following MUST NOT create an RPC Actor node, relationship or Flow root:

- contract declaration only;
- Provider Handler only;
- generated Consumer remote Handler/client artifact;
- local `Mediator.endpoints` dispatch;
- copied operation-name literal;
- descriptor without production registration;
- mismatched Request/Response or design provenance;
- arbitrary relationship name ending in `ToCommand`.

Command-oriented Provider RPC binding is eligible for one default Actor Flow root. Query-oriented binding remains raw Graph-only. HTTP and RPC bindings for the same operation remain separate entry identities and may produce two Flows sharing downstream nodes. Consumer outbound calls do not add a third entry.

## Capability projections

Production descriptors, registries and plan evidence MUST declare:

- RPC Provider and Consumer runtime ownership;
- `endpointRpc` generator configuration and selected operations;
- provider/client generated outputs and `endpoint-client` role;
- Analyzer RPC Provider detector and relationships;
- Flow Command-root/Query-non-root behavior.

Runtime Agent facts MUST expose `runtime.endpoint-rpc-provider` and `runtime.endpoint-rpc-consumer` with accurate implementation/starter ownership. Static assembly remains `UNKNOWN`; observation and verification remain `NOT_PERFORMED`. Endpoint RPC identities MUST NOT be inserted into the Integration Event provider catalog.

Public Docs and the authoring Skill MUST document the Mediator-only Consumer path, explicit service/operation selection, generated client artifact, HTTP/JSON backend, static route map, timeout/auth/retry boundary, Provider-only Actor evidence and explicit Feign/gRPC/discovery non-goals. They MUST NOT restore `ActorEndpoint` vocabulary or API Payload.

## Verification

Verification MUST cover:

- typed binding, client Handler and identity validation;
- explicit generator selection, required module roles, deterministic generated-source/resource ownership and no new public task;
- fixed HTTP route, versioned envelope, Jackson codec, external RPC ExecutionContext, route normalization, timeouts, auth customization and sanitized failures;
- Provider network ingress resolves typed binding, installs external RPC context and reaches the independent business Handler only through the Endpoint Supervisor;
- Consumer direct Endpoint and Capability ACL invocation only through Mediator;
- true cross-process HTTP roundtrip using generated Provider/client artifacts;
- duplicate/missing/mismatched Handler, binding, service, operation, codec and route failures;
- Analyzer Provider RPC evidence and all negative cases;
- Command Flow, Query non-Flow and HTTP+RPC sibling composition;
- Runtime/Generator/Analyzer/AgentFacts/Public Docs/Skill propagation and repository guards.

## Non-goals

- public Feign/gRPC interfaces or shipped Feign/gRPC backend;
- dynamic discovery, load balancing or automatic retry;
- distributed transaction, exactly-once or framework idempotency guarantee;
- local/remote Handler coexistence routing;
- streaming, multipart, binary, WebFlux or bidirectional RPC;
- automatic module/build/publication creation;
- Consumer outbound Actor/Flow projection;
- Endpoint contract, HTTP Provider binding or API Payload boundary redesign.
