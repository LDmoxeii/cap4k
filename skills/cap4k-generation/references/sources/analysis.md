# Analysis Sources

IR analysis:

- comes from `sources.irAnalysis`
- reads module `build/cap4k-code-analysis` directories
- requires `nodes.json` and `rels.json`
- optionally reads `design-elements.json`
- is for flow and drawing-board style observation after compile

Rule:

- IR analysis is not a normal business-source generation input.
- Missing IR analysis output is an analysis setup problem, not a missing business fact to route to modeling.