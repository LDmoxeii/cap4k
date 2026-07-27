# cap4k Database-Entrusted Fields Construction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate database identity and optimistic-lock version fields as provider-assigned nullable state outside aggregate constructors and factory payloads, while removing default owned-child parent scalar/inverse navigation without breaking parent-owned persistence or Schema joins.

**Architecture:** Pipeline core remains the semantic owner: resolved own-ID/version policies identify database-entrusted fields, and physical `DbColumnSnapshot.parentRef` remains relation evidence but is not projected as a domain field. The aggregate generator converts those resolved roles into explicit construction/render roles shared by Entity and Factory planners. Pebble renders final planner truth. JPA persists the root-owned graph through existing cascades and the unchanged root-oriented UoW.

**Tech Stack:** Kotlin 2.2.20, Gradle, JUnit 5, JPA/Hibernate, Spring Boot test, H2, Pebble, Gradle TestKit, kotlin-compile-testing.

## Reader Contract

Before editing, read `AGENTS.md`, the approved `docs/superpowers/specs/2026-07-26-cap4k-database-entrusted-fields-construction-design.md`, and every current file named by the active task. Read GitHub issue `#137` as the future parent-access-mode boundary and issue `#115` as the future owned-child factory-spec boundary. This iteration references but does not close either future capability.

Execute tasks in order. For every behavior change, add the focused failing test first, run the stated RED command, make the minimum production change, and rerun the same command to GREEN. Do not add temporary compatibility APIs merely to keep later modules compiling between intentional breaking steps.

## Current Evidence

- `DefaultCanonicalAssembler` copies parentRef into both Entity and Schema fields.
- `OwnedParentBindingResolver` resolves physical parentRef before canonical field projection.
- `OwnedRelationCardinalityInference` treats parentRef-as-primary-key as owned-one without a supported `@MapsId` runtime contract.
- `AggregateInverseRelationInference`, `AggregateInverseRelationModel`, and `CanonicalModel.aggregateInverseRelations` generate child-to-parent navigation unconditionally.
- Entity planning has resolved policy/ID/provider controls but excludes only generated Strong IDs and `SYSTEM_TRANSITION_ONLY` from constructors.
- Factory payload filtering is resolved, but constructor mapping still treats database identity/version as missing.
- Provider `versionFieldName` is derived from resolved version policy; explicit persistence-field `version` is absent for DSL-default versions.
- Schema already consumes forward owned relations only.
- Existing factory-supervisor/UoW tests already prove one root CREATE and graph reconciliation; preserve those contracts.

## Global Constraints

- [ ] Start on a clean, non-protected branch/worktree containing this plan/spec and descending from `c49e12f5`.
- [ ] Reference issue `#137` and the approved design in implementation notes without claiming the issue is complete.
- [ ] Preserve unrelated worktree changes; stop on overlapping edits.
- [ ] No compatibility constructors, deprecated inverse shells, fallback classifiers, aliases, or feature flags.
- [ ] `READ_ONLY` filters user write surfaces only. It does not generally grant provider-assigned construction.
- [ ] Do not alter generic audit/system/scope field construction or add a managed-field lifecycle SPI.
- [ ] Do not alter Strong ID algorithms/catalogs, `IdentifierGenerator`, `OwnedEntityList`, or Phase 4 documents.
- [ ] Do not alter UoW public APIs, `PersistIntent`, root reconciliation, interceptors, or bounded `isNew()` uses.
- [ ] Do not add child factories/specs/persist, reverse modes, parent scalar opt-in, `@MapsId`, or shared-PK support.
- [ ] Do not edit Unique Query/Handler/Validator production planners/templates. Set `artifact.unique=false` in parentRef-unique fixtures.
- [ ] Preserve merged soft-delete storage, sentinel, SQL, dialect, assignment, and constructor behavior.
- [ ] Classify identity/version from resolved policy only; controls are consistency/render projections.
- [ ] Version accepts only Short/Int/Long spellings already accepted for identity; overflow is not framework-managed.
- [ ] Keep canonical/Schema DB nullability physical. Nullable Entity identity/version is a render/construction shape only.
- [ ] Commands use `.\gradlew.bat` for Windows; use `./gradlew` in CI/Linux.

For negative `rg` checks, treat exit code `1` as success and any other non-zero code as failure. Never hide an unexpected native-command failure.

---

## Task 0: Guard the worktree and capture the baseline

**Files:** Read `AGENTS.md`, the approved design, and this plan. Modify nothing.

- [ ] **Step 1: Verify branch, ancestry, and dirtiness**

