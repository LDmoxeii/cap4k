# Implement Subscriber Or Internal Trigger

1. Classify the entry as domain-event subscriber, external fact entry, or internal trigger.
2. Use subscribers or jobs as routing points, not hidden aggregate persistence layers.
3. Inspect `build/cap4k/plan.json` before editing generated subscriber shells.
4. Route state changes to command.
5. Route reads to query.
6. Keep external protocol payloads out of aggregate behavior and domain events.
