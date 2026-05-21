# Addons

Addons contribute artifact plan items. They do not replace business modeling inputs such as DDL, `design.json`, enum manifest, or `types.registryFile`.

Minimal shape:

```kotlin
dependencies {
    cap4kAddon("com.only4:engine-cap4k-addon:0.1.12-SNAPSHOT")
}

cap4k {
    templates {
        templateConflictPolicies.put(
            "addons/only-engine-enum-translation/aggregate/enum_translation.kt.peb",
            "OVERWRITE"
        )
    }
}
```

Rules:

- addon artifacts appear in the same `cap4kPlan` output as built-in artifacts
- addon outputs use the same ownership review fields: `generatorId`, `templateId`, `outputKind`, `conflictPolicy`, `resolvedOutputRoot`
- addon artifacts are reviewed in generation, not invented in implementation
