# Outcome

Replace Cap4k's fragmented infrastructure-owned field model with one exact managed-field policy contract based on `@Managed=<policy-key>`. Resolve every used policy before generator planning, project it independently into checked-in authoring surfaces and JPA/Hibernate persistence behavior, and generate one runtime catalog that drives entity admission, managed value adaptation, field access, persistence enrichment, and startup diagnostics.

Generalize the build-time installation surface from directly discovered Artifact Addons to Pipeline Extensions with typed contributions. Artifact Addons remain supported as one contribution type, and third-party managed-field policy definitions become another contribution type. Pipeline stage order remains framework-owned.

The design authority for Shape is `docs/superpowers/specs/2026-07-31-cap4k-managed-field-policy-and-pipeline-extension-design.md`. This change preserves the existing application execution/UoW stabilization, owned-child admission, semantic value-type, Strong ID, soft-delete, and checked-in Factory/Behavior ownership contracts except where the design explicitly replaces their managed-field implementation surfaces.

# Scope

- Replace `@IdStrategy`, broad `@Managed` roles, and `@Inherited` with exact lowercase dot-separated managed policy keys.
- Preserve physical schema facts while separating raw DB-source policy selection from canonical policy definition and resolution.
- Add built-in and extension-contributed policy definitions, selection provenance, definition ownership, lifecycle, explicit-value, semantic-type, handler, adapter, and operation-specific persistence authority semantics.
- Add `PipelineExtensionProvider`, versioned descriptors, typed contributions, centralized loading/validation, contribution-scoped options, and the `cap4kPipelineExtension` dependency bucket.
- Retain Artifact Addon planning and template namespace isolation as a typed Pipeline Extension contribution.
- Derive default Factory/write-surface exposure separately from constructor support and JPA INSERT/UPDATE participation.
- Preserve checked-in Factory and Behavior first materialization followed by `SKIP`, including the user's freedom to accept an explicit valid identifier in custom construction code.
- Generate a build-owned managed-field catalog for roots and owned entities, including exact persistence-property identity and typed runtime support for application identifiers and soft-delete sentinels.
- Assemble one immutable application registry with cached reflective access, exact custom accessor overrides, explicit value adapters, and startup validation.
- Initialize or validate new-entity managed values at aggregate admission, before root `onCreate`, while retaining side-effect-free pre-persist validation as a safety net.
- Generalize `JpaPersistenceAuditEnricher` into qualifier-owned JPA persistence enrichment using candidate detection, per-enricher mutation guards, final dirty detection, and stable outer-UoW context.
- Provide standard identifier, version, soft-delete, database-generated, tenant/context, and audit-time/audit-actor policy semantics.
- Derive Hibernate-compatible JPA projection from resolved policy rather than treating generic managed fields as non-insertable/non-updatable.
- Migrate active source, configuration, runtime wiring, generators, fixtures, documentation, skills, and the `only-engine` extension to the new authoritative path.

# Non-goals

- A public multi-ORM Persistence Provider SPI or a second persistence backend.
- Queryable relational/embedded Value Object persistence, `@Embedded`, `@ElementCollection`, read-model generation, or cross-context query composition.
- Automatic tenant query filtering, discriminator SQL, schema/database routing, RPC trust policy, or a complete multi-tenancy solution.
- A required Cap4k audit base class, fixed managed-field property names, or a prescribed constructor/inheritance style.
- Audit history tables, temporal persistence, event sourcing, or delete-audit semantics.
- Arbitrary extension-defined pipeline stages, mutable source/canonical callbacks, direct file-writing hooks, or per-extension classloader isolation.
- Automatic propagation of build-time extension dependencies into application runtime dependencies.
- Prevention or repair of every direct user mutation of a managed property.
- Automatic aggregate-root version or audit advancement solely because an owned child changed.
- Restoring a mandatory application-facing `UnitOfWork.save()` lifecycle.
- Backward-compatible aliases for removed experimental annotations, configurations, registries, or enrichment SPIs.

# Acceptance examples

