# Cap4k only-danmuku Local Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the new cap4k pipeline modules to `mavenLocal()`, wire `only-danmuku` to the new plugin, and generate one smoke-test command and query.

**Architecture:** This plan keeps the validation loop intentionally narrow. `cap4k` remains the producer of the new plugin artifacts, while `only-danmuku` acts as a real consumer using only the current vertical-slice capabilities: `design-json`, `ksp-metadata`, and the design planner.

**Tech Stack:** Gradle, Kotlin DSL, Maven Local, JUnit-verified cap4k pipeline modules

---

### Task 1: Publish cap4k pipeline artifacts locally

**Files:**
- Reuse: `cap4k/*` published modules already on `master`

- [ ] Run pipeline module verification
- [ ] Publish all required `cap4k-plugin-pipeline-*` projects to `mavenLocal()`
- [ ] Confirm the plugin marker and module artifacts exist in `~/.m2/repository/com/only4`

### Task 2: Rewire only-danmuku for smoke validation

**Files:**
- Modify: `only-danmuku/build.gradle.kts`
- Create: `only-danmuku/iterate/pipeline-smoke/pipeline_smoke_gen.json`

- [ ] Replace the legacy codegen plugin usage at the root with `com.only4.cap4k.plugin.pipeline`
- [ ] Keep the root convention plugin intact
- [ ] Point the new plugin at the application module path and domain KSP metadata directory
- [ ] Limit the design input to a single smoke file

### Task 3: Run the smoke generation

**Files:**
- Inspect: `only-danmuku/build/cap4k/plan.json`
- Inspect generated output under `only-danmuku/only-danmuku-application/src/main/kotlin/edu/only4/danmuku/application`

- [ ] Run `cap4kPlan`
- [ ] Run `cap4kGenerate`
- [ ] Inspect the generated `Cmd/Qry`
- [ ] Record any real incompatibilities between the current vertical slice and the real project
