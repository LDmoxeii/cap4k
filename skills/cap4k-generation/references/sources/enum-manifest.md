# Enum Manifest

Minimal shape:

```json
[
  {
    "name": "VideoStatus",
    "package": "com.acme.demo.domain.video",
    "items": [
      { "value": 0, "name": "DRAFT", "desc": "draft" },
      { "value": 1, "name": "PUBLISHED", "desc": "published" }
    ]
  }
]
```

Rules:

- enum manifest supplies shared enums referenced by DB `@T=<TypeName>`.
- configure it with `types { enumManifest { files.from(...) } }`, not `sources.enumManifest`.
- enum manifest entries do not need matching `types.registryFile` entries.
- duplicate type names are rejected.
- enum translation is not a core aggregate-generation feature.
- `generateTranslation` stays removed; use an addon if translation artifacts are required.
