# Source Of Truth And Skeleton Rules

- Implementation only fills already existing, ownership-clear generated or project-owned skeletons.
- Implementation does not create generator-capable skeletons.
- If a missing surface is generator-capable and the relevant facts already exist, stop and return to `cap4k-generation`.
- If the missing piece is a missing business input fact, stop and return to `cap4k-modeling`.
- If the missing piece is KSP metadata output/config/setup that generation depends on, stop and return to `cap4k-generation`.

## Generation Sources

- SQL schema / DDL is the source of truth for aggregate-family skeletons such as aggregate, repository, factory, specification, enum, and unique-helper outputs. Relation and field-mapping facts stay inside aggregate/entity generation input rather than standalone plan families.
- `design.json` is the source of truth for supported design contracts such as `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `domain_service`, and `saga`; core design JSON does not generate validator surfaces.
- Subscriber shells and generated handler shells are derived from supported request or event contracts rather than a standalone subscriber design tag.
- `types.enumManifest`, `types.valueObjectManifest`, and `types.registryFile` entries are business input facts even though they are not declared inside `sources {}`.
- KSP metadata is a generation/setup input produced by compile configuration rather than a modeling fact.

## Required Fallback

- Missing `*Cmd.kt`, `*Qry.kt`, `*QryHandler.kt`, `*CliHandler.kt`, client, payload, domain event, integration event, domain service, saga, or subscriber skeleton: return to `cap4k-generation`.
- Missing aggregate, repository, factory, specification, enum, or unique-helper skeleton when DDL/type facts already exist: return to `cap4k-generation`.
- Missing relation or field-mapping behavior after DDL/type facts already exist: treat it as aggregate/entity generation drift, not a standalone skeleton family, and return to `cap4k-generation`.
- Missing design entry, DDL annotation, `types.enumManifest`, `types.valueObjectManifest`, or `types.registryFile` entry: return to `cap4k-modeling`.
- Missing KSP metadata output/config/setup that current generation requires: return to `cap4k-generation`.

## Handwritten Exception

- Handwrite a surface only when the current generator does not support it.
- State the unsupported-generation reason in review notes or the final note.