```powershell
$branch = & git branch --show-current
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branch)) { throw "No active branch" }
$head = & git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw "Cannot resolve HEAD" }
& git merge-base --is-ancestor c49e12f5 $head
if ($LASTEXITCODE -ne 0) { throw "HEAD does not descend from c49e12f5" }
$status = @(& git status --short --untracked-files=all)
if ($LASTEXITCODE -ne 0) { throw "Cannot inspect worktree" }
if ($status.Count -ne 0) { $status; throw "Implementation must start clean" }
"BRANCH=$branch"
"HEAD=$head"
```

Expected: non-protected branch, correct ancestry, clean worktree. Stop instead of reverting/moving user files.

- [ ] **Step 2: Run focused pipeline baseline**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --console=plain
if ($LASTEXITCODE -ne 0) { throw "Focused baseline failed" }
```

Expected: green. Record but do not repair unrelated baseline failures.

- [ ] **Step 3: Confirm protected production areas are unchanged**

```powershell
git diff --exit-code c49e12f5 -- ddd-domain-repo-jpa/src/main ddd-core/src/main
if ($LASTEXITCODE -ne 0) { throw "Protected UoW/factory production drift exists" }
git diff --exit-code c49e12f5 -- cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt
if ($LASTEXITCODE -ne 0) { throw "Unique planning drift exists" }
```

- [ ] **Step 4: Commit**

No commit; read-only task.

---

## Task 1: Validate the resolved version role and type contract

**Files:**

- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`

- [ ] **Step 1: Add RED tests for authoritative resolution**

Cover accepted types:

```kotlin
val supported = listOf(
    "Short", "kotlin.Short", "java.lang.Short",
    "Int", "kotlin.Int", "Integer", "java.lang.Integer",
    "Long", "kotlin.Long", "java.lang.Long",
)
```

Use the same matrix first to lock the existing accepted database-identity types, then for DB-explicit and `versionDefaultColumn = "lock_version"` assert enabled version role, matching field, `READ_ONLY`, and identical provider `versionFieldName`. For DSL-default, assert explicit `AggregatePersistenceFieldControl.version` is null. Reject version `String` and `UUID` with table/entity/field/column/type plus `Short, Int, Long` in the message. An unmarked `version` with disabled default must remain ordinary. Keep the existing unsupported database-identity type rejection unchanged.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
```

Expected: unsupported version types currently assemble, so rejection tests fail.

- [ ] **Step 3: Validate immediately after resolving version policy**

Add this narrow shape before managed/provider controls are built:

```kotlin
private fun validateVersionType(entity: EntityModel, policy: ResolvedMarkerPolicy) {
    if (!policy.enabled) return
    require(policy.writePolicy == SpecialFieldWritePolicy.READ_ONLY) {
        "resolved version ${entity.packageName}.${entity.name}.${policy.fieldName} must be READ_ONLY"
    }
    val field = requireNotNull(entity.fields.singleOrNull { it.name == policy.fieldName }) {
        "resolved version field ${policy.fieldName} is missing from ${entity.packageName}.${entity.name}"
    }
    require(field.type in SupportedVersionTypes) {
        "unsupported version type for table ${entity.tableName}, entity ${entity.packageName}.${entity.name}, " +
            "field ${field.name}, column ${policy.columnName}: ${field.type}; supported types: Short, Int, Long"
    }
}

private val SupportedVersionTypes = setOf(
    "Short", "kotlin.Short", "java.lang.Short",
    "Int", "kotlin.Int", "Integer", "java.lang.Integer",
    "Long", "kotlin.Long", "java.lang.Long",
)
```

Do not consult field names or `AggregatePersistenceFieldControl.version` as fallback classifiers.

- [ ] **Step 4: Run GREEN/regression**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --console=plain
if ($LASTEXITCODE -ne 0) { throw "Core regression failed" }
```

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat(pipeline): validate resolved version fields"
```

---

## Task 2: Separate structural parentRef from canonical domain fields

**Files:**

- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedParentBindingResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInferenceTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedParentBindingResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`

- [ ] **Step 1: Replace shared-PK positive evidence with RED rejection**

Delete/replace `primary key parent ref infers one`. In resolver and assembler tests require exactly:

```kotlin
"owned child video_file cannot use parent reference column video_id as its primary key; " +
    "declare an independent child primary key"
```

