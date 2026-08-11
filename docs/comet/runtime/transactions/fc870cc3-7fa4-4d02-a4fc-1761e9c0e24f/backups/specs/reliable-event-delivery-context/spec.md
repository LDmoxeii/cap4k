# Reliable Event Delivery Context

## Public contract

The Runtime shall expose one immutable, transport-neutral
`ReliableEventDeliveryContext` to application Event Handlers. Its complete
public data is a non-blank stable event ID, a non-blank logical event name, the
original reliable publication instant, a nullable positive exact attempt, and a
non-authoritative redelivery hint whose only values are UNKNOWN, FIRST, and
REDELIVERED. The context shall expose no transport topology or endpoint detail
and shall make no deduplication guarantee.

Application code shall access the current value through a nullable accessor and
a strict accessor. The nullable accessor returns null when no applicable
delivery is active. The strict accessor returns the current value or throws a
clear state error; it shall not synthesize a default value.

## Applicable delivery boundaries

The Runtime shall install the context only while dispatching handlers for:

- a persisted or deferred reliable Domain Event;
- an inbound HTTP Integration Event;
- an inbound RabbitMQ Integration Event; and
- an inbound RocketMQ Integration Event.

An ordinary synchronous Domain Event shall have no reliable-delivery context.
This remains true when such an event is synchronously raised from a handler that
does have a reliable-delivery context: the nested ordinary dispatch suppresses
the ambient value and restores it only after the nested dispatch has completely
finished.

Installation shall wrap handler dispatch, not route selection, subscriber
lookup, interceptor bookkeeping, publisher selection, or reliable-record state
transitions. Existing sequential, ordered, fail-fast handler behavior remains
unchanged.

## Lifetime and propagation

The context shall be represented as local immutable ExecutionContext state with
no registered transport codec. The current snapshot shall therefore propagate
to the scoped asynchronous Query and Capability work captured by PR #158 while
remaining absent from serialized generic ExecutionContext envelopes.

The delivery scope shall remain installed until every handler invocation and all
managed asynchronous Query/Capability stages owned by those invocations finish.
It shall close deterministically after success, synchronous or asynchronous
failure, Spring condition skip, or surrounding interceptor failure. Cleanup
shall be LIFO-safe, shall restore an outer scope when nested, and shall not leak
to a later delivery on the same thread or executor worker.

## Metadata ownership

Reliable registration shall capture one immutable `publishedAt` instant. The
persisted Domain Event record shall own it independently of scheduled delivery
time, and retries or later message materialization shall not replace it. Outbound
Integration Event transports shall carry that same instant in the existing
Cap4k timestamp header using one strict epoch-millisecond representation. Missing
or malformed required metadata shall fail before handler dispatch; no current-
time or scheduled-time fallback is allowed.

Persisted/deferred Domain Event delivery shall expose the JPA reliable record's
owned positive attempt counter as exact. FIRST means exact attempt 1 and
REDELIVERED means exact attempt greater than 1.

For inbound HTTP, RabbitMQ, and RocketMQ, `eventName` shall be the canonical
envelope's stable logical Integration Event name, which is the unique
`@IntegrationEvent.value` resolved by the local catalog. It shall not be a
payload simple class name and shall not be derived from a provider route, topic,
endpoint, queue, consumer group, or subscriber-registry key.

Inbound HTTP shall expose no exact attempt and UNKNOWN because the transport has
no authoritative delivery counter or redelivery signal.

Inbound RabbitMQ shall expose no exact attempt. Its broker redelivered flag may
produce FIRST or REDELIVERED only as a non-authoritative hint.

Inbound RocketMQ shall expose `reconsumeTimes + 1` as the exact positive attempt.
Zero reconsumes produces FIRST and a positive reconsume count produces
REDELIVERED.

## Explicit exclusions

This capability shall not expose exchange, routing key, topic, queue, consumer
group, HTTP URL, transport message objects, subscriber registry objects, or
subscriber identity, publisher configuration. It shall not add authoritative inbox/deduplication
state or claim exactly-once handling.

This change shall not modify Integration Event routes, attach/detach behavior,
publisher selection, the reliable-record state machine, archive policy, or the
HTTP subscriber registry. It shall not include aliases, deprecated APIs, dual
implementations, fallback timestamp codecs, compatibility bridges, or repairs
for unrelated Runtime audit findings.

## Verification contract

Focused owner tests shall prove all four applicable boundaries, ordinary
synchronous Domain Event suppression, exact/null attempt ownership, each hint,
strict and nullable access, local async propagation, and cleanup after success,
failure, condition skip, and repeated same-thread delivery. Wire tests shall
prove stable event ID/name/publication time and strict rejection of missing or
malformed required metadata.

Repository evidence shall include the Runtime stale-surface script, PR workflow
guard tests, buildSrc tests, the required repository `check`, static forbidden-
surface searches, and `git diff --check`.
