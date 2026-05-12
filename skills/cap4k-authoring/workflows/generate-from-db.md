# Generate From DB

1. Confirm the DDL is the source of truth for aggregate modeling.
2. Review table annotations: `@Parent`, `@AggregateRoot`, table-backed `@ValueObject`, `@Ignore`, dynamic insert/update.
3. Review column annotations: `@Type`, `@Enum`, `@GeneratedValue`, `@Deleted`, `@Version`, `@Managed`, `@Exposed`, insertable/updatable flags.
4. Classify custom value fields: use `@T` + `types.registryFile` + converter for JSON-backed or inline values; reserve `@VO` for intentional table-backed values.
5. Review relation annotations: `@Reference`, `@Relation`, `@Lazy`, and `@Count`; reject many-to-many assumptions.
6. Check uniqueness names and decide whether unique query, handler, and validator helpers should be generated.
7. Run the generation plan before generation.
8. Inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
9. Generate only after classifying build-owned source, checked-in skeletons, copied snapshots, and handwritten targets.
10. Run compile/tests for affected modules and report exact evidence.
