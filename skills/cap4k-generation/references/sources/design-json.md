# Design JSON

Supported tags:

- `command`
- `query`
- `client`
- `api_payload`
- `domain_event`
- `integration_event`
- `validator`

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
- `integration_event` requires `role`, `eventName`, at least one `requestFields` entry, and empty `responseFields`.
- inbound `integration_event` entries can produce subscriber shells through `DesignIntegrationEventSubscriberArtifactPlanner`.
- manifest-file mode is allowed, and every manifest entry path must stay inside `projectDir`.
- Tags outside the supported set are not part of this generation input; `value_object` and `domain_service` stay first-class modeling concepts.
