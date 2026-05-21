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
- duplicate type names are rejected.
- enum translation is not a core aggregate-generation feature.
- `generateTranslation` stays removed; use an addon if translation artifacts are required.
