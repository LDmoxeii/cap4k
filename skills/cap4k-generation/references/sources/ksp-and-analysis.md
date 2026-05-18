# KSP And Analysis

KSP metadata:

- comes from `sources.kspMetadata`
- reads aggregate metadata files such as `aggregate-*.json`
- supports design-driven generation when the planner needs existing aggregate metadata

IR analysis:

- comes from `sources.irAnalysis`
- reads module `build/cap4k-code-analysis` directories
- requires `nodes.json` and `rels.json`
- optionally reads `design-elements.json`
- is for flow and drawing-board style observation after compile

Rule:

- KSP metadata is a generation input.
- Missing KSP metadata is a generation/setup input problem, not a missing business fact to route to modeling.
- IR analysis is not a normal business-source generation input.
