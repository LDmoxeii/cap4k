# Generate From Design

1. Confirm design JSON is the source of truth for use-case and interface contracts.
2. Use supported tags only: `command`, `query`, `client`, `api_payload`, `domain_event`, and `validator`.
3. Preserve unsupported tags as gaps, not local framework behavior: `integration_event`, `value_object`, and `domain_service`.
4. Check common fields such as package, name, desc, aggregates, requestFields, and responseFields.
5. Check tag-specific rules: paged query/api payload traits, persisted domain events, validator targets/value type/parameters, and manifest path safety.
6. Run the generation plan before generation.
7. Inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
8. Generate only after deciding which handlers and skeletons are project-owned.
9. Run compile/tests for affected modules and report exact evidence.