For a valid child assert parentRef is absent from Entity/Schema fields, relation join/parentRefColumn remain `video_id`, and physical unique-constraint columns still contain `video_id`. Retain missing/ambiguous, owned-many, unique-parentRef owned-one, and neutral scope/deleted cases.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.OwnedParentBindingResolverTest" --tests "com.only4.cap4k.plugin.pipeline.core.OwnedRelationCardinalityInferenceTest" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
```

Expected: shared PK still infers one and canonical fields still contain parentRef.

- [ ] **Step 3: Reject shared PK in binding resolution**

After selecting the single parentRef:

```kotlin
val parentRefKey = columnKey(parentRefColumn.name)
require(table.primaryKey.map(::columnKey) != listOf(parentRefKey)) {
    "owned child ${table.tableName} cannot use parent reference column ${parentRefColumn.name} " +
        "as its primary key; declare an independent child primary key"
}
```

Use a case-insensitive key. Keep existing missing/ambiguous messages.

- [ ] **Step 4: Remove the invalid cardinality shortcut**

Delete the branch returning `ONE` when child PK equals parentRef. Do not add `@MapsId` or another fallback.

- [ ] **Step 5: Filter only published domain projection**

Keep relation inference before Entity construction, then change field projection to the existing mapping with a single structural filter:

```kotlin
val fields = table.columns
    .filterNot { it.parentRef }
    .map { column ->
        val fieldName = lowerCamelIdentifier(column.name)
        val resolvedType = resolveStrongIdFieldType(
            tableName = table.tableName,
            column = column,
            aggregateRootIdsByName = aggregateRootIdsByName,
        ) ?: if (isTablePrimaryKeyColumn(table, column) && generatedOwnId != null) {
            generatedOwnId.typeName
        } else {
            column.kotlinType
        }
        FieldModel(
            name = fieldName,
            type = resolvedType,
            nullable = column.nullable,
            defaultValue = column.defaultValue,
            typeBinding = column.typeBinding,
            enumItems = column.enumItems,
            columnName = column.name,
            managedRole = column.managedRole,
            inherited = column.inherited == true,
        )
    }
```

Do not mutate DB columns, unique constraints, or `AggregateRelationModel.parentRefColumn`.

- [ ] **Step 6: Run GREEN/regression**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-source-db:test --console=plain
if ($LASTEXITCODE -ne 0) { throw "Parent binding regression failed" }
```

- [ ] **Step 7: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main cap4k-plugin-pipeline-core/src/test
git commit -m "refactor(pipeline): keep parent refs structural"
```

---

## Task 3: Delete automatic inverse navigation and domain parentRef API

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Delete: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateProjectionArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Modify: generator aggregate tests named by current `rg` results.

- [ ] **Step 1: Write final one-way tests before deletion**

Retain API tests for `DbColumnSnapshot(parentRef = true)` and `AggregateRelationModel(parentRefColumn = ...)`. Remove all inverse/`FieldModel(parentRef=...)` constructions. Replace core inverse assertions with one forward relation plus child Entity/Schema field absence. Replace generator inverse tests with parent forward relation retained and child relation fields empty unless the child itself owns another child or has an ordinary explicit relation.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test --console=plain
```

Expected: old inverse/domain-parentRef APIs still exist, so final-shape assertions fail.

- [ ] **Step 3: Delete the canonical API outright**

Delete `FieldModel.parentRef`, the entire `AggregateInverseRelationModel`, and `CanonicalModel.aggregateInverseRelations`. Preserve `DbColumnSnapshot.parentRef` and `AggregateRelationModel.parentRefColumn`.

- [ ] **Step 4: Delete core inference and inverse-only tests**

Delete `AggregateInverseRelationInference.kt`; remove its assembler call/local/canonical argument. Delete inverse collision tests because the generated field no longer exists. Keep direct-parent validation and forward relation tests.

- [ ] **Step 5: Reduce relation planning to forward relations**

Final signature:

```kotlin
fun planFor(
    entity: EntityModel,
    relations: List<AggregateRelationModel>,
    generatedOwnIdsByEntity: Map<String, GeneratedOwnIdDescriptor> = emptyMap(),
): AggregateRelationRenderPlan
```

Delete inverse parameters, filtering, field maps, imports, and relation-type aggregation. Update Entity/Projection/Schema callers. In Entity planning delete `readOnlyInverseJoinColumns` and its insertable/updatable branches; keep suppression of scalar columns backing actual owner-side `MANY_TO_ONE`/`ONE_TO_ONE` relations.

- [ ] **Step 6: Remove dead parentRef Factory/Entity context**

Delete `parentRef`, `structuralParentRef`, `structuralFields`, `constructorStructuralFields`, and parentRef-specific missing-field branches. ParentRef is absent upstream; no generator fallback remains.

