# Implement Subscriber Or Internal Trigger

1. Run the skeleton gate before writing code.
2. Stop and return to `cap4k-generation` when the subscriber, inbound integration event, domain-event shell, command, query, client, validator, or payload surface is generator-capable but missing.
3. Stop and return to `cap4k-modeling` when the missing piece is the design entry, DDL contract, enum manifest entry, `types.registryFile` entry, or KSP metadata that generation needs.
4. Classify the entry as domain-event subscriber, external fact entry, or internal trigger.
5. Use subscribers or jobs as routing points, not hidden aggregate persistence layers.
6. Inspect `build/cap4k/plan.json` before editing generated subscriber shells.
7. Route state changes to command.
8. Route reads to query.
9. Keep external protocol payloads out of aggregate behavior and domain events.

## Listener Organization

- Cap4k does not guarantee ordering between multiple listeners or listener methods for the same event.
- Represent independent reactions as independent `@EventListener` methods.
- Strongly discourage a public `on(event)` method that manually dispatches to multiple business reaction methods.
- Give each listener method a business-semantic name and one reaction.
- Use private helpers only for shared technical concerns, not for hiding a business dispatch table.
- Listener-side checks are routing filters only. The called command must still validate every write precondition.
- Do not directly write repositories or mutate aggregates from listeners or jobs. Route writes through commands.

Approved shape: independent listener methods.

```kotlin
@Service
class CustomerCoinGivenDomainEventSubscriber {

    @EventListener(CustomerCoinGivenDomainEvent::class)
    fun transferCoin(event: CustomerCoinGivenDomainEvent) {
        val action = event.entity
        Mediator.commands.send(
            TransferCoinCmd.Request(
                senderUserId = action.customerId,
                receiverUserId = action.videoOwnerId,
                amount = action.actionCount
            )
        )
    }

    @EventListener(CustomerCoinGivenDomainEvent::class)
    fun applyVideoCoinCountDelta(event: CustomerCoinGivenDomainEvent) {
        val action = event.entity
        Mediator.commands.send(
            ApplyVideoCoinCountDeltaCmd.Request(
                videoId = action.videoId,
                delta = action.actionCount
            )
        )
    }
}
```

Discouraged shape: public `on(event)` manual dispatch.

```kotlin
@Service
class CustomerCoinGivenDomainEventSubscriber {

    @EventListener(CustomerCoinGivenDomainEvent::class)
    fun on(event: CustomerCoinGivenDomainEvent) {
        transferCoin(event)
        applyVideoCoinCountDelta(event)
    }

    private fun transferCoin(event: CustomerCoinGivenDomainEvent) {
        // Hidden business reaction.
    }

    private fun applyVideoCoinCountDelta(event: CustomerCoinGivenDomainEvent) {
        // Hidden business reaction.
    }
}
```

This shape hides multiple business reactions behind one listener method and can mislead reviewers into assuming ordering or transactional coupling.
