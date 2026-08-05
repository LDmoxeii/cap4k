# Outcome

Agent snapshot generation fails before writing a manifest when a provider supplies a descriptor for an explicitly retired Runtime capability. Current-runtime-facts validation also checks the descriptor implementation surface, so Console, Snowflake, Locker, and Saga cannot reappear as active Agent capabilities through drift.

# Scope

- Define one shared retired Runtime descriptor policy for Console, Snowflake, Locker, and Saga.
- Apply the policy at the shared Agent snapshot assembly boundary used by Gradle and future adapters.
- Cover exact retired capability/provider identities with focused unit tests.
- Extend current Runtime facts validation so descriptor-source drift is checked alongside documentation and skills.

# Non-goals

- Do not delete remaining Console, Snowflake, Locker, or Saga Runtime production code in this slice; their retirement/cleanup slices own those changes.
- Do not invent the final Runtime provider registry, live provider observations, Actuator endpoint, or `NOT_PERFORMED` status model.
- Do not change ordinary Pipeline source/generator descriptor identities or duplicate-provider behavior.
- Do not infer business intent, domain correctness, or successful execution from descriptor absence.

# Acceptance examples

- Given a descriptor with retired Runtime capability identity `runtime.snowflake`, snapshot assembly rejects it and no supported/effective placeholder is emitted.
- Given a descriptor whose provider identity is exactly `console`, `snowflake`, `locker`, or `saga`, snapshot assembly rejects it even if the capability identity is disguised under another namespace.
- Given a surviving built-in or extension descriptor, snapshot assembly preserves the existing projection and status behavior.
- The current-runtime-facts check fails if a retired identifier is reintroduced in an active descriptor declaration outside the single policy definition and its focused tests.

# Constraints and invariants

- Absence means retirement only for the four explicitly retired identities; absence of any other capability is not success evidence.
- Retired descriptors are rejected, not silently filtered and not converted to `UNKNOWN`, `NOT_APPLICABLE`, or another placeholder.
- Matching is based on normalized exact identity segments, not arbitrary substring matching, to avoid rejecting unrelated words.
- The policy must live below the Gradle adapter so every snapshot adapter shares the same boundary.
- Runtime registry agreement is not claimed until the final Runtime Agent API facts slice provides the active provider registry.

# Decisions

- Use the published `runtime-agent-retired-descriptors` and `runtime-agent-api-facts` contracts as the product contract; no new product decision is introduced.
- Enforce retirement in `AgentSnapshotService` before observation normalization or manifest encoding.
- Keep this slice fail-fast and do not emit diagnostics or partial snapshot files for a programmer-supplied retired descriptor.
- Preserve the current status enum and defer `NOT_PERFORMED`/live registry design to `runtime-agent-api-facts`.
- The user confirmed this scoped Build boundary on 2026-08-05.

# Open questions

None.

# Verification expectations

- Run focused `cap4k-plugin-pipeline-agent` tests covering all retired identities and surviving descriptors.
- Run Gradle compilation/tests for the affected Agent/Gradle boundary as practical.
- Run `scripts/validate-current-runtime-facts.ps1` and inspect the final diff for retired descriptor leakage.