- [ ] **Step 7: Run GREEN**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test --console=plain
if ($LASTEXITCODE -ne 0) { throw "Inverse API deletion regression failed" }
```

- [ ] **Step 8: Prove the deletion/retention split**

```powershell
$inverse = & rg -n 'AggregateInverseRelationModel|aggregateInverseRelations|AggregateInverseRelationInference' cap4k-plugin-pipeline-api/src cap4k-plugin-pipeline-core/src cap4k-plugin-pipeline-generator-aggregate/src
if ($LASTEXITCODE -eq 0) { $inverse; throw "Inverse infrastructure remains" }
if ($LASTEXITCODE -ne 1) { throw "Inverse scan failed" }
$domainParent = & rg -n 'structuralParentRef|constructorStructuralFields|field\.parentRef|filter \{ it\.parentRef \}' cap4k-plugin-pipeline-generator-aggregate/src
if ($LASTEXITCODE -eq 0) { $domainParent; throw "Domain parentRef plumbing remains" }
if ($LASTEXITCODE -ne 1) { throw "ParentRef scan failed" }
rg -n 'val parentRef: Boolean|parentRefColumn' cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
if ($LASTEXITCODE -ne 0) { throw "Physical parentRef evidence was removed" }
```

Expected: no inverse/domain-parentRef planner match; positive scan shows only physical `DbColumnSnapshot.parentRef` and relation `parentRefColumn`.

- [ ] **Step 9: Commit**

```powershell
git add -A cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate
git commit -m "refactor(pipeline): remove automatic inverse navigation"
```

---

## Task 4: Centralize database-entrusted construction roles

**Files:**

- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntrustedFieldPlanning.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntrustedFieldPlanningTest.kt`

- [ ] **Step 1: Add RED role/mismatch tests**

Cover database identity + explicit version, database identity + DSL-default version without field-control marker, application-side ID + version, generic managed READ_ONLY, unmarked `version`, non-READ_ONLY identity/version, version provider mismatch, and identity ID-control mismatch. Assert only identity/version field names are provider-assigned.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateEntrustedFieldPlanningTest" --console=plain
```

Expected: unresolved class.

- [ ] **Step 3: Implement the bounded shared resolver**

```kotlin
internal data class AggregateEntrustedFields(
    val databaseIdentityFieldName: String? = null,
    val versionFieldName: String? = null,
) {
    fun isDatabaseIdentity(name: String) = databaseIdentityFieldName == name
    fun isVersion(name: String) = versionFieldName == name
    fun isProviderAssigned(name: String) = isDatabaseIdentity(name) || isVersion(name)
}
```

`resolve(entity, model)` must:

1. select the entity's single resolved special-field policy;
2. classify own ID only when resolved kind is `DATABASE_SIDE`;
3. require identity field equals `entity.idField`, write policy is `READ_ONLY`, and matching `AggregateIdPolicyControl` is database-side;
4. classify version only when resolved version is enabled and field matches;
5. require version `READ_ONLY` and exact equality with derived provider `versionFieldName`;
6. return no roles when no resolved policy exists for a non-aggregate/projection-only hand-built model.

Required mismatch message shapes:

```kotlin
"resolved database identity projection mismatch for ${entity.packageName}.${entity.name}.${id.fieldName}"
"resolved version projection mismatch for ${entity.packageName}.${entity.name}: " +
    "resolved=${version.fieldName}, provider=${provider?.versionFieldName}"
```

Never inspect type, conventional name, raw managed role, all READ_ONLY fields, or `AggregatePersistenceFieldControl.version` to grant a role.

- [ ] **Step 4: Run GREEN**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateEntrustedFieldPlanningTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Entrusted role tests failed" }
```

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntrustedFieldPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntrustedFieldPlanningTest.kt
git commit -m "feat(pipeline): classify database entrusted fields"
```

---

## Task 5: Generate provider-assigned Entity construction shape

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add RED planner matrix**

Cover database identity with absent/Short/Int/Long version and application Strong ID + Long version. For entrusted fields assert `providerAssignedIdentity`/`providerAssignedVersion`, `propertyNullable=true`, `propertyInitializer="null"`, `constructorIncluded=false`, while physical `nullable=false`. Assert a generic managed READ_ONLY field remains constructor-included/non-null/parameter-initialized. Retain soft-delete sentinel and Strong ID regression assertions.

- [ ] **Step 2: Run planner RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: identity/version remain constructor fields and role keys are absent.

- [ ] **Step 3: Project explicit roles**

Resolve once per entity, then compute:

```kotlin
val providerAssignedIdentity = entrustedFields.isDatabaseIdentity(field.name)
val providerAssignedVersion = entrustedFields.isVersion(field.name)
val providerAssigned = providerAssignedIdentity || providerAssignedVersion
val constructorIncluded =
    !generatedOwnId && !providerAssigned &&
        writePolicy != SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY.name
