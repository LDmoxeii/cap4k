---
name: cap4k-authoring
description: Use when an agent must inspect, model for, generate with, implement in, analyze, or verify a cap4k business project.
---

# Cap4k Authoring

This is a thin router and field guide, not a DDD process engine. Humans own domain research, language, boundaries, priorities, and final decisions.

## Start

1. Read `routing.yaml` and select the smallest matching operation route.
2. Generate or refresh the read-only Cap4k Agent API snapshot for the current project.
3. Read `build/cap4k/agent/manifest.json` first, then only the route's `agent_sections` whose manifest status makes them useful.
4. Load only the route's `required_reads`.

If Gradle fails before the Agent API task starts, use the ordinary Gradle failure as evidence and do not claim a snapshot exists. An `invalid` snapshot is diagnostic evidence and accompanies task failure; a `partial` snapshot may be a successful result with optional unavailable sections.

## Operating Contract

- Inspect actual project shape and capability state before choosing inputs or tasks.
- Use supported catalog to learn what the installed version can do and effective project state to learn what is ready here; never conflate them.
- Prepare supported inputs, review plan and diagnostics, then run mutation tasks. Do not handwrite a generator-supported parallel skeleton.
- Put durable business logic only in checked-in author-owned surfaces or explicit handwritten exceptions. Never edit build-owned generated source or generated evidence as source truth.
- Treat analyzer output as observation, not business truth or generator input.
- Keep Domain Events as explicit immutable historical facts; never relax the Aggregate/Entity payload boundary.
- Do not assume a provider or tactical carrier exists. Read the machine catalog and report unsupported/provider-owned boundaries honestly.
- Bootstrap is retired. Do not use or recreate bootstrap tasks, DSL, markers, guards, slots, aliases, or migration workflows.

`routing.yaml` is the only route table. Do not require strategic workspaces, fixed design dossiers, phase chains, rollback workflows, or cap4k-specific approval gates.
