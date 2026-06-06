# Generator Input Source Of Truth

## Always True

- Treat DB/schema definitions as generator inputs.
- Treat design JSON as a generator input.
- Treat enum and value-object manifests as generator inputs.
- Treat Gradle extension settings as generator inputs.
- Treat addons, options, and template override decisions as generator inputs.
- Treat `plan.json`, generated output, generated snapshots, flow output, and drawing-board output as generated evidence.
- Treat analysis outputs as observation evidence by default, not ordinary source-generation input skeletons.
- Treat a manually copied analysis fragment as generator input only when it is placed on a supported input surface and satisfies the current contract.

## Drift Checks

- Prevent "plan.json is the business source of truth."
- Prevent treating analysis output as a supported generator input without transformation into a valid DB/schema, design JSON, manifest, Gradle setting, addon/option, or template decision.
- Prevent "generated evidence can replace generator inputs."
- Prevent "missing inputs should be patched by handwritten skeletons."