val propertyNullable = providerAssigned || field.nullable
val propertyInitializer = when {
    providerAssigned -> "null"
    isSoftDeleteField -> requireNotNull(renderedSoftDelete).propertyInitializer
    writePolicy == SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY.name ->
        error(
            "aggregate field ${entity.packageName}.${entity.name}.${field.name} has " +
                "SYSTEM_TRANSITION_ONLY write policy but no semantic property initializer"
        )
    else -> field.name
}
```

Set aggregate Entity `isVersion` only from `providerAssignedVersion`. Keep the projection-only planner's existing explicit field-control behavior unchanged; it is not a construction classifier. Set `constructorFields` from `constructorIncluded == true`. Do not change physical `nullable`/`attributeOverrideNullable`.

- [ ] **Step 4: Run planner GREEN**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Entity planner tests failed" }
```

- [ ] **Step 5: Add RED renderer compilation test**

Render identity + Long version + title + generic managed `createdAt`. Require `var id: Long? = null`, `var version: Long? = null`, `@GeneratedValue(IDENTITY)`, `@Version`, and constructor containing title/createdAt but not id/version. Do not assert a provider's exact initial version number.

Mechanically migrate every hand-built `aggregate/entity.kt.peb` scalar-field context in `PebbleArtifactRendererTest`: set `propertyNullable` equal to its existing `nullable` value for ordinary/soft-delete fields, `true` for provider-assigned identity/version, and leave the existing Strong-ID `lateinit` branch semantically unchanged. Do not add an undefined-key fallback to the template.

- [ ] **Step 6: Run renderer RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
```

Expected: template cannot separate physical nullability from property nullability.

- [ ] **Step 7: Make template mechanical**

Use `field.propertyNullable` only for scalar property type:

```pebble
var {{ field.name }}: {{ type(field) | raw }}{% if field.propertyNullable %}?{% endif %} = {{ field.propertyInitializer | raw }}
```

Constructor parameters retain existing `field.nullable`; entrusted fields never enter the list. Preserve generated Strong ID `lateinit` and soft-delete rendering.

- [ ] **Step 8: Run GREEN/regressions**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --console=plain
if ($LASTEXITCODE -ne 0) { throw "Entity planner/renderer regression failed" }
```

- [ ] **Step 9: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat(pipeline): omit entrusted fields from entity construction"
```

---

## Task 6: Resolve supported aggregate Factory construction

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

The factory template already emits a real constructor call when `constructorMappingResolved=true`; do not add a new template branch.

- [ ] **Step 1: Turn current negative identity/version tests RED**

Change the resolved database-identity factory test and `factory planner falls back when strong id aggregate has read only version constructor field` to require:

```kotlin
assertEquals(true, factoryContext["constructorMappingResolved"])
assertEquals(listOf("title"), payloadFields.map { it["name"] })
assertEquals(listOf("title"), constructorPayloadFields.map { it["name"] })
assertTrue(constructorUnresolvedFields.isEmpty())
```

Add combinations: identity without version, identity + Short/Int/Long version, application Strong ID + version, and identity/version + existing soft-delete. Add a generic managed READ_ONLY required field case that remains unresolved. Assert no parentRef/child-spec context is introduced.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: identity/version still appear in `missingRequiredFields`, so supported mappings remain false.

- [ ] **Step 3: Compare payloads with actual constructor requirements**

Resolve the shared roles once and filter missing requirements explicitly:

```kotlin
val entrustedFields = AggregateEntrustedFieldPlanning.resolve(entity, model)

val missingRequiredFields = entity.fields
    .filterNot { ownStrongId != null && it.name == entity.idField.name }
    .filterNot { entrustedFields.isProviderAssigned(it.name) }
    .filterNot { it.name in payloadFieldNames }
    .filterNot { resolved && isSystemTransitionOnlyConstructorField(resolvedPolicy, it) }
    .filterNot { field -> hasConstructorDefault(entity, field, model, planning, defaultProjector) }
```

Delete version-specific fallback from `canDeferManagedConstructorField`. If an own-Strong-ID factory has an unrelated generic managed READ_ONLY missing field, keep it as an unresolved out-of-scope blocker rather than resolving it or throwing a new lifecycle policy. A helper used only to suppress the existing strong-ID fail-fast may recognize that blocker, but it must not remove the field from `missingRequiredFields`.

When the remaining list is empty, return:

```kotlin
ConstructorMapping(
    resolved = true,
    payloadFields = payloadFields,
    unresolvedFields = emptyList(),
)
```

- [ ] **Step 4: Run planner GREEN**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Factory planner tests failed" }
```

- [ ] **Step 5: Render and compile supported factories**

Extend the renderer compile test with identity/version Entity + Factory sources. Assert:

```kotlin
assertTrue(factory.contains("VideoPost("))
assertTrue(factory.contains("title = entityPayload.title"))
assertFalse(factory.contains("id ="))
assertFalse(factory.contains("version ="))
assertFalse(factory.contains("TODO(\"Implement aggregate construction\")"))
```

