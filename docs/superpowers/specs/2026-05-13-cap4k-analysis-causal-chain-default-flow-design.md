# cap4k Analysis Causal Chain Default Flow Design

## Context

Issue #43 changes the product meaning of the generated `flow` output. The current flow generator emits broad technical slices from the analysis graph. That output is useful for debugging, but it is not the primary business question users ask when reading a DDD system.

The primary analysis product should be the causal chain:

```text
entry -> command -> entity method -> domain event -> event handler -> command -> ...
```

This direction follows the NetCorePal comparison captured during the #25 discussion. NetCorePal's default processing flow is command/event centered, has no query or validator dimension, keeps event handlers visible, and starts only from root entry nodes without upstream relationships.

#25 remains the broader `irAnalysis` transport and design-projection boundary issue. #42 tracks post-v1 causal-chain follow-ups that should not block this first implementation slice.

## Current Behavior

`FlowGraphSupport.kt` currently builds flow entries from `AnalysisGraphModel` by:

- filtering a broad `allowedEdgeTypes` set;
- accepting entry nodes of type `controllermethod`, `commandsendermethod`, `querysendermethod`, `clisendermethod`, `validator`, and `integrationevent`;
- starting a flow from every matching entry node;
- traversing the filtered graph with DFS until no allowed outgoing edge remains or a visited edge prevents a loop;
- emitting one JSON artifact, one Mermaid artifact, and an index entry per start node.

The DFS stop behavior is already correct for v1. The problems are the projection and entry selection:

- query, cli, and validator paths make the default output noisy;
- middle nodes can become standalone flows because entry selection does not require "no upstream allowed edge";
- `CommandHandler` is visible even though it is normally one-to-one with `Command` and mostly exists as an extraction source for `CommandHandlerToEntityMethod`;
- the default output reads like a technical dependency graph instead of a business causal chain.

## Goals

Make the existing `flow` generator's default output a causal-chain projection.

The v1 generator must:

- keep the existing generator id `flow`, template ids, output root, JSON/Mermaid artifact shape, and index shape;
- narrow default causal edges to command/event causality;
- exclude query, cli, and validator paths from default flow generation;
- select only root entries that have no upstream causal-chain edge;
- collapse `Command -> CommandHandler -> EntityMethod` into `Command -> EntityMethod` in the emitted projection;
- keep `DomainEventHandler` and `IntegrationEventHandler` visible;
- keep DFS natural stop as the only terminal rule;
- avoid adding new graph metadata or redesigning `AnalysisGraphModel`.

The user-facing result should prioritize the chain users care about:

```text
ControllerMethod -> Command -> EntityMethod -> DomainEvent -> DomainEventHandler -> Command
CommandSenderMethod -> Command -> EntityMethod -> DomainEvent -> IntegrationEvent
IntegrationEvent -> IntegrationEventHandler -> Command
```

## Non-Goals

This slice does not:

- redesign `irAnalysis` source transport from #25;
- add a separate debug-flow generator;
- add HTML, Markdown, snapshot history, or explorer UI output;
- fold or hide generated empty event handlers;
- stitch outbound integration events from one service to inbound integration events in another service;
- add endpoint or job analysis if those node types are not already present;
- make query, cli, validator, or read-side dependencies part of the default causal-chain output;
- add node attributes for integration-event role.

Those follow-ups stay in #42.

## Projection Rules

### Causal Edge Set

The default causal-chain projection should use this v1 edge vocabulary:

```text
ControllerMethodToCommand
CommandSenderMethodToCommand
CommandToCommandHandler
CommandHandlerToEntityMethod
EntityMethodToEntityMethod
EntityMethodToDomainEvent
DomainEventToHandler
DomainEventHandlerToCommand
DomainEventToIntegrationEvent
IntegrationEventToHandler
IntegrationEventHandlerToCommand
```

Edges outside this set are ignored by the default `flow` output.

This deliberately removes:

```text
ControllerMethodToQuery
ControllerMethodToCli
QuerySenderMethodToQuery
CliSenderMethodToCli
ValidatorToQuery
QueryToQueryHandler
CliToCliHandler
DomainEventHandlerToQuery
DomainEventHandlerToCli
IntegrationEventHandlerToQuery
IntegrationEventHandlerToCli
```

### Entry Node Set

The v1 entry node types are:

```text
controllermethod
commandsendermethod
integrationevent
```

`integrationevent` remains an entry type so inbound integration events can start a chain:

