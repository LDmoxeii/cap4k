# Cap4k Pipeline DSL Consolidation Design

## Summary

This design consolidates the public Gradle configuration surface of the new pipeline plugin into a single breaking-change DSL:

```kotlin
cap4k {
  project { ... }
  sources { ... }
  generators { ... }
  templates { ... }
}
```

The current flat `cap4kPipeline` extension is removed. The plugin will no longer infer source and generator activation from the presence of random paths or strings. Instead, each built-in source and generator gets an explicit `enabled` switch and a dedicated typed configuration block. Gradle will convert this DSL into a single `ProjectConfig` object for pipeline core consumption.

This slice intentionally does not change pipeline core execution order, does not add new source or generator capabilities, and does not improve template quality. It is a Gradle API cleanup slice.

## Why This Slice

The pipeline migration has already landed the main capability slices:

1. `design-json + ksp-metadata + design generator`
2. `db + aggregate generator`
3. `ir-analysis + flow generator`
4. `ir-analysis + drawing-board generator`

What remains unstable is the repository-facing configuration surface.

Current problems:

- the Gradle extension is still a flat collection of unrelated properties
- source and generator activation is inferred implicitly from whether a field happens to be populated
- configuration validation is scattered through plugin code instead of being centralized
- the current extension name exposes internal implementation language (`pipeline`) instead of the product surface (`cap4k`)

This makes the next stage of migration harder because every new capability would increase public configuration drift. Consolidating the DSL now gives a stable external contract before deeper output-quality work begins.

## Goals

- Replace `cap4kPipeline { ... }` with `cap4k { ... }`
- Replace the current flat extension object with nested typed blocks
- Require explicit enablement of built-in sources and generators
- Centralize DSL-to-`ProjectConfig` conversion in one place
- Centralize validation, defaults, and cross-dependency checks
- Keep `cap4kPlan` and `cap4kGenerate` as the only public tasks
- Keep pipeline execution order fixed and non-configurable
- Prove the DSL through unit tests and Gradle functional tests

## Non-Goals

- Add new pipeline stages
- Allow repository users to customize execution order
- Add custom source or generator registration from build scripts
- Change pipeline core APIs or renderer behavior
- Rework design-generator quality features such as `use()`, `imports()`, `type()`, auto-imports, default values, or nested types
- Migrate checked-in real projects such as `only-danmuku` in the same slice
- Introduce an external YAML or JSON config file system
- Preserve backward compatibility with the current flat extension

## Scope Decision

Three approaches were considered:

1. strong typed nested DSL plus a single `ProjectConfig` conversion layer
2. nested DSL backed by generic named containers for sources and generators
3. Gradle only points at an external config file and does not own the configuration model

The recommended choice is option 1.

Reasons:

- it provides the clearest repository-user API
- IDE completion and discoverability are best with typed nested blocks
- built-in sources and generators are already known and fixed
- it keeps Gradle concerns out of pipeline core by translating once into `ProjectConfig`
- it avoids mixing this cleanup slice with a new config-file system

Option 2 is more generic but weaker for usability. Option 3 may be valuable later, but it would expand scope and delay stabilization of the Gradle contract.

## Public DSL Design

The plugin keeps the existing plugin id in this slice, but changes the extension name.

- plugin id remains `com.only4.cap4k.plugin.pipeline`
- extension name becomes `cap4k`
- old extension name `cap4kPipeline` is removed

The new top-level DSL becomes:

```kotlin
cap4k {
  project { ... }
  sources { ... }
  generators { ... }
  templates { ... }
}
```

### `project`

Purpose:

- define repository-level base package and output module locations

Recommended shape:

```kotlin
project {
  basePackage.set("com.example.demo")
  applicationModulePath.set("demo-application")
  domainModulePath.set("demo-domain")
  adapterModulePath.set("demo-adapter")
}
```

Rules:

- `basePackage` is always required
- module paths remain repository-relative filesystem paths
- module paths are required only when a generator that needs them is enabled

### `sources`

Built-in source blocks:

- `designJson`
- `kspMetadata`
- `db`
- `irAnalysis`

Recommended shape:

