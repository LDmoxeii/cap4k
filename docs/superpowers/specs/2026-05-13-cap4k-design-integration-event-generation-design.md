# cap4k Design Integration Event Generation Design

## Context

Issue #34 adds design-driven integration event support to the cap4k generation and analysis pipeline.

The current runtime already has integration event infrastructure:

- `@IntegrationEvent` marks integration event payload classes.
- `IntegrationEventSupervisor` publishes integration events.
- HTTP, RabbitMQ, and RocketMQ adapters scan `@IntegrationEvent` classes and consume/publish payloads through the existing event dispatch path.
- `DefaultEventSubscriberManager` bridges integration events into Spring `ApplicationEventPublisher`, so Spring `@EventListener` methods can handle them.

The current generator and analysis pipeline does not model integration events as design artifacts. `design.json` supports command, query, client, API payload, validator, and domain event families, but not `integration_event`.

The reference project currently contains a handwritten inbound integration event:

- `MediaProcessingCallbackIntegrationEvent`
- `MediaProcessingCallbackIntegrationEventSubscriber`

That sample proves the runtime path, but its subscriber currently implements `EventSubscriber<T>`. This design standardizes generated and author-facing subscriber skeletons on Spring `@EventListener`, which is already the analysis standard for event-handler flow extraction.

## Goals

Add first-class design support for integration events without redesigning runtime transport.

The feature must:

- support integration event contracts in design JSON;
- distinguish event-level role as `inbound` or `outbound`;
- generate inbound and outbound event contracts into separate packages;
- generate subscriber skeletons only for inbound events;
- keep subscriber identity out of business design JSON;
- keep `EventSubscriber<T>` as runtime SPI, not the generated authoring standard;
- update analysis output so generated integration events are visible to downstream analysis and drawing-board generation.

## Non-Goals

This slice does not:

- redesign HTTP, RabbitMQ, or RocketMQ integration event transport;
- introduce MQ-specific generators;
- merge domain event and integration event semantics;
- generate subscriber skeletons for outbound events;
- support mixed role values such as `inout` in the first version;
- standardize `EventSubscriber<T>` as the generated subscriber shape;
- solve weak-reference projection, enum translation, or read-model generation.

## Design JSON

Add a new design tag:

```json
{
  "tag": "integration_event",
  "package": "media.processing",
  "name": "MediaProcessingCallbackIntegrationEvent",
  "role": "inbound",
  "eventName": "cap4k.reference.contentstudio.media-processing.succeeded",
  "requestFields": [
    { "name": "externalTaskId", "type": "String" },
    { "name": "status", "type": "String" },
    { "name": "assetSha256", "type": "String" },
    { "name": "assetLocation", "type": "String" },
    { "name": "completedAt", "type": "java.time.LocalDateTime" }
  ],
  "responseFields": []
}
```

`role` is required and supports:

- `inbound`: this service consumes the event;
- `outbound`: this service publishes and exposes the event contract.

`eventName` is required and maps to `@IntegrationEvent.value`.

`subscriber` is not accepted in design JSON. It is not part of the event contract; it is consumer runtime identity. Generated inbound contracts use a Spring placeholder so the consumer identity is supplied by the running service.

`traits` remains reserved for shape modifiers such as `page` on query and API payload. `role` is separate because inbound/outbound is event collaboration semantics, not a request shape modifier.

## Generated Packages

Both contracts and subscribers stay in the application layer. The default root is:

```text
application.subscribers.integration
```

The planner appends the event role:

```text
application.subscribers.integration.inbound.<design package>
application.subscribers.integration.outbound.<design package>
```

For the sample above, generated files land under:

```text
application.subscribers.integration.inbound.media.processing
```

This keeps subscriber classes under `application.subscribers.integration` while separating event contracts consumed by this service from event contracts published by this service.

## Generated Contracts

Inbound integration events generate a data class with `@IntegrationEvent`:

```kotlin
@IntegrationEvent(
    value = MediaProcessingCallbackIntegrationEvent.EVENT_NAME,
    subscriber = MediaProcessingCallbackIntegrationEvent.SUBSCRIBER_NAME,
)
data class MediaProcessingCallbackIntegrationEvent(
    val externalTaskId: String,
    val status: String,
)
```

The companion object contains:

```kotlin
const val EVENT_NAME = "cap4k.reference.contentstudio.media-processing.succeeded"
const val SUBSCRIBER_NAME = "\${spring.application.name:}"
```

The placeholder keeps the service name out of design JSON while allowing the runtime environment to resolve the consumer identity.

Outbound integration events generate:

```kotlin
@IntegrationEvent(
    value = ContentPublishedIntegrationEvent.EVENT_NAME,
    subscriber = IntegrationEvent.NONE_SUBSCRIBER,
)
data class ContentPublishedIntegrationEvent(...)
```

Outbound events do not generate subscriber skeletons.

## Generated Subscribers

Inbound events generate a Spring `@EventListener` subscriber skeleton:

```kotlin
@Service
class MediaProcessingCallbackIntegrationEventSubscriber {

    @EventListener(MediaProcessingCallbackIntegrationEvent::class)
    fun on(event: MediaProcessingCallbackIntegrationEvent) {
    }
}
```

