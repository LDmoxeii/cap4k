# Generator-Supported Skeletons

This reference lists skeleton families that must be treated as generator-owned structure when cap4k inputs can express them. Missing supported skeletons return to generator inputs or technical design. Do not handwrite a supported skeleton merely to make implementation proceed.

## Source Surfaces

Generator-supported structure can come from DB/schema, `design/design.json`, value-object manifest, enum manifest, Gradle extension configuration, addon/options, and approved template override decisions. The source surface varies by family; the gate question is whether the current project can express the intended structure through these inputs.

## Supported Families

| Family | Usual Input Surface | Agent Rule |
|---|---|---|
| command | `design/design.json` `command` block or aggregate-aligned design input | Use generated command and handler slots for state-changing use cases. |
| query | `design/design.json` `query` block | Keep read-only semantics; do not patch missing query skeletons by hand before plan review. |
| client | `design/design.json` `client` block | Treat application-facing external capability request shape as generated structure. |
| api payload | `design/design.json` `api_payload` block | Keep protocol DTO shape in adapter-facing generated payload slots. |
| domain event | `design/design.json` `domain_event` block, often aggregate-linked | Use for internal domain facts; do not convert to Integration Event to obtain a skeleton. |
| integration event | `design/design.json` `integration_event` block with inbound/outbound variant | Use for published language; inbound subscriber requires explicit supported selection. |
| subscriber | domain-subscriber or integration-subscriber artifact selection | Keep subscribers thin; missing subscriber returns to event design or artifact selection. |
| domain service | `design/design.json` `domain_service` block | Use when domain collaboration has no single aggregate owner. |
| saga | `design/design.json` `saga` block | Use only for recoverable, compensable, or persistent process coordination. |
| aggregate | DB/schema aggregate markers and aggregate generator input | Aggregate root structure is not handwritten when generator input can express it. |
| entity | DB/schema entity/table relation inference | Entity structure follows aggregate modeling and schema input. |
| factory | Aggregate generator family or design-supported factory skeleton | Keep creation policy in generated factory slots when available. |
| specification | Aggregate/specification generator family, unique helper input, or explicit design exception | Pre-save constraints return to input design when the expected skeleton is absent. |
| repository | Aggregate persistence generator family | Repository handles read/access/load; save ownership stays with Unit of Work. |
| controller | API/adapter generator family or addon-supported adapter input | Controller skeleton belongs to adapter protocol mapping, not business rule ownership. |
| job | Scheduled reaction, compensation, polling, or addon-supported job input | Missing job support needs technical design exception before handwritten structure. |
| projection | Aggregate-projection generator family or read-model input | Treat projections as generated/read ownership, not command-side aggregate shortcuts. |

## Missing Skeleton Rule

If an expected supported skeleton is absent from plan evidence:

1. Stop structural implementation.
2. Check whether the intended structure belongs in DB/schema, `design/design.json`, value-object manifest, enum manifest, Gradle extension configuration, addon/options, or a template override.
3. If the input can express it, return to generator inputs and regenerate only after plan review is allowed.
4. If the input cannot express it, return to technical design and record a handwritten structural exception before creating the skeleton.
5. If ownership is unclear, treat the change as blocked until generation review classifies the output family and conflict policy.