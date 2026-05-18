# Source Of Truth And Skeleton Rules

- Implementation only fills already existing, ownership-clear generated or project-owned skeletons.
- Implementation does not create generator-capable skeletons.
- If a missing surface is generator-capable and the relevant facts already exist, stop and return to `cap4k-generation`.
- If the missing piece is a generation input contract, stop and return to `cap4k-modeling`.

## Generation Sources

- SQL schema / DDL is the source of truth for aggregate, repository, factory, specification, enum, field mapping, relation, and unique-helper skeletons.
- `design.json` is the source of truth for `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, subscriber, and validator surfaces.
- `types.registryFile`, enum manifest, and KSP metadata are generation input contracts even though they are not all declared inside one `sources {}` block.

## Required Fallback

- Missing `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, client, validator, payload, domain event, integration event, or subscriber skeleton: return to `cap4k-generation`.
- Missing aggregate, repository, factory, specification, enum, relation, field-mapping, or unique-helper skeleton when DDL/type facts already exist: return to `cap4k-generation`.
- Missing design entry, DDL annotation, enum manifest definition, `types.registryFile` entry, or KSP metadata: return to `cap4k-modeling`.

## Handwritten Exception

- Handwrite a surface only when the current generator does not support it.
- State the unsupported-generation reason in review notes or the final note.