```text
IntegrationEvent -> IntegrationEventHandler -> Command -> ...
```

`querysendermethod`, `clisendermethod`, and `validator` are not default causal-chain entries.

### Root Entry Filtering

A node is a default flow entry only when:

- its lowercased type is in the v1 entry node set; and
- no v1 causal edge points to it.

This mirrors NetCorePal's "no upstream node" rule and prevents middle nodes from being emitted as separate default flows.

The upstream check is scoped to the v1 causal projection, not the entire raw graph. A query-only edge should not disqualify a command causal-chain root.

### Command Handler Collapse

`CommandHandler` should be hidden in the emitted v1 flow projection.

The raw analysis graph can still contain:

```text
Command -> CommandHandler -> EntityMethod
```

The default causal-chain output should emit:

```text
Command -> EntityMethod
```

with an edge type that keeps the projection understandable. The preferred v1 edge label is:

```text
CommandToEntityMethod
```

This is a projection edge, not a new raw analysis relationship. The raw graph does not need to change.

If a command handler has multiple entity-method edges, the projection emits one `CommandToEntityMethod` edge per target entity method. If an intermediate command handler node is missing from `nodesById`, the projection should still avoid exposing the handler as a flow node and should preserve reachable entity-method targets when the edge data is sufficient.

### Event Handler Visibility

Event handlers stay visible:

```text
DomainEvent -> DomainEventHandler -> Command
IntegrationEvent -> IntegrationEventHandler -> Command
```

This follows the NetCorePal default. Unlike command handlers, event handlers are part of the causal story because one event can fan out to multiple handlers and those handlers may trigger different follow-up commands.

Generated empty event handlers are not folded in v1. DFS naturally stops at an event handler that has no allowed outgoing causal edge.

### Integration Event Boundary

v1 should not assume a service consumes its own outbound integration event.

The default semantics are:

- `DomainEvent -> IntegrationEvent` is an outbound boundary when there is no causal continuation inside the same graph;
- `IntegrationEvent -> IntegrationEventHandler -> Command` is an inbound boundary and command continuation when the event is a root entry;
- cross-service linking is deferred to #42.

Because `AnalysisNodeModel` currently has no metadata map, v1 does not add role-aware node attributes. If role can be inferred from existing package naming or edge shape without changing the model, the implementation may use it for labels, but correctness must not depend on new metadata.

## Data Flow

The existing planner shape remains:

```text
AnalysisGraphModel
  -> causal projection edge filtering
  -> command-handler collapse
  -> root entry selection
  -> DFS natural traversal
  -> FlowEntryPayload JSON
  -> Mermaid text
  -> FlowIndexPayload JSON
```

Keeping the artifact contract stable lets existing downstream consumers keep reading `flows/*.json`, `flows/*.mmd`, and `flows/index.json`. The meaning of those artifacts becomes narrower and more useful.

## Error Handling

The flow generator should keep the current tolerant behavior:

- duplicate edges are deduplicated by `(fromId, toId, type, label)`;
- missing nodes discovered through edges become `unknown` nodes only when they survive projection;
- cycles stop through visited-edge and visited-node tracking;
- empty causal-chain output produces an index with zero flows rather than failing.

The command-handler collapse should be tolerant:

- a `CommandToCommandHandler` edge without matching `CommandHandlerToEntityMethod` continuation does not emit the handler;
- orphan `CommandHandlerToEntityMethod` edges are ignored unless they can be reached from a command through a hidden handler;
- malformed graph fragments should not fail the entire planner.

## Testing

Unit coverage should be added or updated in `FlowArtifactPlannerTest`.

The tests should cover:

- root entry filtering prevents a middle `CommandSenderMethod` or `IntegrationEvent` from creating a duplicate flow when it has an upstream causal edge;
- query, cli, and validator paths are excluded from default flow output and index counts;
- `Command -> CommandHandler -> EntityMethod` emits `Command -> EntityMethod` and does not emit a `CommandHandler` node;
- `DomainEventHandler` remains visible when it sends a command;
- `IntegrationEventHandler` remains visible when an inbound integration event sends a command;
- DFS naturally stops at a generated empty event handler;
- output paths, template ids, and index JSON structure stay compatible with the current `flow` generator.

## Issue Lifecycle

This spec belongs to #43.

Related issues:

- #25: broader `irAnalysis` transport and design-projection boundary;
- #42: post-v1 causal-chain follow-ups.

After this spec is accepted, the next step is a focused implementation plan for #43. The plan should not include #42 follow-ups.