```kotlin
sources {
  designJson {
    enabled.set(true)
    files.from("design/design.json")
  }

  kspMetadata {
    enabled.set(true)
    inputDir.set("demo-domain/build/generated/ksp/main/resources/metadata")
  }

  db {
    enabled.set(false)
  }

  irAnalysis {
    enabled.set(false)
  }
}
```

Each built-in source has an explicit `enabled` property. No source is activated implicitly from parameter presence.

### `generators`

Built-in generator blocks:

- `design`
- `aggregate`
- `drawingBoard`
- `flow`

Recommended shape:

```kotlin
generators {
  design {
    enabled.set(true)
  }

  aggregate {
    enabled.set(false)
  }

  drawingBoard {
    enabled.set(false)
  }

  flow {
    enabled.set(false)
  }
}
```

Export-style generators keep their own output directories:

```kotlin
generators {
  drawingBoard {
    enabled.set(true)
    outputDir.set("design-pipeline")
  }

  flow {
    enabled.set(true)
    outputDir.set("flows-pipeline")
  }
}
```

### `templates`

Purpose:

- control preset, overrides, and file conflict behavior

Recommended shape:

```kotlin
templates {
  preset.set("ddd-default")
  overrideDirs.from("codegen/templates")
  conflictPolicy.set("SKIP")
}
```

Defaults:

- `preset = ddd-default`
- `conflictPolicy = SKIP`
- `overrideDirs = empty`

## Extension Object Model

The Gradle module should replace the current flat extension with a small object graph:

- `Cap4kExtension`
- `ProjectExtension`
- `SourcesExtension`
- `DesignJsonSourceExtension`
- `KspMetadataSourceExtension`
- `DbSourceExtension`
- `IrAnalysisSourceExtension`
- `GeneratorsExtension`
- `DesignGeneratorExtension`
- `AggregateGeneratorExtension`
- `DrawingBoardGeneratorExtension`
- `FlowGeneratorExtension`
- `TemplatesExtension`

Responsibilities:

- extension objects only hold typed Gradle properties
- they do not assemble `ProjectConfig`
- they do not execute validation logic beyond basic property typing

This keeps all behavioral rules in one later conversion step.

## Enablement Semantics

The DSL should apply the same enablement contract to every built-in source and generator.

### `enabled = false`

- the block does not participate in validation
- missing or empty parameters do not matter
- no pipeline config entry is produced

### `enabled = true`

- the block enters strict validation
- all required parameters must be present
- cross-dependencies must be satisfied
- a config entry is emitted into `ProjectConfig`

This is intentionally stricter than the current implicit style. The goal is to make configuration intent explicit and validation predictable.

## Validation Rules

Validation should be centralized and performed when Gradle assembles `ProjectConfig`.

### Project Rules

- `basePackage` is required for every run
- `applicationModulePath` is required when `design` is enabled
- `domainModulePath` is required when `aggregate` is enabled
- `adapterModulePath` is required when `aggregate` is enabled

### Source Rules

`sources.designJson`

- when enabled, `files` must not be empty

`sources.kspMetadata`

- when enabled, `inputDir` is required

`sources.db`

- when enabled, required:
  - `url`
  - `username`
  - `password`
- optional:
  - `schema`
  - `includeTables`
  - `excludeTables`

`sources.irAnalysis`

- when enabled, `inputDirs` must not be empty

### Generator Rules

`generators.design`

- requires `project.applicationModulePath`
- requires enabled `designJson`
- may additionally consume enabled `kspMetadata`

`generators.aggregate`

- requires enabled `db`
- requires `project.domainModulePath`
- requires `project.adapterModulePath`

`generators.flow`

- requires enabled `irAnalysis`
- requires `outputDir`

`generators.drawingBoard`

- requires enabled `irAnalysis`
- requires `outputDir`

### Cross-Dependency Rules

If a generator is enabled and its required source is disabled, Gradle should fail fast during config assembly. It should not silently skip output.

Examples:

- `aggregate.enabled = true` and `db.enabled = false` -> fail
- `drawingBoard.enabled = true` and `irAnalysis.enabled = false` -> fail
- `flow.enabled = true` and `irAnalysis.enabled = false` -> fail

## `ProjectConfig` Assembly

Gradle should introduce a dedicated converter, for example:

- `Cap4kProjectConfigFactory`

Responsibilities:

