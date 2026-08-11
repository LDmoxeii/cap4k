# Outcome

Make Runtime provider association diagnostics and all encoded Agent snapshot outputs independent of
capability/provider input order when duplicate normalized provider identities point at different
capabilities.

# Scope

- Replace last-write-wins provider association in `RuntimeAgentFactsPolicy` with full-candidate,
  normalized identity validation.
- Emit stable diagnostics for mixed duplicate providers and capability provider references.
- Make an invalid Runtime section reason explicitly identify static catalog inconsistency while
  retaining any original Runtime reason as supplemental context.
- Add two-order service and codec evidence covering diagnostics, counts, section reason, four encoded
  files, and snapshot hash.
- Refresh the PR #183 Comet verification and rerun all requested checks.

# Non-goals

- No capability/provider catalog, Agent contract v3, static status, live registry, retirement,
  transport, Runtime state-machine, Repository, Analyzer, or Actuator decision changes.
- No sorting-plus-`associateBy` workaround and no compatibility layer.

# Acceptance examples

- Two provider facts normalize to `provider.shared`, one targets `runtime.a`, and one targets
  `runtime.b`; a capability references `provider.shared`. Both input orders produce `INVALID`.
- Reversing capabilities and providers produces identical diagnostic IDs/messages, diagnostic counts,
  Runtime section reason, `runtime.json`, `diagnostics.json`, `manifest.json`, and snapshot hash.
- Existing duplicate providers that target the same capability remain rejected with their current
  deterministic duplicate diagnostic.
- An existing unrelated Runtime reason cannot hide the static catalog consistency failure.

# Constraints and invariants

- Validate association from the complete normalized provider candidate set.
- Diagnostic semantics and identities must not depend on encounter order.
- Preserve all already-reviewed Runtime Agent API facts and boundaries from PR #183.

# Decisions

- A capability/provider reference is valid only when the normalized provider identity resolves to a
  non-empty candidate set and every candidate targets that capability's normalized identity.
- Mixed duplicate candidates therefore produce both the duplicate-provider fact diagnostic and a
  stable capability-provider mismatch diagnostic for every referencing capability that cannot own all
  candidates.
- The invalid Runtime reason starts with `The static Runtime fact catalog is invalid.` and appends a
  distinct pre-existing reason when present.

# Open questions

None. The user confirmed the bounded deterministic duplicate-provider fix and explicit invalid reason
without reopening any Runtime Agent API product decision.

# Verification expectations

- `RuntimeAgentFactsCatalogTest`, `AgentSnapshotServiceTest`, `AgentSnapshotCodecTest`, and
  `Cap4kAgentSnapshotTaskTest`.
- Mixed-duplicate reversed-order assertions for diagnostics, counts, section reason, encoded files,
  and snapshot hash.
- `scripts/validate-current-runtime-facts.ps1`, `git diff --check`, `./gradlew check`, Comet Native
  check/Verify/Archive, and the required GitHub `check`.