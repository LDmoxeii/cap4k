# Design JSON

`design.json` defines supported request, client, payload, event, domain-service, and saga generation contracts. It does not replace aggregate modeling or DB carrier decisions.

Supported tags:

- `command`
- `query`
- `client`
- `api_payload`
- `domain_event`
- `integration_event`
- `domain_service`
- `saga`

Common fields:

- `package`
- `name`
- `description`
- `aggregates`
- `artifacts`
- `fields`
- `resultFields`

Minimal example:

```json
[
  {
    "tag": "command",
    "package": "content.write",
    "name": "SubmitContentForReviewCmd",
    "description": "submit content for review",
    "aggregates": ["Content"],
    "artifacts": [{ "family": "command" }],
    "fields": [{ "name": "contentId", "type": "String" }],
    "resultFields": []
  },
  {
    "tag": "integration_event",
    "package": "media.processing",
    "name": "MediaProcessedIntegrationEvent",
    "description": "media processed",
    "eventName": "media.processed",
    "artifacts": [{ "family": "integration-event", "variant": "inbound" }],
    "fields": [{ "name": "taskId", "type": "String" }],
    "resultFields": []
  }
]
```

Rules:

- `query` and `api_payload` may use artifact variants such as `list` or `page`.
- `domain_event` entries can produce domain-event subscriber/handler shells through `DesignDomainEventHandlerArtifactPlanner`.
- `domain_event` supports `persist`, may omit `package`, and must declare exactly one aggregate. Public `fields` do not include a synthetic aggregate entity; missing or empty `aggregates` means the modeling input is incomplete and should return to modeling.
- `integration_event` requires an `integration-event` artifact with `variant` `inbound` or `outbound`, `eventName`, at least one `fields` entry, and empty `resultFields`.
- inbound `integration_event` entries can produce subscriber shells through `DesignIntegrationEventSubscriberArtifactPlanner`.
- `domain_service` entries produce domain-module service skeletons.
- `saga` entries produce application-module param, result, and handler skeletons.
- manifest-file mode is allowed, but rejects blank `manifestFile`, empty manifest content, blank entries, duplicate entries, and paths that escape `projectDir`.
- Tags outside the supported set are not part of this generation input; `value_object` belongs to `types.valueObjectManifest`, and `validator` stays addon-owned if a project needs validator artifacts.