Keep one explicit unresolved generic managed-field fixture that still renders the existing TODO boundary; do not mislabel it as supported.

- [ ] **Step 6: Run renderer GREEN**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Factory renderer compilation failed" }
```

- [ ] **Step 7: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat(pipeline): resolve entrusted field factory mapping"
```

---

## Task 7: Close generation, Schema, and compilation evidence

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateProviderPersistenceCompileSmoke.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateRelationCompileSmoke.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `docs/public/reference/db-schema-annotations.md`

No Schema template change is expected. If forward joins fail, fix canonical/planner inputs rather than adding inverse or scalar fallback logic to the template.

- [ ] **Step 1: Make provider fixture demand a real identity factory**

Import/inject `VideoPostFactory`, create `VideoPostFactory.Payload(title = "identity")`, call `create`, and return the result from `AggregateProviderPersistenceCompileSmoke`. Update functional assertions for `VideoPost`:

```kotlin
assertFalse(internalConstructorParameters(generatedVideoPost).contains("id"))
assertFalse(internalConstructorParameters(generatedVideoPost).contains("version"))
assertTrue(generatedVideoPost.contains("var id: Long? = null"))
assertTrue(generatedVideoPost.contains("var version: Long? = null"))
assertEquals(true, factoryContexts.getValue("VideoPost").get("constructorMappingResolved").asBoolean)
assertFalse(generatedIdentityFactory.contains("TODO(\"Implement aggregate construction\")"))
```

- [ ] **Step 2: Expand relation fixture to the required graph**

Add `version bigint not null comment '@Managed=version;'` to root and owned entities. Add a third-level table with no FK constraint:

```sql
create table video_post_file_variant (
    id bigint primary key comment '@IdStrategy=db_identity;',
    version bigint not null comment '@Managed=version;',
    video_post_file_id bigint not null comment '@ParentRef;',
    variant_key varchar(128) not null
);

comment on table video_post_file_variant is '@Parent=video_post_file;';
```

Add the table to `includeTables`. Keep owned-one as unique `video_post_id` plus independent child `id`.

In aggregate artifacts configure exactly:

```kotlin
artifacts {
    unique.set(false)
}
```

This is required because parentRef-containing unique artifacts belong to the future addon; do not modify unique production code.

- [ ] **Step 3: Update compile smoke to forward-only use**

Remove `child.videoPost.id`. Exercise only:

```kotlin
entity.items.add(child)
entity.file = file
file.variants.add(variant)
entity.file?.variants?.firstOrNull()?.variantKey
```

Use actual generated relation names after inspecting the plan context; do not add child parent access to make the smoke compile.

- [ ] **Step 4: Change relation functional expectations**

Rename the old `keeps owned direct parent bindings scalar plus read only inverse relation` test to the final forward-only contract. Assert:

```kotlin
assertFalse(generatedChildEntity.contains("videoPostId"))
assertFalse(generatedChildEntity.contains("@ManyToOne"))
assertFalse(generatedChildSchema.contains("videoPostId"))
assertTrue(generatedRootEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
assertTrue(generatedRootSchema.contains("fun joinItems()"))
assertTrue(generatedRootSchema.contains("fun joinFile("))
assertTrue(generatedFileSchema.contains("fun joinVariants()"))
```

Also assert identity/version nullable Entity properties at every level, physical Schema version fields remain non-null typed, and no inverse imports appear solely due to owned parent binding.

- [ ] **Step 5: Run RED functional tests**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.aggregate provider specific persistence generation renders bounded controls" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate provider specific persistence generation participates in domain compileKotlin" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate relation generation keeps owned parent bindings forward only" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate schema owned relation joins compile for owned many owned one and chained children" --console=plain
```

Expected: before all fixture/test migrations settle, old factory/inverse/parentRef expectations or compile smoke fail. If the renamed test filter does not resolve yet, run the containing test class until the method has its final name.

- [ ] **Step 6: Make only fixture/assertion corrections and run GREEN**

Do not repair failures by restoring parentRef fields, inverse relations, or unique planner fallbacks.

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Pipeline functional generation/compilation failed" }
```

Expected: complete functional/compile classes pass, including application-side Strong ID regression and no-FK nested Schema joins.

- [ ] **Step 7: Commit**

Before committing, update the public DB-annotation reference with these user-visible facts:

- `@ParentRef` is structural owned-relation metadata and does not generate a child parent-ID scalar or inverse parent navigation by default;
- an owned child must have an independent primary key; parentRef-as-primary-key is rejected;
- `@IdStrategy=db_identity` and resolved `@Managed=version` fields are omitted from constructors/factory payloads and are readable nullable state until the provider assigns them;
- parent-owned persistence and Schema joins continue to use the physical parentRef column.

Do not document future parent access modes or owned-child specs as available.

