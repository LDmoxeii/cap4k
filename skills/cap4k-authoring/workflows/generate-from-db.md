# Generate From DB

1. Confirm the DDL is the source of truth for aggregate modeling.
2. Review table annotations: `@Parent`, `@AggregateRoot`, `@ValueObject`, `@Ignore`, dynamic insert/update.
3. Review column annotations: `@Type`, `@Enum`, `@GeneratedValue`, `@Deleted`, `@Version`, `@Managed`, `@Exposed`, insertable/updatable flags.
4. Review relation annotations: `@Reference`, `@Relation`, `@Lazy`, and `@Count`; reject many-to-many assumptions.
5. Check uniqueness names and decide whether unique query, handler, and validator helpers should be generated.
6. Run the generation plan before generation.
7. Inspect `generatorId`, `templateId`, `outputPath`, `outputKind`, `conflictPolicy`, and `resolvedOutputRoot`.
8. Generate only after classifying build-owned source, checked-in skeletons, copied snapshots, and handwritten targets.
9. Run compile/tests for affected modules and report exact evidence.
