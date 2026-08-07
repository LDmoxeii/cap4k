# Outcome

Close the two remaining PR #170 review blockers by making reliable Command and Event ownership tokens byte-identical at every runtime and database boundary, and by making JPA-generated schema plus the shipped SQL resources express the same token and timestamp contract.

# Scope

- Replace the raw textual ownership token surface with one immutable private token value whose identity is the original 32 ASCII bytes.
- Persist ownership tokens as `VARBINARY(32)` for both Command and Event carriers and bind all claim, renew, acknowledge, and failure/retry CAS operations as bytes.
- Preserve the current lowercase UUID-hex token generator while preventing normalization, mutable-array aliasing, or text-collation comparison from weakening ownership fencing.
- Express millisecond precision for every runtime-owned Command/Event timestamp in both JPA metadata and `command.sql` / `event.sql`.
- Strengthen integration evidence so Hibernate-created schema metadata, production SQL declarations, token format behavior, and token-bound writes are checked for both record types.
- Correct verification reporting: H2/MySQL mode is executable baseline evidence, while behavior that requires a real MySQL engine remains explicitly unclaimed.

# Non-goals

- No change to claim eligibility, retry budget, lease expiry, record expiry, safe failure-facts, or retry-policy snapshot semantics already accepted in PR #170.
- No manual redrive, retention/cleanup, Integration Event envelope/transport, partition-management cleanup, Locker cleanup, or public task framework.
- No compatibility layer for the private `String` token representation.
- No edit to any archived Comet brief, spec, verification report, receipt, or evidence object.
- No merge of PR #170 from this change.

# Acceptance examples

- A Command or Event owner claims with token bytes for `aaaaaaaa...`; a token containing `AAAAAAAA...`, a different final byte, or a different length cannot renew, acknowledge, or transition failure and changes zero durable fields.
- Copying or mutating a byte array obtained from the token cannot mutate token identity or alter its equality/hash behavior.
- Hibernate `create-drop` creates a binary 32-byte `delivery_token` column and millisecond-capable runtime timestamp columns for both carriers.
- The shipped `command.sql` and `event.sql` declare the same binary token width and `datetime(3)` runtime timestamps as the JPA carriers.
- Existing concurrent claim, stale-owner fencing, lease renewal/recovery, terminal/cancelled rejection, retry snapshot, safe failure-facts, and rollback tests continue to pass symmetrically for Command and Event.

# Constraints and invariants

- Ownership identity is exact byte sequence equality. It MUST NOT depend on collation, padding, case folding, trimming, Unicode normalization, or application-side canonicalization.
- The ownership token remains opaque private Runtime infrastructure and MUST NOT become a public scheduling or delivery API.
- Token-bound repository updates accept the same bytes that were persisted by claim; every mismatch has zero write effect.
- Command and Event remain separate JPA projections but MUST use one shared token type/codec contract and the same schema semantics.
- Runtime timestamps continue to be normalized to milliseconds and their persistence mappings MUST not silently round to seconds.
- Safe failure facts and persisted retry-policy snapshots remain the only failure/retry inputs; payloads and stack traces stay excluded.

# Decisions

- Use an immutable `JpaOwnershipToken` wrapper with defensive copies and content-based equality/hash code.
- Keep the generated token human-diagnosable as 32 lowercase hexadecimal ASCII characters; parsing/fixture construction may preserve upper-case hexadecimal bytes so case-only mismatch can be proven without normalization.
- Use `ByteArray` only at the private JPA carrier/repository binding boundary, never as the ownership value object itself.
- Use `VARBINARY(32)`, not `VARCHAR ... ascii_bin` or fixed-width `BINARY`, so byte content and length are both part of equality without collation or zero-padding behavior.
- Declare runtime-owned temporal columns with millisecond precision in JPA metadata and production MySQL SQL resources.
- Use H2 in MySQL mode for executable JPA behavior and schema-metadata assertions. Do not claim real-MySQL collation/dialect execution unless such an engine is actually run.

# Open questions

- No unresolved product questions. The user confirmed continuation of this repair Shape on 2026-08-07.

# Verification expectations

- Add focused shared token value tests for format, round-trip, content equality/hash, defensive copying, and invalid length/characters.
- Add Command and Event JPA integration coverage for case-only and byte-only token mismatch across renew, acknowledge, and failure/retry transitions.
- Query the actual H2/Hibernate-created schema metadata and assert binary token type/width plus millisecond timestamp precision.
- Assert `command.sql` and `event.sql` express the same token and time contract as their carrier mappings.
- Run the shared JPA core, Command JPA, Event JPA, Command starter, and Event starter test tasks.
- Report real MySQL as NOT_PERFORMED unless it is actually available and executed.
