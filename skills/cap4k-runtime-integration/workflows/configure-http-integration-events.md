# Configure HTTP Integration Events

1. Confirm the project uses the HTTP integration event adapter.
2. Confirm whether HTTP-JPA subscriber registry persistence is present.
3. Add only the required framework table DDL for the selected database.
4. Verify event scan package includes generated integration event classes.
5. Confirm inbound event classes do not use `[none]` / `IntegrationEvent.NONE_SUBSCRIBER` and resolve to the actual consuming service subscriber value.
6. Test the consume path or document the manual HTTP smoke path.
