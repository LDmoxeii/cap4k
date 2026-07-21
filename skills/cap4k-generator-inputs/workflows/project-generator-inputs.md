# Project Generator Inputs

Use this workflow to project an approved technical design into cap4k generator inputs.

## Steps

1. Read the approved technical design contract.
2. Scan the current workspace for mature project inputs and historical iteration materials relevant to the requested change, such as `design/*.json`, schema DDL, manifests, Gradle extension blocks, committed plan evidence, and prior authoring materials. If relevant examples are present, read only those examples; if absent, continue.
3. Read `../references/input-surfaces.md`.
4. Identify the required generator input surface.
5. Load the specific contract reference for the selected surface:
   - `sources.designJson.files`: read `../references/design-json-contract.md`.
   - DB/schema DDL comments: read `../references/db-schema-annotations.md`.
   - Enum or value-object manifests: read `../references/manifest-contracts.md`.
6. Update the input only when the design supports the carrier, placement, ownership, and expected skeleton.
7. Return to technical design if the carrier, package, owner, or expected skeleton is unclear.
8. Return to `cap4k-tactical-modeling` if the business concept no longer maps cleanly to cap4k.
9. If `scripts/validate-cap4k-generator-inputs.py` exists at the repository root, run it against the changed generator inputs. If it is absent, disclose the validator absence and perform a static self-check with the loaded contract references.
10. Do not claim generator inputs are ready while validation reports `ERROR`.

## Evidence To Record

- Technical design section that authorizes the input.
- Input file or setting that carries the generator fact.
- Contract reference used.
- Expected plan item or skeleton family.
- Validation result or validator absence disclosure.
- Rollback target if the input cannot express the design.
