# Service Integration Gotchas

- Controller is not the architecture concept; Open Host Service is the boundary concept.
- Callback controller is an external fact entry implementation, not a normal write surface.
- RPC is a transport name. Use Open Host Service when describing the business boundary.
- OSS, S3, payment, SMS, and media processing are external capabilities behind client ports.
- Application contracts should say resource storage or media storage, not OSS bucket or objectKey.
- A write use case should not be split into entry calls client first and command later.
- External facts may enter through HTTP callback, MQ listener, or integration event subscriber, but all state advancement still converges to command.
