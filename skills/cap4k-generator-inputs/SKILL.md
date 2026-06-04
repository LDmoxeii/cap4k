---
name: cap4k-generator-inputs
description: >
  Use when projecting an approved cap4k technical design into generator inputs
  such as DB/schema, design JSON, manifests, Gradle extension settings,
  addons/options, or template override decisions.
---

# Cap4k Generator Inputs

Use this after the technical design contract exists and before plan review.

## Always Read

1. `../shared/rules/generator-input-source-of-truth.md`
2. `../shared/workflows/skeleton-generation-gate.md`
3. `workflows/project-generator-inputs.md`

## Common Routes

| Task | Read | Workflow |
|---|---|---|
| Choose the generator input surface | `references/input-surfaces.md` | `workflows/project-generator-inputs.md` |
| Update DB/schema, design JSON, manifests, Gradle settings, addons, or template decisions | `references/input-surfaces.md` | `workflows/project-generator-inputs.md` |

## Stop Conditions

- Technical design is missing or has unresolved carrier decisions.
- The requested input surface is unclear.
- The input would create structure not supported by the approved design.
- Plan review is requested before generator inputs are coherent.
