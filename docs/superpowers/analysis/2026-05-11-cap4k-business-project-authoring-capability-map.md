# cap4k Business Project Authoring Capability Map

Date: 2026-05-11

This is an internal capability analysis for rewriting public authoring docs and the `cap4k-authoring` AI skill. It is not a public authoring page and not a framework contributor guide.

## Target Reader Boundary

| Reader | In scope | Out of scope |
|---|---|---|
| Human author of a business project using cap4k | Decide model boundaries, review generated output, configure generator/runtime, audit AI implementation | cap4k framework issue governance, skill architecture meta rules, cap4k internal release process |
| AI author of a business project using cap4k | Turn agreed model/design/DDL into a runnable project, use generated skeletons correctly, run compile/test/analysis before human audit | Managing cap4k project issues, editing cap4k generator internals unless explicitly asked |
| cap4k framework author | Maintain runtime, generator, plugin, docs, skills | Should use a separate skill or guide from business-project authoring |

The current `skills/cap4k-authoring` draft mixes these readers. Anything about cap4k issue lifecycle, shell drift, runtime context bloat, or skill implementation pressure tests belongs to cap4k framework work, not to business project authoring.

## Capability Areas

| Area | Current support | Authoring implication |
|---|---|---|
| Minimal project bootstrap | `cap4kBootstrapPlan` / `cap4kBootstrap` with `ddd-multi-module`, slots, template override dirs | Teach how to produce a minimal runnable four-module project and where generated bootstrap ends |
| Gradle plugin configuration | `project`, `layout`, `sources`, `generators`, `templates`, `bootstrap`, `cap4kAddon` | Teach exact knobs, conflict policies, source/generator enablement, and generated-source wiring |
| Template override | Project `overrideDirs` are checked before addon and built-in presets; bootstrap has separate override dirs and slots | Teach same override mental model for built-in and addon templates |
| DB input | JDBC metadata, table/column comments, relations, enum annotations, managed/exposed fields, unique constraints | Teach DDL as generator contract, including annotation syntax and uniqueness naming discipline |
| Design input | JSON entries for command/query/client/api_payload/domain_event/validator | Teach design as explicit use-case/interface contract; note unsupported tags |
| Aggregate generation | Entity, schema, behavior, repository, shared/local enums, factory/specification/unique options | Teach which output is generated source, which is checked-in skeleton, and which artifacts are optional |
| Public tactical runtime | `Mediator`, repository supervisor, factories, domain services, UoW, specs, lifecycle listeners, events, requests | Teach canonical usage, especially static `Mediator.*`, command UoW, and factory-driven creation |
| Layering | Domain/application/adapter/start module paths plus physical package defaults | Teach responsibilities separately from physical handler placement |
| Integration events | Core annotations/supervisor plus HTTP/JPA/RabbitMQ/RocketMQ adapters | Teach publish/attach/consume flow, DB setup needs, and external contract sharing strategy |
| Analysis | compiler plugin emits `nodes.json`/`rels.json`/`design-elements.json`; pipeline generates flow/drawing-board | Teach analysis as verification/export after code compiles |
| Testing | Docs-first testing contract; domain/application behavior first; reference project has end-to-end smoke tests | Teach useful tests, not scaffolding accident or architecture-policing residue |
| SPI/addon | `ArtifactAddonProvider`, `cap4kAddon`, addon resources under `cap4k/addons/<id>/...` | Teach extension as a first-class generator path with same override/conflict semantics |

## Role Corrections

- Query handler and client/cli handler are physically generated into adapter packages by default. They represent application request handling responsibility, but their code location is adapter-side.
- Command handlers are the normal write-use-case boundary. They may use factories, repositories, domain services, specifications through UoW, and `Mediator.uow.save()`.
- Query handlers should wrap read access and are a good boundary for jobs or controllers that need read data.
- Client/cli handlers wrap external capability calls. Command handlers should not call them casually, but may do so when the command result depends on the external capability result.
- Process orchestration can live in application subscribers, jobs, or other application-facing flow code. These orchestration points can send commands, queries, and client/cli requests through `Mediator`.
- Event subscribers should have semantic method names when they contain business logic; generated default names are acceptable only as untouched skeletons.

## Current Public Authoring Gaps

- Public authoring docs are fragmented and do not yet provide a single business-project authoring path.
- Tactical concepts such as `Mediator`, built-in repository, factory, UoW, lifecycle listener, domain service, specification, and layer responsibility are not presented as one coherent contract.
- Bootstrap, design input, DB input, template override, slot usage, and generated-output verification are documented, but not yet assembled as a teachable end-to-end workflow.
- Integration event mechanics are not explained at a framework-flow level for business authors.
- Analysis plugin usage exists, but the connection between compile, `irAnalysis`, `cap4kAnalysisPlan`, `cap4kAnalysisGenerate`, and review artifacts needs a clearer authoring workflow.
- The business-project AI skill must become self-contained. It should not tell the agent to read all public docs or example projects during normal use.

## Known Product Gaps To Track

| Gap | Current state | Authoring consequence |
|---|---|---|
| Design input for integration events | `integration_event` design tag, role/eventName validation, contract generation, inbound subscriber skeleton, and drawing-board integration are supported | Teach integration-event contracts as design-driven generation; keep cross-service contract sharing guidance explicit |
| Design input for value objects/domain services | No `value_object` or `domain_service` design tags | These concepts are manual/modeling guidance today |
| Lifecycle recognition | Behavior template exposes `onCreate`/`onUpdate`/`onDelete`, but discovery behavior has known limitations | Keep lifecycle usage documented, but track framework defect separately |
| Enum translation | Removed from core aggregate artifact options; expected via addon | Reference projects must stop using stale core DSL and use addon path when needed |
| Integration event HTTP JPA tables | HTTP subscriber registry needs persistence schema when JPA register is present | Example projects need minimal H2-compatible DDL for required framework tables |

## Reuse Rule

Future public docs and AI skill rules should pull stable facts from these analysis files, then rewrite them for their reader. Do not copy internal issue history or one-off project incidents into business authoring materials.
