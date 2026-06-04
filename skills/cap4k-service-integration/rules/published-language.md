# Published Language

Published Language is the stable boundary language external readers use to understand a service capability, request, response, error, or Integration Event.

- Treat boundary names, fields, status meanings, and error meanings as compatibility contracts.
- Do not expose internal Aggregate shape as the external contract.
- Do not mirror Entity fields, persistence names, or internal Domain Event payloads just because they exist.
- Use terms external consumers can safely rely on; keep provider-specific or protocol-specific words out unless they are part of the public business vocabulary.
- Record versioning, compatibility, deprecation, and consumer-impact decisions in technical design before generator input or handwritten implementation work.
