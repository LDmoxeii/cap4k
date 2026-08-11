# Runtime Console Retirement

## Target outcome

Cap4k shall not ship an operations console, administrative UI, or framework-owned administrative HTTP API. Reliable Command/Event recovery remains a programmatic Runtime capability, and consuming applications own any authenticated operator surface they choose to build.

## Module and build surface

- The `cap4k-ddd-console` module does not exist in the repository.
- Gradle settings, root or aggregate builds, dependency metadata, publication/release configuration, tests, and fixtures do not reference or discover `cap4k-ddd-console`.
- No alias, deprecated artifact, empty compatibility module, replacement artifact, or migration bridge preserves the retired module coordinate or package surface.

## Removed operations surface

- No Console auto-configuration or auto-configuration metadata is packaged.
- No Console-owned service or HTTP handler exposes Command search, Command retry/redrive, Event search, Event retry/redrive, or Locker unlock.
- Console-owned direct SQL query/mutation services are removed together with the HTTP layer that used them.
- Cap4k publishes no unauthenticated administrative endpoint and no framework-owned operations UI.

## Preserved Runtime boundaries

- Existing programmatic reliable Command/Event recovery APIs remain owned by their current Runtime modules and continue to enforce their existing state-machine eligibility and transition rules.
- This retirement does not change reliable Command/Event persistence, claim, retry, lease, terminal-state, or redrive semantics.
- Locker was retired by the Runtime surface cleanup; Console retirement does not restore a Locker module, API, schema, or administrative operation.
- A consuming application that exposes operational recovery owns authentication, authorization, redaction, operator audit, and network policy at its own boundary.

## Documentation and Agent facts

- Active public documentation does not advertise `cap4k-ddd-console`, Console endpoints, Console direct SQL services, or Console operations as supported capabilities.
- Machine-readable and generated Agent facts/manifests do not list Console as an available Runtime module, provider, operation, or dependency.
- Historical design records may retain accurate historical context, but they must not be used by active guidance or Agent facts to claim that Console is currently supported.

## Verification contract

- Focused Runtime tests prove that the retained programmatic reliable Command/Event recovery API remains available after Console deletion.
- Build/settings checks prove that no Console project, task, artifact, dependency, auto-configuration, test, or publication surface remains.
- Repository stale-surface checks prove that active code, build files, tests, public docs, and Agent facts contain no Console capability references.
- The repository required check passes for the complete implementation scope.

## Non-goals

- Reliable Command/Event state-machine redesign.
- Replacement Locker APIs, compatibility bridges, or distributed coordination redesign.
- Any other finding from the broader Runtime capability reset.
- Compatibility aliases, deprecated APIs, dual implementations, fallback codecs, or migration bridges.
