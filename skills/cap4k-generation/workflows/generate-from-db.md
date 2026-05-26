# Generate From DB

1. Confirm DDL is the source of truth for aggregate generation.
2. Review table annotations: `@Parent`, `@AggregateRoot`, `@ValueObject`, `@Ignore`, dynamic insert/update.
3. Review column annotations: `@Type`, `@Enum`, `@GeneratedValue=identity`, `@GeneratedValue=database-identity`, `@Deleted`, `@Version`, `@Managed`, `@Inherited`; reject stale `@Id`, marker `@GeneratedValue`, `@GeneratedValue=uuid7`, `@GeneratedValue=snowflake-long`, `@Exposed`, `@Insertable`, and `@Updatable` assumptions.
4. Classify custom value fields as `@T` plus type registry, inline field, JSON-backed value, or table-backed `@VO`.
5. Review relation annotations: `@Reference`, `@Relation`, `@Lazy`, `@Count`; reject many-to-many assumptions.
6. Review unique constraint names and decide whether unique query, handler, and validator helpers are useful.
7. Run `cap4kPlan`.
8. Review `plan.json` using `workflows/review-plan-json.md`.
9. Run `cap4kGenerate` or `cap4kGenerateSources` only after ownership classification.
10. Run affected compile/tests.
