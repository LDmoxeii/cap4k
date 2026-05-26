# Implement Subscriber Or Internal Trigger

1. Run the skeleton gate before writing code.
2. Stop and return to `cap4k-generation` when the subscriber shell, inbound integration event, domain-event shell, `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, client, or payload surface is generator-capable but missing, including generated handler surfaces.
3. Stop and return to `cap4k-generation` when DDL, enum, or type-registry facts already exist but the aggregate, repository, factory, specification, enum, or unique-helper skeleton is still missing. If relation or field-mapping behavior seems missing after those facts exist, that is still aggregate/entity generation drift, not a standalone skeleton plan item, and must return to `cap4k-generation`.
4. Stop and return to `cap4k-modeling` when the missing piece is the design entry, DDL contract, enum manifest entry, or `types.registryFile` entry.
5. Stop and return to `cap4k-generation` when generation is blocked by missing KSP metadata output/config/setup that the subscriber shell needs.
6. Classify the entry as domain-event subscriber, external fact entry, or internal trigger.
7. Use subscribers or jobs as routing points, not hidden aggregate persistence layers.
8. Inspect `build/cap4k/plan.json` before editing generated subscriber shells.
9. Route state changes to command.
10. Route reads to query.
11. Keep external protocol payloads out of aggregate behavior and domain events.

## Listener Organization

- Cap4k does not guarantee ordering between multiple listeners or listener methods for the same event.
- Represent independent reactions as independent `@EventListener` methods.
- Strongly discourage a public `on(event)` method that manually dispatches to multiple business reaction methods.
- Give each listener method a business-semantic name and one reaction.
- Use private helpers only for shared technical concerns, not for hiding a business dispatch table.
- Listener-side checks are routing filters only. The called command must still validate every write precondition.
- Listener-side filters may use event snapshot data to avoid obvious ghost work, but they must not replace command-side validation.
- For expected inapplicable, non-ready, or already-applied paths, the command should return an explicit no-op outcome such as `NotPaidContent`, `NotPublicationReady`, `AlreadyStarted`, or `AlreadyPublished`.
- Add diagnostics or audit context that makes the outcome visible: listener method, command request, applied/no-op result, and retreat reason.
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
