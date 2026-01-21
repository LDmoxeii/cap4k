# Aggregate Unit Generators Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace aggregate table generators with unit generators, centralize naming, and update GenAggregateTask to use the unit pipeline only.

**Architecture:** Add AggregateNaming and a complete set of AggregateUnitGenerator implementations mirroring existing aggregate generators. Update GenAggregateTask to collect units and render via GenerationPlan, then remove the old aggregate generator classes.

**Tech Stack:** Kotlin, Gradle, Pebble templates, existing cap4k codegen utilities.

### Task 1: Add shared naming utility and update existing unit generators

**Files:**
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/AggregateNaming.kt`
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/EnumUnitGenerator.kt`
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/EnumTranslationUnitGenerator.kt`
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/DomainEventUnitGenerator.kt`
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/DomainEventHandlerUnitGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/DomainEventNaming.kt`

**Step 1: Write the failing test**
- Skip (user request: no new tests).

**Step 2: Run test to verify it fails**
- Skip.

**Step 3: Write minimal implementation**
- Add AggregateNaming functions for all aggregate artifact names and suffix rules.
- Replace per-generator name logic with AggregateNaming calls.
- Remove DomainEventNaming and update references.

**Step 4: Run tests to verify it passes**
- Skip.

**Step 5: Commit**
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/AggregateNaming.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/EnumUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/EnumTranslationUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/DomainEventUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/DomainEventHandlerUnitGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/DomainEventNaming.kt`
- `git commit -m "refactor(codegen): centralize aggregate naming"`

### Task 2: Add unit generators for schema base and entity

**Files:**
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/SchemaBaseUnitGenerator.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/EntityUnitGenerator.kt`

**Step 1: Write the failing test**
- Skip (user request: no new tests).

**Step 2: Run test to verify it fails**
- Skip.

**Step 3: Write minimal implementation**
- Port logic from SchemaBaseGenerator and EntityGenerator to unit-based implementations.
- Use AggregateNaming for class names and export types.
- Keep existing import manager and context helpers.

**Step 4: Run tests to verify it passes**
- Skip.

**Step 5: Commit**
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/SchemaBaseUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/EntityUnitGenerator.kt`
- `git commit -m "feat(codegen): add schema base and entity unit generators"`

### Task 3: Add unit generators for unique query artifacts

**Files:**
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UniqueQueryUnitGenerator.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UniqueQueryHandlerUnitGenerator.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UniqueValidatorUnitGenerator.kt`

**Step 1: Write the failing test**
- Skip (user request: no new tests).

**Step 2: Run test to verify it fails**
- Skip.

**Step 3: Write minimal implementation**
- Port logic from UniqueQueryGenerator, UniqueQueryHandlerGenerator, UniqueValidatorGenerator.
- Generate one unit per unique constraint and model dependencies between query, handler, and validator units.
- Use AggregateNaming for class names and export types.

**Step 4: Run tests to verify it passes**
- Skip.

**Step 5: Commit**
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UniqueQueryUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UniqueQueryHandlerUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/UniqueValidatorUnitGenerator.kt`
- `git commit -m "feat(codegen): add unique query unit generators"`

### Task 4: Add remaining unit generators (spec, factory, repository, aggregate, schema)

**Files:**
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/SpecificationUnitGenerator.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/FactoryUnitGenerator.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/RepositoryUnitGenerator.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/AggregateWrapperUnitGenerator.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/SchemaUnitGenerator.kt`

**Step 1: Write the failing test**
- Skip (user request: no new tests).

**Step 2: Run test to verify it fails**
- Skip.

**Step 3: Write minimal implementation**
- Port logic from SpecificationGenerator, FactoryGenerator, RepositoryGenerator, AggregateGenerator, SchemaGenerator.
- Use AggregateNaming for class names and export types.
- Ensure schema unit uses schema base name and relevant type mappings.

**Step 4: Run tests to verify it passes**
- Skip.

**Step 5: Commit**
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/SpecificationUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/FactoryUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/RepositoryUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/AggregateWrapperUnitGenerator.kt`
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/unit/SchemaUnitGenerator.kt`
- `git commit -m "feat(codegen): add remaining aggregate unit generators"`

### Task 5: Update GenAggregateTask to use unit generators only

**Files:**
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenAggregateTask.kt`

**Step 1: Write the failing test**
- Skip (user request: no new tests).

**Step 2: Run test to verify it fails**
- Skip.

**Step 3: Write minimal implementation**
- Remove AggregateTemplateGenerator usage and table-step generation.
- Register all unit generators in order and use GenerationPlan ordering for each unit.
- Keep export type mapping logic for downstream generators.

**Step 4: Run tests to verify it passes**
- Skip.

**Step 5: Commit**
- `git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenAggregateTask.kt`
- `git commit -m "refactor(codegen): switch aggregate generation to unit pipeline"`

### Task 6: Remove obsolete aggregate generators

**Files:**
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/AggregateTemplateGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/AggregateGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SchemaBaseGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EnumGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EnumTranslationGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EntityGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/DomainEventGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/DomainEventHandlerGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueQueryGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueQueryHandlerGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueValidatorGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SpecificationGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/FactoryGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/RepositoryGenerator.kt`
- Delete: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SchemaGenerator.kt`

**Step 1: Write the failing test**
- Skip (user request: no new tests).

**Step 2: Run test to verify it fails**
- Skip.

**Step 3: Write minimal implementation**
- Remove the legacy generator classes after GenAggregateTask and imports are updated.
- Ensure no references remain (rg check).

**Step 4: Run tests to verify it passes**
- Skip.

**Step 5: Commit**
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/AggregateTemplateGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/AggregateGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SchemaBaseGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EnumGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EnumTranslationGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EntityGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/DomainEventGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/DomainEventHandlerGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueQueryGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueQueryHandlerGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueValidatorGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SpecificationGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/FactoryGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/RepositoryGenerator.kt`
- `git rm cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SchemaGenerator.kt`
- `git commit -m "chore(codegen): remove legacy aggregate generators"`