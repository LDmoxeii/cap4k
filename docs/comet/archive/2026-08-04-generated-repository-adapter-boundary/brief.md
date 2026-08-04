# Outcome

Replace the Generator's public Spring Data aggregate repository interface with one framework-owned, provider-private JPA carrier while keeping `Mediator.repositories` as the only aggregate repository entry for application behavior. Preserve the current aggregate query, dirty-checking/Unit-of-Work save, and `RepositorySupervisor.remove` deletion semantics, and make the carrier losslessly visible to Analyzer and the real Drawing Board round trip.

# Scope

- Change the default aggregate repository planner/template so it generates only a framework-owned JPA repository adapter/carrier for each aggregate repository artifact.
- Remove generated application-visible `JpaRepository` and `JpaSpecificationExecutor` interfaces and remove Spring Data repository injection from generated source.
- Back the generated carrier with the cap4k JPA provider through `EntityManager` or an equivalent provider-private implementation.
- Keep `Mediator.repositories` and `RepositorySupervisor` as the public application-facing repository path.
- Preserve current Command/Query invocation guards, aggregate-root checks, repository observation, Hibernate dirty checking, Unit-of-Work persistence, and explicit removal registration.
- Keep repository artifact metadata on the generated carrier so Analyzer can recover the `repository` aggregate element and the Drawing Board retains that DB-derived framework-owned structure as analysis evidence.
- Update Generator planner/renderer tests, Analyzer metadata recovery tests, the real Design JSON round-trip gate, and focused JPA runtime tests.
- Run focused tests, a repository stale-surface scan, and the repository required `check` before delivery.

# Non-goals

- Do not modify `docs/framework-capability-audit` or the `docs/framework-capability-audit` worktree/branch.
- Do not implement any other Runtime audit finding, including repository route-key uniqueness, `JpaPredicate` variant validation, ordered ID-list/pagination fixes, codec migration, provider descriptors, transport work, Console retirement, Snowflake retirement, or reliable-record changes.
- Do not preserve a generated public Spring Data interface through aliases, deprecated APIs, dual generation, fallback adapters, or compatibility bridges.
- Do not add a second business repository entry beside `Mediator.repositories`.
- Do not change the accepted dirty-checking and Unit-of-Work ownership model.

# Acceptance examples

- Given a generated aggregate, application behavior compiles and performs aggregate reads through `Mediator.repositories` without declaring or injecting a public generated `JpaRepository`/`JpaSpecificationExecutor` bean.
- Given a Command that loads and changes a managed aggregate through `Mediator.repositories`, the current Hibernate dirty-checking and Command-owned Unit-of-Work save path persists the change without an explicit Spring Data `save` call.
- Given an aggregate deletion requested through `Mediator.repositories.remove`, `RepositorySupervisor` still observes the loaded aggregate and registers the existing explicit delete intent.
- Given generated repository source, it contains one framework-owned provider carrier with repository analysis metadata and contains no public Spring Data repository interface, no generated Spring Data repository bean, and no Spring Data repository constructor dependency.
- Given real compiler analysis of generated sources, Analyzer recognizes the carrier's repository metadata as a complete framework-owned aggregate element.
- Given the DB source generates Project A, real Analyzer output recovers the provider-private repository carrier, and the Drawing Board retains its DB-derived repository structure as analysis evidence, Project B regenerates from the same DB source without a public Spring Data repository interface. The Design JSON round-trip gate covers only Design JSON elements and does not treat Repository as a Design JSON input.
- Given a second `cap4kGenerate`, the removed public Spring Data repository surface is not recreated.

# Constraints and invariants

- The implementation branch is `fix/generated-repository-adapter-boundary`, created from the latest `origin/master` in an isolated worktree.
- Cap4k has no external users; this is an intentionally breaking replacement with one current contract.
- `Mediator.repositories` is the sole business repository boundary.
- Ordinary managed aggregate modification remains Hibernate dirty checking plus the Command-owned Unit of Work.
- Aggregate removal remains `RepositorySupervisor.remove` plus explicit delete-intent registration.
- The carrier is framework-owned and provider-private; application code must not depend on a generated Spring Data interface.
- Analyzer recovery uses the existing compile-time analysis-metadata boundary rather than a sidecar compatibility index.
- Current `master` and the confirmed runtime-capability-reset specification outrank historical repository plans that still describe public Spring Data repositories.

# Decisions

- Delete the generated public Spring Data repository interface rather than rename or deprecate it.
- Generate one internal framework carrier per aggregate repository artifact and annotate that carrier with `AggregateElementMetadata(type = "repository")`.
- Keep Spring Data implementation details, if used internally by the JPA provider, behind the cap4k repository adapter and out of generated public signatures.
- Preserve repository artifact identity in planner metadata and Analyzer recovery even though the implementation carrier class name may differ from the removed public interface name.
- Keep the real two-project Design JSON -> generated skeleton -> Analyzer -> Drawing Board -> regenerated skeleton gate for Design JSON elements. Verify DB-driven Repository generation in the same fixture through the DB source and Analyzer/Drawing Board structural evidence; do not make Repository a Design JSON tag or Drawing Board input.
- Treat existing historical docs/plans that intentionally preserve public Spring Data repository generation as stale surfaces; do not rewrite unrelated historical design records.

# Open questions


# Verification expectations

- Focused Generator planner and Pebble renderer tests prove the generated source shape and absence of public Spring Data interfaces.
- Focused Analyzer tests prove the carrier metadata is accepted and recovered as repository aggregate structure.
- Focused JPA runtime/starter tests prove `Mediator.repositories` query, dirty-checking save, and remove semantics without application injection of a generated Spring Data repository.
- `DesignRoundTripFunctionalTest` uses the real compiler Analyzer and two clean projects, asserts carrier recovery, and rejects public Spring Data repositories in both generations.
- A repository stale-surface scan checks active source/templates/tests/fixtures/machine contracts for generated public `JpaRepository`/`JpaSpecificationExecutor` aggregate interfaces while excluding historical archives/specs from mutation.
- Run the repository's required `check` command and record the actual result.
