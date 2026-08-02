# Analysis Metadata Contract

## Purpose

The analysis metadata contract is a compile-time-only, lossless carrier used by the Cap4k compiler Analyzer and its Drawing Board and Flow Analysis consumers. It is not a DDD runtime contract.

## Module and dependency contract

- The annotations are published from the dedicated `cap4k-analysis-metadata` module.
- Their package is `com.only4.cap4k.analysis.metadata`.
- Business modules that compile metadata-bearing generated source receive the artifact on `compileOnly`, not `implementation` or runtime classpaths.
- `ddd-core` does not contain or re-export these annotations.

## Annotation contract

`DesignBlockMetadata` marks generated authoring-source top-level design carriers and retains the lossless tag, logical name, authoring-relative package, description, aggregate ownership, event name, artifact family, and variant fields.

`AggregateElementMetadata` marks generated aggregate-derived top-level carriers and retains aggregate name, logical name, package, description, element type, and root identity.

Both annotations:

- target classes only;
- use BINARY retention;
- have no runtime behavior;
- have no aliases for the removed `BuildingBlock` or `AggregateElement` names.

## Generator and template contract

- The default `ddd-default` templates emit the corresponding annotation on every currently metadata-bearing top-level carrier.
- User override templates may omit the annotation. Doing so explicitly opts the affected symbol out of Drawing Board recovery and/or metadata-dependent Flow Analysis.
- Templates do not introduce a sidecar skeleton metadata index.

## Analyzer and consumer contract

- Analyzer defaults use the new annotation FQNs and diagnostics use the new names.
- Existing Analyzer outputs may carry explicit missing-metadata evidence alongside graph observations; they do not fabricate missing authoring values.
- Drawing Board and Flow Analysis must fail before planning or rendering any apparently complete result when required metadata is missing.
- Legacy flow export applies the same fail-fast rule for its Flow Analysis input.

## Diagnostic contract

A metadata-loss failure must include:

1. every missing symbol known to the Analyzer input;
2. the required metadata annotation for that symbol;
3. each requested capability affected (`Drawing Board`, `Flow Analysis`);
4. restoration guidance: restore the default Cap4k template or add the corresponding annotation and ensure `cap4k-analysis-metadata` is on the business module compile-only classpath.

No consumer may silently omit the symbol or render an unlabeled partial result.

## Verification contract

Verification covers annotation retention/target, absence from `ddd-core`, default template output, compile-only dependency wiring, Analyzer extraction and metadata-loss evidence, Drawing Board fail-fast, pipeline Flow fail-fast, legacy flow-export fail-fast, generated module compilation, and cross-module Flow Analysis behavior.
