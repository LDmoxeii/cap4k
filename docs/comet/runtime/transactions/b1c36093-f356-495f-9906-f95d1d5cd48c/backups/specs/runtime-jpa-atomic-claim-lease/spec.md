## Requirements

### Requirement: Shared private ownership contract
The runtime MUST provide one private semantic contract for reliable Command and reliable Event execution ownership. Command and Event MAY have different JPA entity/carrier classes and SQL projections, but state transitions, token validation, lease rules, and failure boundaries MUST have the same meaning.

#### Scenario: Same ownership semantics
- **WHEN** a worker claims a Command or Event record
- **THEN** both record types enter their existing in-flight state, receive an opaque ownership token, and record a lease expiry under the same rules

### Requirement: Atomic claim
The substrate MUST claim an eligible record within one database transaction/statement boundary. A ready record is eligible only when it matches the service/consumer identity, is non-terminal/non-cancelled, its retry time is due, its record expiry is still in the future, and its lease is absent or expired. An in-flight record whose lease expired is also eligible for immediate worker-loss recovery; its previous retry timestamp does not delay re-claim. A concurrent claim MUST succeed for at most one worker.

#### Scenario: Concurrent claimers
- **WHEN** two workers attempt to claim the same due record at the same time
- **THEN** exactly one claim commits and returns ownership, while the other returns no claim and performs no partial write

#### Scenario: Expired lease recovery
- **WHEN** a record has an unexpired lease
- **THEN** another worker cannot claim it
- **WHEN** that lease expires
- **THEN** another worker MAY claim it and receives a new token and lease

#### Scenario: Terminal or cancelled record
- **WHEN** a record is terminal or cancelled
- **THEN** claim is rejected even if its retry time or lease is expired, and no state, token, lease, or attempt fields change

### Requirement: Opaque delivery token
Each successful claim MUST return a non-empty, unguessable/opaque token associated with the claimed row and lease owner. The token MUST be persisted with the claim so that a caller cannot forge ownership from a row id or record UUID alone.

#### Scenario: Token mismatch
- **WHEN** renew, acknowledge, or failure/retry transition is invoked with a token different from the currently persisted token
- **THEN** the operation is rejected and has zero durable write effect

### Requirement: Token-bound lease renewal
The owner MUST be able to renew an unexpired lease by presenting the current token. Renewal MUST extend only that token's lease, MUST reject token mismatch, and MUST reject terminal/cancelled records. Renewal after the current lease expiry MUST be rejected; expiry is recovered through a new claim.

#### Scenario: Renewal before expiry
- **WHEN** the current token renews before lease expiry
- **THEN** the lease expiry is extended and the token remains unchanged

#### Scenario: Renewal after expiry
- **WHEN** the current token renews after lease expiry
- **THEN** renewal is rejected and the expired row remains eligible for a future claim

### Requirement: Token-bound completion and retry transitions
Acknowledgement and failure/retry transitions MUST verify the current token and lease ownership before writing. Successful acknowledgement MUST enter the existing terminal success state. Failure MUST persist safe structured `failure_facts`, use the retry-policy snapshot stored on the record, and enter the existing retryable or terminal failure state according to that snapshot and record expiry. No operation may persist business payload or an exception stack trace as failure data.

#### Scenario: Successful acknowledgement
- **WHEN** the owner acknowledges with the current token before lease expiry
- **THEN** the record enters `EXECUTED` (Command) or `DELIVERED` (Event), clears/invalidates ownership as defined by the carrier, and cannot be claimed again

#### Scenario: Retryable failure
- **WHEN** the owner records a retryable failure with the current token and retry budget/expiry remain
- **THEN** the record enters the existing exception/retryable state, stores safe failure facts, calculates the next attempt from the persisted retry-policy snapshot, and becomes claimable only when due

#### Scenario: Terminal failure
- **WHEN** the owner records failure after retry budget or record expiry is exhausted
- **THEN** the record enters the existing terminal failure state with terminal safe failure facts and cannot be claimed again

### Requirement: Atomic durable writes
Claim, token, lease, state, and attempt metadata MUST commit atomically. A transaction that fails before commit MUST NOT expose a token or partial state transition. Token-bound transitions MUST use a conditional update/CAS or equivalent database-level guard and MUST report whether exactly one row changed.

#### Scenario: Failed transaction rollback
- **WHEN** a claim or token-bound transition raises a persistence failure before commit
- **THEN** a subsequent transaction observes the complete prior durable record, with no new token, lease, state, attempt, or failure-facts fragment

### Requirement: Retry and failure data boundaries
The substrate MUST continue using the immutable retry-policy snapshot captured when each record was created. It MUST continue using the existing safe `failure_facts` representation and MUST NOT reconstruct policy from current annotations/configuration or write raw business payload/exception stack traces into failure storage.

#### Scenario: Snapshot stability
- **WHEN** current retry annotations/configuration change after a record is persisted
- **THEN** the next retry transition uses the record's original `retry_policy` snapshot

### Requirement: Real JPA integration verification
The change MUST include real JPA integration tests, not only mocks or in-memory concurrency tests. Tests MUST run separate transactions for competing workers and cover both Command and Event carriers.

#### Scenario: Required integration matrix
- **WHEN** the verification suite runs
- **THEN** it covers concurrent claimers, token mismatch, renewal before/after expiry, worker/process-loss re-claim, terminal/cancelled rejection, atomic rollback/write visibility, retry snapshot stability, and SQL/entity field alignment for both record types

### Requirement: Scope boundaries
This change MUST remain private runtime infrastructure. It MUST NOT add public Scheduler/Job/Task APIs, manual redrive, retention/cleanup, Integration Event envelope/transport, or broad legacy Locker/scheduling removal.

#### Scenario: Downstream integration remains separate
- **WHEN** this change is complete
- **THEN** downstream Command/Event state-machine and transport changes can consume the substrate without requiring this change to implement their own competing ownership semantics
