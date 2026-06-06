# cap4k Documentation System Redesign

Date: 2026-06-01

Status: Proposed

Scope: define the shared architecture for the cap4k documentation redesign across #98, #99, and #100. This is a design packet entry point, not an implementation plan and not a rewrite of `analysis`, `public`, or `skills` content.

## Backlog Source

This design covers the shared architecture behind these open issues:

- #98: `analysis: rebuild internal agent-facing project map from code`
- #99: `docs: rebuild public README and user documentation from an outline`
- #100: `skills: rebuild cap4k authoring skills as an agent execution system`

The user explicitly rejected a gradual patch-by-patch documentation cleanup. The target is a full outline-first reconstruction: define the reader, structure, chapter responsibility, source-of-truth policy, deletion policy, and verification policy before rewriting any body content.

## Worktree And Branch Rule

cap4k work must not be committed directly on `master`.

All design and rewrite work for this redesign must happen in an isolated worktree and branch. The initial design worktree is:

- branch: `spec/documentation-system-redesign`
- worktree: `cap4k/.worktrees/documentation-system-redesign`

Future agents must continue from an isolated worktree or create a new one. Do not switch the main `cap4k` worktree away from `master` to perform this work.

## Chosen Approach

Use three separated documentation layers with strong interfaces:

| Layer | Primary reader | Friendliness target | Core job |
| --- | --- | --- | --- |
| `docs/superpowers/analysis/` | cap4k maintenance agents and human maintainers | 70% AI-friendly, 30% human-maintainer-friendly | Current code map and fact index |
| `README.md` + `docs/public/` | human cap4k users | 100% human-friendly | Project story, concepts, authoring guide, examples, reference |
| `skills/` | agents authoring business systems with cap4k | 100% AI-friendly | Self-contained execution system for cap4k business authoring |

This design deliberately does not use a single book for all readers. Mixing these readers would make `analysis` too prose-heavy, make `public` too internal, and make `skills` too dependent on external context.

## Reader Boundaries

### analysis

`analysis` is not public documentation. It is a maintenance map for future cap4k work.

It should answer:

- what exists now;
- where the source-of-truth code and tests are;
- which Gradle tasks, DSL keys, source IDs, generator IDs, output paths, runtime modules, and release flows are current facts;
- what to inspect before changing a capability;
- what to verify after changing it;
- which stale terms and historical assumptions must not be trusted.

It should not explain cap4k to ordinary users, replace code reading, or preserve historical snapshots. If a page becomes a long implementation essay, it should usually become a source-anchor table plus invariants and verification steps.

### public

`public` is for human users. It should establish the cap4k mental model before sending users into DSL or reference details.

It should answer:

- what cap4k is;
- why it exists;
- who should use it and who should not;
- what its differentiators are;
- how DDD tactical modeling, generator-backed authoring, generated skeletons, handwritten logic, tests, and analysis outputs fit together;
- how to start from a business problem and reach working cap4k code.

It should not expose internal maintenance notes, historical issue context, stale implementation details, or agent-only execution gates as the main user story.

### skills

`skills` is an installed cap4k skills bundle for agents. It must be self-contained at bundle runtime.

A business-authoring agent using this bundle must not be required to read:

- `docs/superpowers/analysis/`;
- `docs/public/`;
- GitHub issues;
- historical specs/plans;
- Context7;
- the cap4k source repository.

The bundle can be maintained from code, analysis, and public docs, but the published skill instructions must carry the rules, workflows, gates, gotchas, and validation steps needed to execute.

## Fact And Visibility Chains

There are two separate chains.

Maintenance chain:

```text
code -> analysis -> public rewrite
code + public contract + cap4k tactical rules -> skills rewrite
```

Runtime chain:

```text
human user -> README/docs/public
maintenance agent in cap4k repo -> analysis + code
business-authoring agent -> installed cap4k skills bundle
agent with Context7 -> optional public-doc lookup acceleration
```

Rules:

- Code is the final source of truth.
- `analysis` is a current fact index, not a second source of truth.
- `public` may use `analysis` during maintenance, but public docs must be self-contained for human readers.
- `skills` may use code, public docs, and analysis during maintenance, but installed skills must run without those external documents.
- If documentation conflicts with code, code wins and the documentation must be corrected.

## Context7 Boundary

Context7 compatibility should be considered now, but Context7 submission and verification are not part of this phase.

Public docs should be Context7-ready:

- stable titles;
- short pages with one primary topic;
- clear Gradle, Kotlin, JSON, and DSL examples;
- code blocks with language markers;
- no internal analysis/spec/plan material in the public path;
- a future `context7.json` can select `README.md` and `docs/public/` and exclude internal material.

Context7 must not become a hard dependency for `skills`. Skills may mention Context7 as an optional acceleration path only. Context7 results must not outrank the installed skills bundle's own gates and rules.

## Language And Media Rules

This design packet follows the repository's existing spec convention and is written in English. The language rules below apply to the rewritten `analysis`, `public`, and `skills` documentation, not to this design packet itself.

Target rewritten repository documentation uses Chinese prose with code identifiers preserved in English, except that `skills` may be all English if that improves AI execution quality.

Rules:

- Gradle tasks, DSL keys, JSON tags, module names, class names, packages, file paths, artifact families, generator IDs, source IDs, and skill names stay in code form.
- `public` introduces core concepts as Chinese concept plus English term where useful, such as `聚合 aggregate` or `生成计划 cap4kPlan`.
- `analysis` can be terse and table-heavy.
- `skills` may be all English if that improves AI execution. Human readability is not a goal for skills.
- No bilingual parallel documentation is required in this phase.

