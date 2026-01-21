# Aggregate Unit Generator Migration Design

## Goal
Migrate aggregate code generation to unit-based generators so generation is granular, dependency-driven, and parallel-ready. Remove AggregateTemplateGenerator and move all aggregate-related output to AggregateUnitGenerator implementations with shared naming utilities.

## Background
The current pipeline mixes table-based generators with unit generators. This causes coarse granularity, complex shouldGenerate logic, and tight coupling between generators. It also makes dependency ordering fragile when one class depends on another class's generated name.

## Architecture
Introduce a single unit-based pipeline that collects GenerationUnit instances from AggregateUnitGenerator implementations, orders them by dependency and order, and renders them through a shared unit renderer. A centralized naming utility provides stable class name generation for all aggregate artifacts (entity, enum, unique query, domain event, etc), removing the need for per-generator overrides. AggregateTemplateGenerator and table-step execution are removed.

## Components
- AggregateUnitGenerator implementations for each artifact type: schema base, enum, enum translation, entity, unique query, unique query handler, unique validator, specification, factory, domain event, domain event handler, repository, aggregate wrapper, schema.
- AggregateNaming utility that resolves all class names and suffix rules in one place.
- GenerationPlan orders units by dependency (deps) and order, enabling parallel groups for same order.
- GenAggregateTask uses only unit generators and the unit rendering pipeline.

## Data Flow
1. Build aggregate context from builders (tables, modules, relations, enums, constraints, aggregate info, packages).
2. Collect units from all AggregateUnitGenerators.
3. Order units via GenerationPlan (deps first, then order, then insertion order).
4. Render each unit via TemplateNode merge/selection and export types into typeMapping.

## Error Handling
- Detect duplicate unit ids and warn on first-seen policy.
- If a dependency is missing, still generate but skip the missing edge (current behavior); future improvement could hard-fail on missing deps.
- Template rendering errors should surface with unit id/name context; IO errors should report the target path and unit id.

## Testing
Testing can focus on unit counts, dependency ordering, and naming outputs. Per user request, this change proceeds without adding new tests.

## Scope
In scope:
- New unit generators for all aggregate artifacts (replacing aggregate generators).
- Central naming utility for aggregate artifacts.
- GenAggregateTask updated to use only unit generators.
- Remove AggregateTemplateGenerator and aggregate generator classes once references are gone.

Out of scope:
- Import manager customization.
- Non-aggregate generation pipelines.