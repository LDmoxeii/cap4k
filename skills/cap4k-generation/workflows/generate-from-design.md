# Generate From Design

1. Confirm design JSON is the source of truth for use-case and interface contracts.
2. Allow only supported tags: `command`, `query`, `client`, `api_payload`, `domain_event`, `integration_event`, and `validator`.
3. Reject first-class `value_object` and `domain_service` as unsupported design generation.
4. Check common fields: package, name, desc, aggregates, requestFields, and responseFields.
5. For `integration_event`, require role, eventName, at least one request field, and empty response fields.
6. Check tag-specific rules: page traits, persisted domain events, validator fields, and manifest path safety.
7. Run `cap4kPlan`.
8. Review `plan.json` using `workflows/review-plan-json.md`.
9. Generate only after deciding which handlers and subscribers are project-owned.
10. Run affected compile/tests.
