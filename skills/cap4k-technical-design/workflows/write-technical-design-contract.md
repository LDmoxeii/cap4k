# Write Technical Design Contract

## Inputs

- Coherent business brief from `cap4k-business-discovery`.
- Approved carrier selection table from `cap4k-tactical-modeling`.
- Shared layer/runtime boundaries and Skeleton Generation Gate.

## Procedure

1. Copy every heading from `../references/technical-design-contract.md`.
2. Fill each heading from the business brief and tactical carrier table.
3. Keep unresolved facts in `openDecisions`; do not hide them in assumptions.
4. For each expected skeleton, answer the Skeleton Generation Gate questions before editing generator inputs or handwritten files.
5. For each carrier, record layer placement, generator input surface, handwritten slot, verification evidence, and rollback trigger.
6. Move carrier mismatches back to `cap4k-tactical-modeling`.
7. Move missing or unsupported generator input questions to the generator input phase only after the contract identifies the intended carrier.

## Required Completion Gate

- Every heading from `../references/technical-design-contract.md` is present.
- Every heading has content or a blocking open decision.
- No generator input authoring starts while a blocking carrier or skeleton decision remains open.
- Skeleton expectations distinguish generated structure from handwritten logic slots.
- Ownership exceptions are explicit, not inferred from compile pressure.

## Rollback

- `unclear carrier -> cap4k-tactical-modeling`
- `missing input -> cap4k-generator-inputs`
- `implementation bypass -> cap4k-technical-design`
- `structure drift -> earliest phase that introduced wrong assumption`
