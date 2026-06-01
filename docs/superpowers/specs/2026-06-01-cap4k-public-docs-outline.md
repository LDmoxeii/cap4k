# cap4k Public Documentation Outline

Date: 2026-06-01

Status: Proposed Outline

Scope: coarse design for #99, rebuilding `README.md` and `docs/public/` as human-facing cap4k documentation. This is not the final detailed public-doc design. The final design must be written after the #98 analysis rewrite is complete.

## Backlog Source

This outline supports:

- #99: `docs: rebuild public README and user documentation from an outline`
- Shared design: `docs/superpowers/specs/2026-06-01-cap4k-documentation-system-redesign.md`
- Analysis design: `docs/superpowers/specs/2026-06-01-cap4k-analysis-redesign.md`

## Reader And Purpose

Public docs are 100% human-friendly.

They should help a human user understand:

- what cap4k is;
- why it exists;
- what problem it solves in DDD tactical implementation;
- what is distinctive about the generator-backed authoring model;
- how generated skeletons and handwritten business logic cooperate;
- how to use cap4k from business modeling through verification.

Public docs must not require issue history, specs/plans, `analysis`, or skills knowledge.

## Current Problems To Fix

Known problems from the drift audit:

- README is too weak as a public entry point and does not fully explain the current public surface.
- Public docs are fragmented around prior page boundaries instead of a coherent reader journey.
- Some paths and terms are stale, including malformed analysis output paths such as `build/cap4k code analysis` and `build/cap4k/analysis plan.json`.
- Some generator DSL examples omit required DB fields such as `username` and `password`.
- Code-analysis guidance has stale enabled-switch language for flow/drawing-board.
- Public docs do not yet read like an article-grade explanation of cap4k's purpose, highlights, concepts, workflow, examples, and reference.

## Coarse Structure

The final public detailed design may change names, but it should follow this conceptual shape:

```text
README.md
docs/public/
  index.md
  concepts/
  authoring/
  generator/
  examples/
  reference/
```

### README.md

Role:

- project positioning;
- who cap4k is for;
- core highlights;
- minimal entry path;
- documentation map.

README must not become the full manual.

### docs/public/index.md

Role:

- public documentation front door;
- reader path by intent: new user, project author, generator user, reference lookup, advanced concept reader;
- explain how to navigate the docs without needing internal context.

### concepts/

Role:

- build the cap4k mental model before DSL details;
- explain DDD tactical framework concepts in cap4k terms;
- explain generator-backed authoring as architecture control, not just scaffolding.

Likely topics:

- what cap4k is;
- domain/application/adapter;
- aggregate, command, query, event, repository, Unit of Work;
- generated skeleton vs handwritten logic;
- cap4k vs generic DDD terminology;
- when not to use cap4k.

### authoring/

Role:

- explain the human workflow from business problem to code;
- show the default happy path;
- make the generation-first philosophy understandable without exposing agent-only gates.

Likely flow:

```text
business intent
 -> cap4k tactical model
 -> technical design
 -> generator input
 -> cap4kPlan review
 -> cap4kGenerate
 -> handwritten logic in generated skeletons
 -> tests and analysis review
```

### generator/

Role:

- teach the generator and Gradle plugin accurately;
- split bootstrap, inputs, planning, generation, generated-source output, analysis output, and ownership.

Must match current code facts from #98 analysis.

Likely topics:

- plugin id and setup;
- `cap4kBootstrapPlan` / `cap4kBootstrap`;
- DB/design/types/addon inputs;
- `cap4kPlan` / `cap4kGenerate`;
- `cap4kGenerateSources`;
- `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`;
- plan review and ownership.

### examples/

Role:

- demonstrate complete slices tied to concepts;
- avoid isolated API snippets that do not teach cap4k authoring.

Examples should be picked after analysis and public detailed design. A good example shows business intent, model, generator input, generated skeleton, handwritten logic, and verification.

### reference/

Role:

- lookup tables and exact contracts;
- no concept teaching unless needed to disambiguate a field.

Likely topics:

- Gradle DSL;
- task table;
- design JSON schema;
- output paths;
- artifact families;
- common errors.

## Writing Principles

- Chinese prose with code identifiers preserved in English.
- Explain concepts before mechanics.
- Prefer article-grade clarity over terse internal notes.
- Keep pages scoped so Context7 can retrieve useful chunks later.
- Do not publish historical or internal uncertainty as user-facing truth.
- Do not describe the generator as generating business decisions.
- Do not make skills execution gates the public story.
- Public docs may use friendly examples, diagrams, and analogies if they do not distort the framework contract.

## Image Prompt Placeholder Policy

Public docs may include future image-generation placeholders. These are optional human-maintainer aids.

Use this format:

```markdown
<!-- IMAGE_PROMPT:
Purpose: <why this image helps this page>
Type: <architecture diagram | workflow diagram | editorial illustration | concept map>
Prompt: <generation prompt>
Must show: <required concepts>
Must avoid: <misleading imagery or false contracts>
Alt text after insertion: <future alt text>
-->
```

Rules:

- The surrounding prose must remain complete without the image.
- The prompt must say what not to imply. For example, do not imply cap4k generates business logic automatically.
- Use images for human mental models: architecture layers, generator-backed authoring workflow, generated skeleton vs handwritten logic, command/query/event flow, and human/agent/generator collaboration.
- Do not use image prompt placeholders in `skills`.
- Do not use generated illustration placeholders in `analysis`.

## Context7-Ready Requirements

The final public rewrite should be ready for future Context7 indexing, but this phase does not submit or verify cap4k in Context7.

Requirements:

- stable page titles;
- one topic per page;
- examples with complete code blocks;
- language markers on code fences;
- no internal `analysis`, issue, spec, or plan dependency;
- reference pages that are lookup-friendly;
- avoid burying important DSL facts inside long narrative pages.

Possible future `context7.json` direction:

- include `README.md` and `docs/public/`;
- exclude `docs/superpowers/analysis/`, `docs/superpowers/specs/`, `docs/superpowers/plans/`, and internal worktree material.

This is a future task, not a current acceptance criterion.

## Non-Goals

- Do not write the final public detailed design before #98 analysis is complete.
- Do not rewrite public docs in this outline phase.
- Do not preserve current public file paths as a hard constraint.
- Do not require readers to understand historical specs/plans.
- Do not make public docs a skills execution manual.
- Do not make Context7 submission or verification part of #99 rewrite acceptance.

## Inputs For The Later Detailed Public Design

When the later public detailed design starts, read:

1. `2026-06-01-cap4k-documentation-system-redesign.md`
2. this outline
3. the completed #98 analysis result
4. #99 issue body
5. current `README.md` and `docs/public/`
6. current code anchors for any public contract being documented

The later detailed design must be self-contained. It must not depend on this conversation.

## Acceptance Criteria For The Later Detailed Public Design

The detailed design should define:

- exact README outline;
- exact `docs/public/` directory tree;
- concept chapter list;
- authoring workflow chapter list;
- generator/reference split;
- example strategy;
- image prompt placeholder locations and standards;
- Context7-ready checklist;
- deletion/migration strategy for old public files;
- code-fact verification checklist.

## Handoff For Future Agents

This file is only a coarse outline. Do not implement #99 from this file alone. First finish #98 and then write the detailed public docs design using the completed analysis maps as fact input.