# Generate From Design

1. Confirm design JSON is the source of truth for use-case and interface contracts.
2. Allow only supported tags: `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, `domain_service`, and `saga`.
3. Reject first-class `value_object` and `validator` as unsupported core design generation.
4. Check common fields: package, name, description, aggregates, artifacts, fields, and resultFields.
5. For `integration_event`, require eventName, at least one field, empty resultFields, and an `integration-event` artifact with variant `inbound` or `outbound`.
6. Check tag-specific rules: page variants, persisted domain events, integration event artifact variants, domain-service/saga skeleton ownership, and manifest path safety.

## Surface Ownership Gate

- [ ] New command surfaces are represented in `design.json` when generation supports them.
- [ ] New query surfaces are represented in `design.json` when generation supports them.
- [ ] New domain event or subscriber surfaces are represented in `design.json` when generation supports them.
- [ ] New client and API payload surfaces are represented in `design.json` when generation supports them.
- [ ] New domain-service or saga skeletons are represented in `design.json` when generation supports them.
- [ ] Any handwritten surface has a stated reason why generation is not available for that surface.

7. Run `cap4kPlan`.
8. Review `plan.json` using `workflows/review-plan-json.md`.
9. Generate only after deciding which handlers and subscribers are project-owned.
10. Run affected compile/tests.
