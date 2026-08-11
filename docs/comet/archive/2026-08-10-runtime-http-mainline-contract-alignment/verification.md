# Acceptance evidence

<!-- comet-native:acceptance-evidence:start -->
[
  {
    "acceptance_id": "acceptance-037303764f5daa717cac758ba988bc76cd842f72f740899ae904f11ddf5b49e0",
    "evidence_refs": [
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventPublisherTest.kt",
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventRealClientTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-1e880c73e9a302e51de4836223e548d62846c1b77b0d4f4720034d39cbd42f60",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventAutoConfigurationTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-5832009c4f05b42d88706fdbf846816f55f6fd4cb7f530ef934b536beaed058c",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventSelfRouteTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-9c99dd12eca1188b1669645d4014a81fb39349aaa34151d6e42eb0e9c3fdc953",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventConsumeHandlerTest.kt",
      "ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/event/ReliableEventDeliveryContext.kt",
      "ddd-integration-event-http/src/main/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventSubscriberAdapter.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-add2400341f0a09e09e5959b55722084e64a33c6086a0ac38879ef01ef24da03",
    "evidence_refs": [
      "ddd-integration-event-http/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventRouteInterceptorTest.kt"
    ]
  },
  {
    "acceptance_id": "acceptance-c0432f7eee237d280a5c40afebfd574c8b771275bb68d4ff41ac62ce61267763",
    "evidence_refs": [
      "cap4k-ddd-integration-event-http-starter/src/test/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventAutoConfigurationTest.kt"
    ]
  }
]
<!-- comet-native:acceptance-evidence:end -->

# Commands and results

- `.\gradlew.bat :ddd-integration-event-http:test :cap4k-ddd-integration-event-http-starter:test :cap4k-ddd-domain-event-jpa-starter:test --no-daemon` — passed in 1m 30s; 48 actionable tasks. This covered HTTP publisher/receiver behavior, real client failures and timeouts, provider lifecycle, real self-routing with Handler-scoped async Query completion, and JPA transport composition.
- `.\gradlew.bat check --stacktrace --no-daemon` — passed in 8m 6s; 211 actionable tasks, 4 executed, 54 from cache, and 153 up-to-date.
- `comet native check runtime-http-mainline-contract-alignment --json` — passed; receipt `runtime/evidence/check-receipts/67679c6ce7da3d7951b56232767f3dab54422f4689c842717d0837923cc73979.json`, 44 files scanned, 0 issues, and no stale reasons.
- Targeted `rg` scans passed with no forbidden matches for the removed parallel Provider State package, Delivery Context topology, dynamic subscriber/JPA management, legacy route syntax, the old HTTP prefix, or raw payload logging.

# Skipped checks

- The existing real PostgreSQL soft-delete integration test was skipped because PostgreSQL was unavailable; it is unrelated to HTTP transport.
- No external RabbitMQ or RocketMQ broker test was run for this HTTP change. Their ordinary module tests and the repository-wide build passed.

# Spec consistency

- HTTP uses the mainline `RuntimeProviderStateRegistry`, starts at `RECOVERING/enrolled`, reports observed handoff states, and unregisters on Spring shutdown.
- The PR-local Provider State implementation and `UNKNOWN` compatibility state are absent.
- Delivery Context contains only transport-neutral facts; subscriber identity, application name, URL, endpoint, route, queue, group, and topology are absent.
- Static routes, fixed POST, pre-persistence route validation, self-routing, timeouts, 2xx-only success, safe failures, executor ownership, context cleanup, and duplicate behavior match the target spec.

# Known limitations and risks

- HTTP remains an experience transport without broker durability, inbox/deduplication, broadcast, discovery, or downstream acknowledgement collection.
- A process loss after receiver success but before durable sender acknowledgement may duplicate delivery under at-least-once semantics.
- HTTP 2xx proves completion of the local Handler scope, not any later external side effect.
- The Comet scope includes PR #180 Core/RabbitMQ/RocketMQ files inherited during rebase so that scope remains complete; these are not HTTP-authored behavior.

# Conclusion

PASS. PR #179 satisfies the confirmed HTTP experience contract on top of PR #180 with focused, static, repository-wide, and Comet evidence.
