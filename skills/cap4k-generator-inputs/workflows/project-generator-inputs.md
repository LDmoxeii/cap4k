# Project Generator Inputs

1. Read the technical design contract.
2. Read `../references/input-surfaces.md` before editing generator inputs.
3. Identify the required generator input surface.
4. Confirm the surface is one of DB/schema, `design/design.json`, value-object manifest, enum manifest, Gradle extension, addons/options, or template override decisions.
5. Update input only when the design supports the carrier, placement, ownership, and expected skeleton.
6. Return to technical design if a carrier is unclear.
7. Return to `cap4k-tactical-modeling` if the business concept no longer maps cleanly to a cap4k carrier.
8. Run or request cap4kPlan only after inputs are coherent.

## Evidence To Record

- Technical design section that authorizes the input.
- Input file or setting that carries the generator fact.
- Expected plan item or skeleton family.
- Rollback target if the input cannot express the design.