This is the standard generated shape because:

- existing domain event subscribers already use `@EventListener`;
- code-analysis flow extraction already recognizes event handlers through `@EventListener`;
- runtime integration event dispatch already republishes integration event payloads as Spring application events;
- `EventSubscriber<T>` remains available as low-level runtime SPI but is not the generated authoring surface.

## Generator Families

Add two built-in generator families:

- `designIntegrationEvent`
- `designIntegrationEventSubscriber`

`designIntegrationEvent`:

- requires `designJson`;
- requires `project.applicationModulePath`;
- plans contract artifacts for all `integration_event` entries;
- emits inbound contracts under `.inbound`;
- emits outbound contracts under `.outbound`.

`designIntegrationEventSubscriber`:

- requires `designIntegrationEvent`;
- requires `project.applicationModulePath`;
- plans subscriber artifacts only for `role = inbound`;
- ignores `role = outbound`.

The default layout adds:

```kotlin
layout {
    designIntegrationEvent {
        packageRoot = "application.subscribers.integration"
    }
    designIntegrationEventSubscriber {
        packageRoot = "application.subscribers.integration"
    }
}
```

The role segment is appended by the planner and is not configured through `package`.

## Canonical Model

Add:

```kotlin
enum class IntegrationEventRole {
    INBOUND,
    OUTBOUND,
}

data class IntegrationEventModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val role: IntegrationEventRole,
    val eventName: String,
    val fields: List<FieldModel> = emptyList(),
)
```

`CanonicalModel` gains:

```kotlin
val integrationEvents: List<IntegrationEventModel> = emptyList()
```

Canonical assembly validates:

- `tag = integration_event` is supported;
- `role` is present and is `inbound` or `outbound`;
- `eventName` is present and non-blank;
- `responseFields` must be empty;
- `requestFields` become event payload fields.

## Analysis Changes

The analysis pipeline must be updated with the generator.

### Design Element Extraction

`DesignElementCollector` should recognize `@IntegrationEvent` classes and emit:

```json
{
  "tag": "integration_event",
  "package": "media.processing",
  "name": "MediaProcessingCallbackIntegrationEvent",
  "role": "inbound",
  "eventName": "cap4k.reference.contentstudio.media-processing.succeeded",
  "requestFields": [...]
}
```

Role is inferred from package segments:

- `.application.subscribers.integration.inbound.` -> `inbound`
- `.application.subscribers.integration.outbound.` -> `outbound`

Classes outside those role packages may still be detected as runtime integration events by arch info, but they are not emitted as design elements unless their role can be inferred. This prevents analysis from inventing design semantics for arbitrary runtime classes.

`DesignElementJsonWriter` and `DesignElement` need `role` and `eventName` fields.

### Drawing Board

The drawing-board canonical model and generator must accept `integration_event` as a supported tag.

`DrawingBoardElementModel` gains optional:

- `role`
- `eventName`

The planner emits:

```text
drawing_board_integration_event.json
```

### Flow Analysis

Flow extraction continues to use `@EventListener` as the standard integration handler boundary.

No new flow support for `EventSubscriber<T>` is required in this slice. Generated subscribers use `@EventListener`, so generated code remains visible to existing flow relationships:

- `IntegrationEventToHandler`
- `IntegrationEventHandlerToCommand`
- `IntegrationEventHandlerToQuery`
- `IntegrationEventHandlerToCli`

## Reference Project Follow-Up

The reference project should be updated after generator support lands:

- move inbound event contracts under `application.subscribers.integration.inbound`;
- generate the inbound contract and subscriber from design;
- convert handwritten `EventSubscriber<T>` usage to `@EventListener`;
- keep HTTP consume examples unchanged.

## Testing

Unit tests should cover:

- design JSON parsing for valid inbound and outbound integration events;
- rejection of missing role;
- rejection of unsupported role;
- rejection of blank eventName;
- rejection of responseFields on integration events;
- canonical assembly of `IntegrationEventModel`;
- contract planner output paths for inbound and outbound events;
- subscriber planner output only for inbound events;
- templates render expected `@IntegrationEvent` and `@EventListener` code;
- drawing-board extraction includes `integration_event`;
- design-element analysis extracts role and eventName from generated integration event classes.

Functional tests should cover:

- `cap4kPlan` includes integration event artifacts;
- `cap4kGenerate` writes inbound contract and subscriber;
- `cap4kGenerate` writes outbound contract only;
- generated integration event subscriber code compiles;
- analysis output includes `integration_event` design elements.

## Acceptance Criteria

The feature is complete when:

- `integration_event` is a supported design JSON tag;
- integration event role is event-level and required;
- generated packages separate inbound and outbound event contracts;
- inbound events generate `@EventListener` subscriber skeletons;
- outbound events do not generate subscribers;
- subscriber identity is not hand-written in design JSON;
- canonical assembly and public Gradle DSL expose the new families;
- drawing-board and design-element analysis understand integration events;
- flow analysis remains aligned with generated `@EventListener` subscribers;
- tests prove stable artifact plans and generated Kotlin output.
