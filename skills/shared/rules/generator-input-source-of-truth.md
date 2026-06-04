# Generator Input Source Of Truth

## Always True

- Treat DB and schema definitions as generator inputs.
- Treat design JSON as a generator input.
- Treat manifests as generator inputs.
- Treat Gradle extension settings as generator inputs.
- Treat addons and options as generator inputs.
- Treat plan.json as generated evidence.
- Treat analysis outputs as observation evidence.
- Keep runtime guidance self-contained.

## Drift Checks

- Prevent "plan.json is the business source of truth."
- Prevent "analysis output is an input skeleton."
- Prevent "generated evidence can replace generator inputs."
- Prevent "missing inputs should be patched by handwritten skeletons."