- Given `@Managed=identifier.uuid7` on a single physical primary key, when canonical resolution completes, then the field uses the built-in UUID7 policy, the default Factory omits ordinary ID input, a valid explicit UUID7 may be preserved, absence is allocated no later than admission, `onCreate` observes the final ID, and JPA inserts it without later update authority.
- Given `@Managed=identifier.assigned`, when the default Factory is generated, then the ID is required and admission rejects absence; given a valid custom-constructor value, admission preserves it.
- Given `@Managed=identifier.database-identity`, when a normal aggregate is created, then application input is forbidden, no application initializer allocates an ID, and provider/database identity assignment remains observable after flush.
- Given `@Managed=soft-delete`, when a new root or child is admitted, then the generated active sentinel is preserved or initialized and written on INSERT; later delete transition remains provider-owned.
- Given `@Managed=scope.tenant`, when a new entity is admitted, then an absent tenant is filled from the explicit execution-context snapshot, an equal explicit value is preserved, a mismatch or missing context fails, and Cap4k makes no claim to filter all tenant queries.
- Given a business CREATE or UPDATE candidate with audit policies, when one stabilization round runs, then the applicable audit fields are enriched with stable UoW values, only declared mutation footprints change, final dirty detection sees the result, and Hibernate writes the configured columns.
- Given a clean loaded aggregate, when a Command or Query only reads it, then it is not an enrichment candidate, no audit field changes, and no audit-only SQL UPDATE occurs.
- Given a child-only update, when stabilization runs, then the changed child may receive its own update audit values while the root's version and audit fields do not advance automatically.
- Given a delete-only entity change, when persistence enrichment runs, then update-audit handles are not exposed and no delete audit is inferred.
- Given semantic `Instant` and target `Long`, when no exact adapter qualifier is resolved, then startup fails rather than guessing seconds or milliseconds; with an exact adapter, semantic comparison and assignment use the adapted target value.
- Given a managed field declared privately or in a user superclass, when the registry starts, then exact generated metadata resolves one cached reflective binding; ambiguous hierarchy shadowing fails unless one exact custom accessor replaces it.
- Given an enricher that mutates a business property or another qualifier's property, when its invocation completes, then per-enricher dirty-delta validation fails the UoW before flush.
- Given a non-empty `cap4kPipelineExtension` classpath, when no `PipelineExtensionProvider` is discoverable or IDs/SPI versions/contribution types conflict, then loading fails with provider and classpath evidence.
- Given one extension contributing both a managed policy provider and an Artifact Addon, when the pipeline runs, then policy definitions participate only in fixed canonical resolution and artifact plans remain post-canonical with preserved template namespace isolation.
- Given checked-in Factory or Behavior source already exists, when generation runs again, then `SKIP` remains authoritative and no overwrite, merge, or patch freshness promise is introduced.

# Constraints and invariants

- The pipeline remains `collect -> normalize -> enrich -> plan -> render -> export`; extensions contribute typed declarations to fixed phases and cannot reorder or inject stages.
- Raw policy capture, policy selection, policy definition ownership, canonical resolution, generator planning, and runtime execution remain distinct responsibilities.
- Policy keys are exact and case-sensitive; there is no prefix matching, abbreviation, compatibility alias, or renderer-side inference.
- Every managed lifecycle set is non-empty. Admission and persistence enrichment cannot coexist in one policy, while admission may coexist with provider/database ownership for operation-specific behavior.
- Every non-`NONE` insert/update authority has exactly one executable owner compatible with its lifecycle. Extension definitions cannot claim built-in `FRAMEWORK` ownership.
- A handler qualifier belongs to exactly one runtime Handler kind across the application. Multiple fields under one qualifier require complete, unique slots.
- Separate handler qualifiers have no semantic ordering; Spring `Ordered`, `@Order`, and injection order are not part of the contract.
- Application identifiers are initialized no later than aggregate admission; UoW validation never performs first allocation or late repair.
- Loaded entities are never re-admitted or reinitialized as new entities.
- Owned entities receive managed-field handling but never aggregate lifecycle callbacks or independent Domain Event authority.
- Persistence enrichment runs only for candidate changes, before final dirty detection, and may mutate only the exact provider-property footprints owned by the invoked enricher.
- Audit/enrichment values are stable within one outer REQUIRED UoW and repeated stabilization rounds must be idempotent.
- Semantic values and entity target representations are connected only through direct assignability or an explicit named adapter.
- Cached reflection is the default access mechanism; it implements access only and never guesses field semantics.
- Build-time Pipeline Extension code and runtime application components are connected only by stable IDs and generated metadata; runtime dependencies remain explicit.
- Spring Data JPA plus Hibernate remains the only persistence runtime in this change.
- Checked-in source ownership remains first generation plus `SKIP`; build-owned catalogs, converters, and provider artifacts remain regenerable.

