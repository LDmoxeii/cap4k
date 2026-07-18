# Generation Stop Policy

- Generation may run only after plan review gate allows it.
- After generation, stop for human review of generated diff, ownership, and plan-output alignment.
- Do not continue into handwritten implementation after generation.
- Resume implementation only after the user explicitly authorizes it.
