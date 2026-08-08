# Outcome

Make reliable Command execution use the already-landed private JPA ownership substrate as its
only persisted execution path. A scheduled Command is persisted once, claimed atomically by one
worker, executed through the existing synchronous command supervisor, and acknowledged or failed
with the claim token. No result polling, archive, distributed Locker, or legacy retry scheduler
remains in the Command runtime.

# Scope

- Connect `Mediator.commands.enqueue`, `schedule`, and `delay` to the reliable Command record
  creation path while keeping `Mediator.commands.send` synchronous.
- Replace the old local `CommandRecord` begin/save/retry loop with substrate claim, lease fencing,
  safe failure transition, and token-bound acknowledgement.
- Keep Command handler completion synchronous; only scheduling is detached from the source call
  stack.
- Remove the public `CommandManager` retry/scan surface and its provider slot.
- Remove Command's `Locker`-based `getByNextTryTime` polling and scheduled retry wiring.
- Preserve persisted execution context and retry-policy snapshots, and keep source transaction
  rollback atomic with record creation.

# Non-goals

- No changes to Domain Event or Integration Event delivery in this change.
- No generic task/job framework, public polling/result API, manual redrive API, or transport-specific
  Command state.
- No new Command handler contract and no change to synchronous `CommandSupervisor.send` semantics.
- No broad deletion of the shared Locker module while Event runtime still owns its migration.
- No full retention/cleanup worker beyond the substrate's existing terminal-state decisions.

# Acceptance examples

- A scheduled Command is initially claimable and exactly one concurrent worker receives ownership.
- A successful handler produces one token-bound acknowledgement and `EXECUTED` state.
- A retryable failure records only safe failure facts and returns to the persisted retry state; a
  terminal failure reaches `EXPIRED` or `EXHAUSTED` according to the immutable retry snapshot.
- A duplicate claim returns no second owner; a lease-expired record can be recovered by a new owner.
- An old owner cannot acknowledge or fail after lease fencing has moved ownership.
- A source transaction rollback leaves no claimable reliable Command record.
- `send` still executes synchronously; `enqueue`, `schedule`, and `delay` are the only reliable
  scheduling entry points and expose no polling method.
- Static scans find no Command production reference to `CommandManager`, `Locker`,
  `getByNextTryTime`, or the legacy scheduled retry service.

# Constraints and invariants

- The JPA substrate remains private; it owns claim, lease, retry, safe failure, acknowledgement,
  and retention transition semantics.
- One successful claim corresponds to one execution attempt; scanning must not consume retries.
- Ownership mutations are fenced by the delivery token and unexpired lease.
- Command handler code remains synchronous and runs through the existing `CommandSupervisor.send`.
- Reliable payloads and execution context remain persisted values; no persisted entity instance is
  introduced.
- Changes are breaking by project policy; no compatibility bridge for removed Command surfaces.

# Decisions

- Use the existing `JpaCommandExecutionSubstrate` and CAS repository primitives from #170; do not
  reimplement ownership state in the supervisor.
- Put the worker adapter in the Command JPA module/starter because core cannot depend on JPA.
- Replace local scheduled execution with a substrate-backed worker that claims due records and then
  invokes the synchronous core supervisor.
- Delete `CommandManager`, `JpaCommandScheduleService`, `CommandScheduleProperties`, and related
  provider/configuration/test paths when no longer referenced by Command production code.

# Open questions


# Verification expectations

- Run focused Command core/JPA/starter tests and the existing substrate integration tests.
- Add or update integration coverage for success, retryable/terminal failure, duplicate claim,
  lease recovery, old-owner fencing, rollback atomicity, and synchronous send.
- Run static scans for removed polling/Locker/manager symbols in Command production sources.
- Record actual commands, results, and any environment-limited checks in `verification.md`.