```powershell
git add cap4k-plugin-pipeline-gradle/src/test docs/public/reference/db-schema-annotations.md
git commit -m "test(pipeline): compile entrusted owned graphs"
```

---

## Task 8: Prove real JPA/UoW identity and version completion

**Files:**

- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`
- Read only: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisorTest.kt`
- Read only: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Do not modify production UoW/factory-supervisor code.

- [ ] **Step 1: Add a generated-shape-equivalent runtime graph**

Append test-local `RuntimeEntrustedRoot`, `RuntimeEntrustedChild`, and `RuntimeEntrustedGrandchild` entities. At every level use:

```kotlin
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
open var id: Long? = null
    protected set

@Version
open var version: Long? = null
    protected set
```

Use parent-owned unidirectional `@OneToMany` + `@JoinColumn`, cascade `PERSIST/MERGE/REMOVE`, orphan removal, private backing collections, and `OwnedEntityList` facades matching generated persistence paths. Child/grandchild must not contain parent ID scalar or parent entity relation. On the test-local join columns use `foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT)` so Hibernate creates the physical columns without FK constraints; this is a DDL-fixture choice, not a generated annotation change.

- [ ] **Step 2: Add root-only CREATE and forward Criteria join proof**

Construct one root with children/grandchildren, call only:

```kotlin
unitOfWork.persist(root, PersistIntent.CREATE)
unitOfWork.save()
```

Assert every `id` and `version` is non-null, query physical rows to verify root/child join-column values, use JDBC metadata to assert the child/grandchild tables have no imported FK keys, then execute Criteria joins through the parent backing paths and assert the expected grandchildren. Do not assert version starts at exactly 0 or 1.

- [ ] **Step 3: Add existing-root/new-child proof**

After the first save/reset, load the root in `TransactionTemplate`, add a new identity/version child through the forward collection, register/persist the root as existing using the already-supported path, save, and assert the new child's ID/version and join column are non-null/correct. Do not call `persist(child, CREATE)` and do not add a child persistence API.

Add a separate outer-transaction rollback case: persist/flush a new root graph, record that provider-assigned IDs/versions became non-null, then throw to roll the transaction back. Assert rows were rolled back but the framework did not clear already assigned in-memory identity/version values. Do not add rollback cleanup production code.

- [ ] **Step 4: Run real H2 evidence**

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest" --console=plain
```

Expected: pass. If provider-assigned IDs/versions or unidirectional nested joins fail, capture the exact SQL/exception and stop at the design rollback trigger. Do not redesign UoW, add inverse navigation, or insert placeholder IDs inside this task.

- [ ] **Step 5: Run existing UoW/factory boundary evidence unchanged**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisorTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Factory supervisor boundary regressed" }
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Root-only UoW boundary regressed" }
```

The existing tests must still prove one root CREATE, explicit nested entries collapsing to the outer root, one `EntityManager.persist(root)`, no child/grandchild independent persist/merge, flush/refresh behavior, and no public child contract.

- [ ] **Step 6: Prove production runtime stayed untouched**

```powershell
git diff --exit-code c49e12f5 -- ddd-core/src/main ddd-domain-repo-jpa/src/main cap4k-ddd-starter/src/main
if ($LASTEXITCODE -ne 0) { throw "Runtime production code changed outside approved scope" }
```

- [ ] **Step 7: Commit**

```powershell
git add cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt
git commit -m "test(starter): prove entrusted owned graph persistence"
```

---

## Task 9: Final static, focused, and full verification

**Files:** Review all changed files. Modify only tests/implementation necessary to correct a demonstrated in-scope defect.

- [ ] **Step 1: Inspect the complete diff and protected areas**

```powershell
git status --short
git diff --stat c49e12f5...HEAD
git diff c49e12f5...HEAD -- cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle/src/test cap4k-ddd-starter/src/test
git diff --exit-code c49e12f5...HEAD -- ddd-core/src/main ddd-domain-repo-jpa/src/main cap4k-ddd-starter/src/main cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt
if ($LASTEXITCODE -ne 0) { throw "Protected production scope changed" }
```

Expected: only approved production/test areas changed; no unique/UoW runtime production diff.

- [ ] **Step 2: Run static deletion and retention scans**