Public docs may contain image-generation prompt placeholders. These placeholders are for future human maintainers and must not carry required information. A public image placeholder must include purpose, suggested type, prompt, must-show points, must-avoid points, and future alt text.

Do not put image-generation prompt placeholders in `skills`. `analysis` may use Mermaid or structural diagrams only when they improve navigation.

## Aggressive Rewrite Policy

This is an aggressive redesign. Existing paths are not preservation constraints.

Allowed actions in later implementation phases:

- delete existing analysis, public, or skill files;
- move or rename files;
- merge or split pages;
- remove stale sections without archiving them;
- redesign skill names and boundaries.

Do not keep dual documentation tracks. Do not create low-value archive directories just to preserve old pages. Git history, issues, specs, and plans already preserve historical context.

## Retention And Deletion Criteria

Retain or migrate content only when it satisfies the new reader and structure.

Retain when content:

- serves the target reader;
- fits a named chapter or workflow in the new outline;
- matches current code facts;
- provides a source anchor, example, gate, invariant, concept explanation, gotcha, or verification value;
- reduces future drift, implementation error, or review churn;
- is in the correct layer.

Delete or rewrite when content:

- conflicts with current code;
- is historical process, old plan, old gap inventory, or issue background;
- needs issue/spec/plan context to make sense;
- exists only because the path already existed;
- duplicates a rule maintained elsewhere;
- is correct but stored in the wrong layer;
- makes `analysis` a prose implementation essay instead of a code map;
- puts internal maintenance context into `public`;
- makes `skills` depend on external documents, repo paths, Context7, or historical issues;
- gives agent advice without a gate, trigger, failure handling, or acceptance check.

Old content should be rewritten by purpose, not copied verbatim.

## Skills Workflow Philosophy

The skills bundle must not be a linear generic DDD checklist.

Target motion:

```text
business intent
 -> cap4k-aware tactical interpretation
 -> technical design
 -> generator input projection
 -> plan/generation review
 -> handwritten implementation
 -> verification/audit
 -> forced rollback to earlier phases when mismatch appears
```

The bundle must encode forced rollback. Later phases are not just execution; they are also tests of earlier assumptions.

The highest priority gate is:

> Skeletons are generated by cap4k. Complex business logic is implemented inside generated skeletons. Do not leave that structure unless the technical design explicitly decides to. If implementation discovers the need to leave it, stop implementation and return to design.

The detailed gate belongs in the skills outline and later skills detailed design.

## Design Packet Structure

This design is intentionally split into four files so future agents can load only the part they need after context compaction:

- `2026-06-01-cap4k-documentation-system-redesign.md`: this shared design and cross-layer policy.
- `2026-06-01-cap4k-analysis-redesign.md`: detailed #98 analysis redesign.
- `2026-06-01-cap4k-public-docs-outline.md`: coarse #99 public docs outline.
- `2026-06-01-cap4k-skills-workflow-outline.md`: coarse #100 skills workflow outline.

Each file must be self-contained enough for a new-context agent to continue without reading this conversation.

## Revised Execution Sequence

The sequence accounts for context compaction and hallucination risk.

```text
Phase 0: design packet
  1. shared documentation-system design
  2. detailed analysis redesign
  3. coarse public docs outline
  4. coarse skills workflow outline

Phase 1: analysis
  5. rewrite analysis from the detailed analysis redesign
  6. audit analysis output against current code

Phase 2: public
  7. write detailed public docs design from shared design + analysis result + public outline
  8. rewrite README/docs/public
  9. audit public output against current code and analysis result

Phase 3: skills
  10. write detailed skills design from shared design + analysis result + public result + skills outline
  11. rewrite skills bundle
  12. run validation/dry-run/global drift audit
```

Later specs must assume they will be read in a new context. They must repeat the necessary constraints, inputs, non-goals, deletion standards, and handoff instructions.

## Non-Goals

- Do not rewrite `analysis`, `public`, or `skills` in this design packet.
- Do not submit cap4k to Context7 in this phase.
- Do not require Context7 or GitHub issue access for installed skills.
- Do not preserve old documentation paths as a hard constraint.
- Do not write a complete public manual in the shared design.
- Do not finalize the exact skills bundle directory in the shared design.
- Do not make historical specs/plans part of the default reading path.

## Acceptance Criteria

- The shared design defines the three readers and three documentation layers.
- The shared design distinguishes maintenance chain from runtime chain.
- The shared design states that code is the final source of truth.
- The shared design states that skills must be self-contained at bundle runtime.
- The shared design includes Context7 compatibility without making Context7 a dependency.
- The shared design permits aggressive deletion, movement, and renaming.
- The shared design defines retention/deletion criteria.
- The shared design requires future specs to be self-contained for new-context agents.
- Companion specs exist for analysis, public outline, and skills outline.

## Handoff For Future Agents

Start with this file only to recover cross-layer policy. Then read exactly one companion spec based on the next task:

- For #98 implementation, read `2026-06-01-cap4k-analysis-redesign.md`.
- For #99 detailed design, read `2026-06-01-cap4k-public-docs-outline.md` plus the final analysis result.
- For #100 detailed design, read `2026-06-01-cap4k-skills-workflow-outline.md` plus the final analysis and public results.

Do not continue from conversation memory. Treat these specs, current code, and issue bodies as the handoff state.