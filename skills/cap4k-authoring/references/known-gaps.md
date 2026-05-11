# Known Gaps

These gaps must be surfaced during design and review. Do not present them as completed framework capabilities.

## Authoring And Generator Gaps

- Value object authoring needs stronger public qualification.
- Value object generator support is not complete.
- Saga authoring needs stronger public qualification.
- Saga generator support is not complete.
- Domain service generator support is not complete.
- Integration event generator support is not complete.
- Design support for integration events needs issue tracking if absent in the current slice.
- Design support for value objects and domain services needs issue tracking if absent in the current slice.

## Modeling Gaps

- Layered model qualification needs continued refinement.
- Public tactical model qualification needs continued refinement.
- Command, query, cli, domain event, integration event, value object, and domain service should eventually be driven by design where supported.

## External Collaboration Gaps

- `drawing_board.json` can become a later integration-event communication surface.
- Addon and SPI guidance exists as a direction, but advanced authoring rules should expand only after real use.
