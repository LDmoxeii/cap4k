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
- `desc`
- `aggregates`
- `requestFields`
- `responseFields`

Minimal example:

```json
[
  {
    "tag": "command",
    "package": "content.write",
    "name": "SubmitContentForReviewCmd",
    "desc": "submit content for review",
    "aggregates": ["Content"],
    "requestFields": [{ "name": "contentId", "type": "String" }],
    "responseFields": []
  },
  {
    "tag": "integration_event",
    "package": "media.processing",
    "name": "MediaProcessedIntegrationEvent",
    "desc": "media processed",
    "role": "inbound",
    "eventName": "media.processed",
    "requestFields": [{ "name": "taskId", "type": "String" }],
    "responseFields": []
  }
]
```

Rules:

- `query` and `api_payload` may use request trait `page`.
- `domain_event` entries can produce domain-event subscriber/handler shells through `DesignDomainEventHandlerArtifactPlanner`.
- `domain_event` supports `persist`, may omit `package`, and reserves request field name `entity`, which is derived from the first aggregate entry. If `aggregates` is missing or empty, the design contract is incomplete and should return to modeling.
- `integration_event` requires `role`, `eventName`, at least one `requestFields` entry, and empty `responseFields`.
- inbound `integration_event` entries can produce subscriber shells through `DesignIntegrationEventSubscriberArtifactPlanner`.
- `domain_service` entries produce domain-module service skeletons.
- `saga` entries produce application-module param, result, and handler skeletons.
- manifest-file mode is allowed, but rejects blank `manifestFile`, empty manifest content, blank entries, duplicate entries, and paths that escape `projectDir`.
- Tags outside the supported set are not part of this generation input; `value_object` belongs to `types.valueObjectManifest`, and `validator` stays addon-owned if a project needs validator artifacts.
