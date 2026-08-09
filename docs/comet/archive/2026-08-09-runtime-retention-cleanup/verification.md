# Commands and results

- `./gradlew :ddd-core:compileKotlin :ddd-application-command-jpa:compileKotlin :ddd-domain-event-jpa:compileKotlin`
  — passed.
- `./gradlew :cap4k-ddd-command-jpa-starter:test --tests 'com.only4.cap4k.ddd.starter.command.JpaCommandAtomicClaimIntegrationTest' --tests 'com.only4.cap4k.ddd.starter.command.JpaCommandRetentionCleanupIntegrationTest' :cap4k-ddd-domain-event-jpa-starter:test --tests 'com.only4.cap4k.ddd.starter.event.JpaEventAtomicClaimIntegrationTest' --tests 'com.only4.cap4k.ddd.starter.event.JpaEventRetentionCleanupIntegrationTest'`
  — passed; 42 focused Command/Event JPA tests completed successfully.
- `comet native check runtime-retention-cleanup`
  — passed; 11 scoped files scanned, 0 text-safety issues.

# Acceptance evidence

- Command and Event carriers now persist a nullable `terminalizedAt` separate from `expireAt`.
- Success acknowledgement, terminal expiry, and terminal exhaustion write the terminal timestamp
  inside their existing conditional transition.
- Cleanup selects only service-matching `EXECUTED`/`DELIVERED`, `EXHAUSTED`, and `EXPIRED` records
  with a null lease and state-specific cutoff, then repeats those predicates in the final delete.
- The H2/JPA tests prove bounded deletion, safe aggregate counts, repeated-call idempotency,
  service isolation, live-lease exclusion, retryable/cancelled retention, terminal timestamp
  population, and concurrent state-change fencing.
- No external MySQL/PostgreSQL server was available; production-database-specific execution was
  not run. The generated H2 schema and the checked-in SQL/JPA column alignment were verified.

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-0265dcfc3ff0b0aa3d6179f57ce1348aac01cd619d4ad7941fccbc3b59ab3116",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-0ba16e15d1d255a75ae13666e13b9d179edcc79170d3ff94195d41efb3cc9afb",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-137b23c412ec8126a275edd14c6e5e2e799ca7a52fae7f8fa1d1c4a3d6ccd75c",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-55ac53175a9f331aeb01ea5d693f1d671ad9e7f8051de5663fcce80b4d96b6b2",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-699df4e4b00b6126ebe5f886c2a90004a6e937614c0808b33c1487f9d85756ce",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-7500558e62519b003f2e613b9047b2e437e33e9b11104082f46324f474aa0322",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-8472b12a05e34270287f227144f05b6360c033dbe853f0e9be70b15dde37b698",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-b3c1515be2cf7964c63f99130403bfdbdbaa46391115af994c5b1c49b98ab6f5",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-c6398be1671885a5f13811c4ed9c6ac403681dfe7744c532923b9900e7f4cdf0",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-db36f73b990102f717411605b635bd617aa1cb95d36a7aec8b4e526273922a60",
    "evidence_refs": [
      "cap4k-ddd-command-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/command/JpaCommandRetentionCleanupIntegrationTest.kt",
      "cap4k-ddd-domain-event-jpa-starter/src/test/kotlin/com/only4/cap4k/ddd/starter/event/JpaEventRetentionCleanupIntegrationTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Spec consistency

The implementation matches the complete `runtime-retention-cleanup` target specification: it owns
only successful, exhausted, and expired terminal states; keeps `expireAt` as the execution deadline;
rechecks service/state/lease/cutoff conditions at delete time; returns bounded safe counts; and does
not add scheduling, redrive, transport, archive, or generic task APIs.

# Skipped checks

- No live MySQL or PostgreSQL instance was available, so vendor-specific DDL and locking behavior
  were not executed.
- Automatic cleanup scheduling remains intentionally unimplemented in this explicit-invocation slice.

# Known limitations and risks

- Existing terminal rows created before this breaking change have a null `terminalizedAt` and are
  therefore retained until a future migration or explicit policy handles them.
- Cleanup is exposed through the private JPA execution substrates; no public scheduler or operator
  endpoint is added yet.

# Conclusion

Pass for the bounded Runtime/JPA retention contract. Automatic scheduling, manual redrive, and
external-database execution remain intentionally outside this slice.
