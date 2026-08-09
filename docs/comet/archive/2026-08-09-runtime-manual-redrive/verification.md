# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-1f6b4aeaab2609f6730928906a15131ae038cd74636e0eaa11ef4b71ee28b05e",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandAtomicClaimIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventAtomicClaimIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-2b535d4137120a31b55502cc63938c68329123f111aaf44d8ca1b5fd052d57ae",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandAtomicClaimIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventAtomicClaimIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-92253d190ad747a7da3fbe69d2d8d530b76f1a9021e3bea927bc3fd6d041911b",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandAtomicClaimIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventAtomicClaimIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-a036279ce6278f86f35ac4b54d57a9b5ee46575f9c26654d3a0b7dba1d675b6a",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandAtomicClaimIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventAtomicClaimIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-a0e1b9494231f8df468b29ac09efc897c1328649b1498e8e7ad58b489685f9a5",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandAtomicClaimIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventAtomicClaimIntegrationTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results



- `./gradlew :ddd-application-command-jpa:test --tests 'com.only4.cap4k.ddd.application.command.JpaReliableCommandWorkerTest' :ddd-domain-event-jpa:test --tests 'com.only4.cap4k.ddd.domain.event.JpaEventScheduleServiceTest' --no-daemon` — passed.
- `./gradlew :cap4k-ddd-command-jpa-starter:test --tests 'com.only4.cap4k.ddd.starter.command.JpaCommandAtomicClaimIntegrationTest' :cap4k-ddd-domain-event-jpa-starter:test --tests 'com.only4.cap4k.ddd.starter.event.JpaEventAtomicClaimIntegrationTest' --rerun-tasks --no-daemon` — passed.
- `git diff --check` — passed; Git reported only the repository's existing LF/CRLF normalization notices.
- `comet native check runtime-manual-redrive` — run after this report was written; result is recorded by the Comet receipt supplied to Verify.

# Skipped checks

- None.

# Spec consistency

- Command and Event use the same private redrive shape: read current carrier, validate service/state/version/expiry/lease, then issue one token/version-fenced JPA update.
- `EXCEPTION` and `EXHAUSTED` records reset to `INIT`, due immediately, with `triedTimes` reset and delivery ownership cleared.
- Persisted retry-policy snapshots, original expiry, and safe failure facts remain unchanged.
- `EXPIRED`, active leased records, successful records, `CANCEL`, stale state/version, and wrong durable ownership context are rejected without a state reset.
- A durable operator request marker makes an immediate duplicate return `ALREADY_APPLIED`; only a new `REDRIVEN` result wakes the existing worker/coordinator.
- The normal claim, synchronous execution/provider completion, token-fenced acknowledgement, and failure transitions remain the only execution path.
- SQL/schema parity tests cover the new `redrive_request_token` carrier column and existing mapped columns.

# Known limitations and risks

- No long-running multi-process soak or production database benchmark was run; this slice is covered by deterministic H2/JPA CAS, lease, token-fencing, retry, and worker/coordinator tests.
- The durable request marker stores the latest accepted operator token on the carrier; the contract only relies on immediate duplicate replay for a request token.

# Conclusion

Manual Redrive satisfies the scoped Runtime contract for a bounded operator-triggered retry. It does not add a generic scheduler, direct handler/publisher path, transport API, or compatibility surface.