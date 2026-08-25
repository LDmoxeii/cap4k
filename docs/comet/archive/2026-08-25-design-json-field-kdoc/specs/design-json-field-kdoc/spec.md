# Design JSON field KDoc

## Requirement: field descriptions reach generated Kotlin KDoc

### Scenario: request field description
- **GIVEN** a supported design entry contains a field object with `name`, `type`, and a non-blank `description`
- **WHEN** cap4k parses the Design JSON and generates the corresponding Kotlin design type
- **THEN** the canonical field retains the description
- **AND** the generated Kotlin field is preceded by a KDoc block containing the sanitized description
- **AND** the field name, rendered type, nullability, default value, order, and imports remain unchanged.

### Scenario: result field description
- **GIVEN** a supported design entry contains `resultFields` with a non-blank field `description`
- **WHEN** cap4k generates the corresponding result fields
- **THEN** each described result field receives the same KDoc projection rules as request fields.

### Scenario: nested field description
- **GIVEN** a design field resolves to a nested canonical value definition whose fields contain descriptions
- **WHEN** the generator renders nested Kotlin value types
- **THEN** described nested fields receive KDoc using the same sanitizer and omission rules.

### Scenario: absent or blank description
- **GIVEN** a field has no `description`, an empty description, or whitespace-only description
- **WHEN** the field is rendered
- **THEN** no empty KDoc block is generated
- **AND** the existing field output remains unchanged.

### Scenario: unsafe KDoc text
- **GIVEN** a field description contains `*/`
- **WHEN** cap4k renders the field KDoc
- **THEN** the generated source remains syntactically valid
- **AND** the closing sequence is sanitized consistently with existing description handling.

### Scenario: backward-compatible parsing
- **GIVEN** existing Design JSON without field descriptions
- **WHEN** it is parsed and generated
- **THEN** parsing succeeds and existing generated semantics remain unchanged.

### Scenario: analyzer boundary
- **GIVEN** generated Kotlin contains field KDoc from Design JSON descriptions
- **WHEN** the code-analysis pipeline processes the generated source
- **THEN** Analyzer continues to use design metadata annotations as its description input
- **AND** it does not need to parse KDoc or ordinary comments.

## Acceptance

- A1: parser/model preservation is covered by focused source-provider/model tests.
- A2: render model and sanitizer behavior is covered by focused generator tests, including missing, blank, multiline, and `*/` descriptions.
- A3: every default design template that emits request/result/nested fields projects field KDoc without changing existing field code.
- A4: cap4k full relevant test suites and a clean build pass.
- A5: an integration fixture with `design.json` field descriptions generates compilable Kotlin containing the expected KDoc.
- A6: Analyzer boundary and backward compatibility are explicitly tested or verified.