- read values from `Cap4kExtension`
- apply defaults
- validate enabled blocks
- validate cross-dependencies
- normalize filesystem paths
- emit a fully materialized pipeline `ProjectConfig`

The factory is the only place in the Gradle module that should understand:

- default preset behavior
- default conflict policy
- enabled versus disabled semantics
- project path requirements
- source and generator dependency rules

`PipelinePlugin` itself should stop owning these rules directly.

## Task Behavior

Public tasks remain:

- `cap4kPlan`
- `cap4kGenerate`

Task responsibilities do not change:

`cap4kPlan`

- assembles `ProjectConfig`
- runs the pipeline through planning
- writes `build/cap4k/plan.json`
- does not export generated files

`cap4kGenerate`

- assembles `ProjectConfig`
- runs the full pipeline including export
- writes generated artifacts to their target locations

The DSL should determine which sources and generators are active. Tasks should not interpret missing strings or directories as activation signals.

## Task Dependency Behavior

The current task dependency approach remains conceptually valid, but trigger conditions change to use explicit enablement.

Recommended rules:

- if `design` is enabled and `kspMetadata` is enabled, pipeline tasks depend on relevant `kspKotlin`
- if `flow` or `drawingBoard` is enabled and `irAnalysis` is enabled, pipeline tasks depend on relevant `compileKotlin`
- if `aggregate` is enabled and `db` is enabled, no compile-time dependency is added

This keeps task wiring predictable and avoids accidental dependency activation from stray configuration values.

## Implementation Shape

This slice should focus only on the Gradle module.

### Files And Areas To Replace

Main target:

- [PipelineExtension.kt](/C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt)

Main target:

- [PipelinePlugin.kt](/C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt)

Main tests:

- [PipelinePluginFunctionalTest.kt](/C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt)

### Recommended Refactor Steps

1. replace flat extension classes with nested DSL classes
2. introduce `Cap4kProjectConfigFactory`
3. move validation and config assembly out of `PipelinePlugin`
4. update task dependency inference to use explicit enablement
5. rewrite functional-test fixtures to the new DSL

## Testing Strategy

This slice should be verified with unit tests plus Gradle functional tests.

### Factory And Extension Tests

Add focused tests for:

- enabled blocks requiring mandatory properties
- disabled blocks being ignored even if incomplete
- default template settings
- generator-source cross-dependency failures
- module path validation tied to enabled generators

### Gradle Functional Tests

Functional fixtures should cover at least:

- design-only configuration
- aggregate-only configuration
- flow-only configuration
- multiple generators enabled together
- invalid configuration failing during config assembly

Each fixture build script should use the new `cap4k { ... }` DSL only. No transitional coverage is needed for the old flat extension because this slice intentionally breaks compatibility.

## Migration Impact

This slice is intentionally breaking.

Repository users will need to change:

- extension name from `cap4kPipeline` to `cap4k`
- flat property assignments to nested block configuration

Repository users will not need to change:

- plugin id
- public task names
- pipeline core behavior
- source and generator ids used internally by the plugin

Because this is a breaking slice, documentation and sample fixtures should be updated in the same implementation plan.

## Risks And Mitigations

### Risk: Validation logic stays duplicated

If some rules remain in `PipelinePlugin` and others move into the config factory, the new DSL will still be hard to reason about.

Mitigation:

- make `Cap4kProjectConfigFactory` the only assembly and validation entry point
- keep `PipelinePlugin` thin

### Risk: Too much Gradle API leaks into pipeline config

If `Property`, `DirectoryProperty`, or `ConfigurableFileCollection` survive past assembly, core boundaries weaken again.

Mitigation:

- convert everything to plain Kotlin values inside the config factory
- let pipeline core see only `ProjectConfig`

### Risk: Over-scoping into implementation changes outside Gradle

Because this slice changes public configuration, it may be tempting to also rewrite core or generator internals.

Mitigation:

- keep changes inside `cap4k-plugin-pipeline-gradle`
- only touch tests and fixtures outside that module when required for verification

## Follow-Up Work

After this DSL consolidation slice, the next planned area remains design-generator quality improvement:

- `use()`
- `imports()`
- `type()`
- auto-imports
- nested-type expansion
- default-value handling

That work should build on the stabilized Gradle configuration contract rather than proceed in parallel with it.