```powershell
$inverse = & rg -n 'AggregateInverseRelationModel|aggregateInverseRelations|AggregateInverseRelationInference' cap4k-plugin-pipeline-api/src cap4k-plugin-pipeline-core/src cap4k-plugin-pipeline-generator-aggregate/src
if ($LASTEXITCODE -eq 0) { $inverse; throw "Inverse infrastructure remains" }
if ($LASTEXITCODE -ne 1) { throw "Inverse scan failed" }

$domainParent = & rg -n 'structuralParentRef|constructorStructuralFields|field\.parentRef|filter \{ it\.parentRef \}' cap4k-plugin-pipeline-generator-aggregate/src
if ($LASTEXITCODE -eq 0) { $domainParent; throw "Domain parentRef plumbing remains" }
if ($LASTEXITCODE -ne 1) { throw "ParentRef planner scan failed" }

rg -n 'parentRef' cap4k-plugin-pipeline-source-db/src cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedParentBindingResolver.kt cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt
if ($LASTEXITCODE -ne 0) { throw "Physical parentRef evidence is missing" }

$forbidden = & rg -n 'ApplicationSideId|snowflake-long|@MapsId|owned child spec|ManagedFieldLifecycle' cap4k-plugin-pipeline-api/src/main cap4k-plugin-pipeline-core/src/main cap4k-plugin-pipeline-generator-aggregate/src/main cap4k-plugin-pipeline-renderer-pebble/src/main
if ($LASTEXITCODE -eq 0) { $forbidden; throw "Out-of-scope compatibility/capability appeared" }
if ($LASTEXITCODE -ne 1) { throw "Forbidden capability scan failed" }
```

- [ ] **Step 3: Run focused verification set**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --console=plain
if ($LASTEXITCODE -ne 0) { throw "Focused pipeline verification failed" }
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Functional compilation verification failed" }
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisorTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Factory supervisor verification failed" }
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "UoW verification failed" }
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.AggregateJpaRuntimeDefectReproductionTest" --console=plain
if ($LASTEXITCODE -ne 0) { throw "Runtime verification failed" }
```

- [ ] **Step 4: Run repository-wide verification**

```powershell
.\gradlew.bat check --console=plain
if ($LASTEXITCODE -ne 0) { throw "Repository check failed; report exact failures and do not claim completion" }
```

Expected: green. Do not fix unrelated modules just to force completion.

- [ ] **Step 5: Self-review plan/spec adherence**

Confirm all of the following from code/tests, not memory:

- resolved identity/version roles are the only entrusted classifiers;
- provider/ID projection mismatches fail;
- database identity/version properties are nullable/null and constructor-free;
- generic managed READ_ONLY remains unchanged;
- soft-delete and Strong ID matrices remain green;
- parentRef/inverse are absent from domain surfaces but physical join evidence remains;
- shared PK fails, unique-parentRef + independent ID still yields owned-one;
- supported factories contain real constructors and no TODO;
- parent Schema forward/nested joins compile and run without child reverse navigation;
- root-only UoW and existing-root/new-child runtime paths assign all database IDs/versions;
- no protected production area changed.

- [ ] **Step 6: Preserve approved documents**

```powershell
$documentationBase = & git log -1 --format=%H -- docs/superpowers/plans/2026-07-27-cap4k-database-entrusted-fields-construction.md
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($documentationBase)) { throw "Cannot locate plan commit" }
git diff --exit-code $documentationBase -- docs/superpowers/specs/2026-07-26-cap4k-database-entrusted-fields-construction-design.md docs/superpowers/plans/2026-07-27-cap4k-database-entrusted-fields-construction.md
if ($LASTEXITCODE -ne 0) { throw "Approved plan/spec changed during implementation" }
```

- [ ] **Step 7: Commit final in-scope corrections, if any**

```powershell
git status --short
```

If Task 9 required a demonstrated correction, stage only those files and commit `fix(pipeline): close entrusted field verification gaps`. Otherwise create no empty commit.

## Rollback Triggers

Stop and return to design review instead of broadening implementation if any occurs:

1. Hibernate cannot persist the root-owned identity graph without a child scalar, inverse relation, `@MapsId`, or new UoW behavior.
2. Version cannot remain nullable at construction while provider initialization/increment works for supported integral types.
3. Parent Schema joins require the removed child scalar rather than forward JPA relation paths.
4. Factory mapping cannot resolve a supported identity/version combination without granting all READ_ONLY fields new lifecycle semantics.
5. Removing inverse infrastructure breaks a current accepted contract not expressible through the forward graph.
6. Soft-delete or Phase 4 Strong ID behavior must change to implement this design.
7. ParentRef-containing unique artifacts cannot be isolated by `artifact.unique=false` without editing unique production planners.

## Agent Handoff Notes

- Implement Tasks 0-9 in order and stop at every RED/GREEN checkpoint.
- Re-read changed files before each commit; do not stage the whole repository.
- Keep source comments brief and only where role separation is not self-evident.
- Do not “help” future issues `#137`/`#115`; absence of parent access is the final behavior for this iteration.
- Final handoff must list commits, focused/full command outcomes, static scan outcomes, and any skipped evidence. A compile-only generated TODO is not acceptable for a supported factory.
