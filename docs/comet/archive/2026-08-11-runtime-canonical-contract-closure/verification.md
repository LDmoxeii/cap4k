# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-3e7d5440132aee13775cb91b54bf599e451e69a5b77b4080667a53fc78ed9d22",
    "evidence_refs": [
      "ddd-application-command-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/command/persistence/CommandRetryPolicySnapshotTest.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/share/retry/ReliableRetryPolicySnapshot.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/share/retry/ReliableRetryPolicySnapshotTest.kt",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-45793ae4a3390a072e343287095bde7fceef14c64e559460e583112c6869f6a3",
    "evidence_refs": [
      "cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsCatalog.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/provider/RuntimeProviderState.kt",
      "ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/application/provider/RuntimeProviderStateTest.kt",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-d5d3f625eff462a1271c3100eee5a30b98e8e2cc7ebb028dd7c31306baec99b3",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventSupervisor.kt",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-df1734857d71bd365bc4f50f227d38cf5abc006f2e1430fd0f30dec0d4b8c94f",
    "evidence_refs": [
      "scripts/validate-current-runtime-facts.ps1"
    ]
  },
  {
    "acceptance_id": "acceptance-f44b6af6ea79d486c838a4ae97e5e5396fff7e0beff87369b9ad65fa2e09aaff",
    "evidence_refs": [
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventSupervisor.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/DomainEventSupervisor.kt",
      "ddd-integration-event-rabbitmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventRouteInterceptor.kt",
      "ddd-integration-event-rabbitmq/src/test/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventRouteInterceptorTest.kt",
      "scripts/validate-current-runtime-facts.ps1"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `comet native check runtime-canonical-contract-closure` passed with zero findings; receipt `runtime/evidence/check-receipts/2663bd23114b84369bbc96f262ad3faf33259f6bada5e39e37e53ca36a74ed2a.json`.
- `pwsh -NoProfile -File scripts/validate-current-runtime-facts.ps1` passed against the selected complete proposed specs and the current Runtime source/KDoc.
- `./gradlew.bat :ddd-core:test` passed in 32s; 14 actionable tasks, 5 executed and 9 from cache.
- `./gradlew.bat check` passed in 7m 53s; 197 actionable tasks, 57 executed, 126 from cache, and 14 up-to-date.
- `git diff --check` passed; Git emitted only the repository line-ending conversion warning for the PowerShell script.

# Skipped checks

The following operational evidence remains `NOT_PERFORMED` and is not represented as passing:

- real MySQL and PostgreSQL claim/lease/renewal/retention execution;
- live RabbitMQ broker confirmation, acknowledgement, reconnect, redelivery, and failure behavior;
- live RocketMQ nameserver/broker send-result, consumption, reconnect, and retry behavior;
- multi-process long-running claim/lease soak;
- real process crash and lease reclaim;
- external side-effect success followed by process loss before durable acknowledgement.

The repository's environment-guarded real PostgreSQL soft-delete fixture was skipped during the full
Gradle check. It is a pipeline fixture and is not evidence for Runtime reliable claim/lease behavior.
No Actuator endpoint was exercised because the current contract explicitly has no such endpoint.

# Spec consistency

- The roadmap now records Batches 1-4, Surface Cleanup, Repository Contract, and Runtime Agent API
  facts as completed through PR #183. It does not authorize additional Runtime behavior work.
- The Jackson-only spec distinguishes PR #164's codec-only scope from PRs #177/#179, which later
  retired the HTTP subscriber registry. Current Runtime has no such registry.
- Reliable delivery context wording now separates Domain Event/UoW `attach/detach`, public
  Integration Event `enqueue/schedule/delay`, and transport-internal attachment/pre-persist hooks.
- The UoW contract preserves origin context across persistence, claim, retry, redrive, and terminal
  transition without referring to a current archive path.
- The transport and roadmap contracts agree with `runtime-agent-api-facts`: the live registry exists,
  no Actuator endpoint currently exists, and a future optional projection cannot become a second
  state source.
- The retry snapshot contract records version `1`, `ANY_EXCEPTION`, annotation overrides, the
  1/5/10-minute default delay curve, final custom-interval repetition, and distinct Command/Event
  fallback limits.
- `IntegrationEventSupervisor` KDoc describes reliable registration, scheduling, and delayed
  publication. No method signature or Runtime implementation changed.

# Known limitations and risks

- The Runtime facts guard is a deterministic textual contract guard. It catches the known stale
  wordings and required current facts, but it is not a substitute for semantic review of newly
  invented terminology.
- While a Native change is selected, the guard reads that change's complete proposed spec for a
  capability; otherwise it reads the canonical spec. This allows Verify to validate the intended
  target before Archive and automatically returns to canonical facts afterward.
- External database, broker, multi-process, process-crash, and acknowledgement-window evidence is
  still absent. These are later real-project/provider verification items, not hidden passes and not
  new Runtime implementation gaps.

# Conclusion

PASS. All five acceptance examples have project-relative evidence. The proposed specs express the
confirmed final Runtime contracts, the KDoc and drift guard are the only implementation artifacts,
and focused plus full-repository checks pass. External operational evidence remains explicitly
`NOT_PERFORMED` and does not block the independent Analyzer capability audit after this canonical
closure is archived and merged.