# Decisions

- Create a new Canonical Capability named `managed-field-policy-and-pipeline-extension`; do not replace the existing UoW, owned-child, or semantic-value-type capability specifications.
- Use `@Managed=<exact-policy-key>` as the sole active custom column annotation for infrastructure-owned field policy.
- Remove `@IdStrategy`, `@Inherited`, broad `@Managed=system|scope|deleted`, `cap4kAddon`, direct `ServiceLoader<ArtifactAddonProvider>`, `JpaPersistenceAuditEnricher`, and the parallel Strong-ID-only registry after migration.
- Use exact built-in policy keys for UUID7, snowflake, assigned and database-identity IDs, version, soft delete, database generation, tenant/context binding, audit time, and audit actor.
- Keep Factory input policy separate from constructor freedom, explicit-value validation, semantic value authority, and JPA column participation.
- Preserve valid caller-supplied application identifiers when the selected policy permits them.
- Use admission initialization followed by side-effect-free UoW validation; do not allocate managed values during stabilization.
- Generalize generated Strong ID metadata into one managed-field catalog and registry, with typed identifier and soft-delete runtime support.
- Use `readTarget`, non-mutating semantic adaptation/comparison, and semantic assignment rather than exposing raw entity mutation to Handlers.
- Generalize audit enrichment into `JpaPersistenceEnricher`, scoped by exact qualifiers and slots, with a stable `Instant` and execution-context snapshot.
- Do not enrich clean reads, do not infer delete audit, and do not advance root audit/version for child-only changes.
- Validate each enricher's dirty delta immediately against only its own mutation footprints.
- Make Pipeline Extension the sole build-time installation unit and retain Artifact Addon as one typed contribution.
- Use one task-scoped extension classloader for the resolved classpath; defer per-extension isolation until a concrete conflict exists.
- Keep runtime handlers, adapters, and accessors as Spring application components and require startup completeness for every used binding.
- Keep JPA/Hibernate as the only provider and defer the public Persistence Provider SPI until a real second provider supplies evidence.
- Treat the 2026-07-31 approved design as the detailed implementation authority while this brief and target spec form the Comet approval contract.
- The user explicitly confirmed this complete brief and target specification as the Build contract and authorized sub-agent assistance for implementation.

# Open questions

None.

# Verification expectations

- Validate the brief has exactly the eight required H1 sections and that the target capability spec is complete rather than a delta fragment.
- Run focused parser, default-precedence, policy-resolution, contribution-loader, generator-planner, catalog, registry, admission, adapter, accessor, JPA projection, UoW, context, and migration tests described by the design.
- Verify startup failures for every missing/duplicate/incompatible handler, adapter, accessor, runtime-support, slot, field, and extension binding category.
- Verify SQL/database participation for application identifiers, audit fields, soft delete, version, database identity, and generated columns instead of relying only on in-memory state.
- Prove identifiers and context-bound values are visible before root `onCreate`, owned children receive admission without aggregate callback/event authority, and repository-loaded entities are never initialized.
- Prove candidate-before-enrich-before-final detection, clean-read silence, stable repeated-round enrichment, delete exclusion, and per-enricher mutation isolation.
- Prove Artifact Addon behavior, options, plan evidence, and template isolation survive the Pipeline Extension migration.
- Run repository-wide Gradle verification for the affected build graph and `git diff --check`.
- Search active code, templates, fixtures, documentation, and skills for removed terms including `@IdStrategy`, `@Inherited`, `DbIdStrategy`, `DbManagedRole`, `cap4kAddon`, `JpaPersistenceAuditEnricher`, and `GeneratedOwnIdRegistry`; retain only explicitly justified migration diagnostics or historical evidence.
- Do not edit archived Comet evidence or historical specs to disguise their recorded state.
