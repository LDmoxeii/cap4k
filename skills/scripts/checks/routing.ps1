$ErrorActionPreference = 'Stop'

function Get-RouteBlock {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Text,
    [Parameter(Mandatory = $true)]
    [string] $RouteId
  )

  $escapedRouteId = [regex]::Escape($RouteId)
  $match = [regex]::Match($Text, "(?ms)^\s*-\s+id:\s+$escapedRouteId\s*\r?\n.*?(?=^\s*-\s+id:\s+|\z)")
  if (-not $match.Success) {
    throw "Missing route in routing.yaml: $RouteId"
  }

  return $match.Value
}

$routingPath = 'skills/cap4k-authoring/routing.yaml'
$routeMapPath = 'skills/cap4k-authoring/references/route-map.md'

if (-not (Test-Path -LiteralPath $routingPath -PathType Leaf)) {
  throw "Missing cap4k authoring routing manifest: $routingPath"
}

if (Test-Path -LiteralPath $routeMapPath) {
  throw "Forbidden duplicate route-map file still exists: $routeMapPath"
}

$routingText = Get-Content -LiteralPath $routingPath -Raw

$authoringMarkdownFiles = Get-ChildItem -LiteralPath 'skills/cap4k-authoring' -Recurse -File -Filter '*.md'
foreach ($file in $authoringMarkdownFiles) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  if ($text -match '(?im)^\s*#*\s*Route Map\b' -or
      $text -match '(?im)^\|\s*If the user asks\s*\|' -or
      $text -match '(?im)^\|\s*Task\s*\|\s*Read\s*\|\s*Workflow\s*\|') {
    throw "Duplicated authoring route table found in Markdown: $($file.FullName)"
  }
}

$requiredRouteIds = @(
  'full-authoring-flow',
  'business-discovery',
  'tactical-modeling',
  'technical-design',
  'generator-inputs',
  'generation-review',
  'handwritten-implementation',
  'verification-audit',
  'service-integration'
)

$routeBlocks = @{}
foreach ($routeId in $requiredRouteIds) {
  $routeBlocks[$routeId] = Get-RouteBlock -Text $routingText -RouteId $routeId
}

$fullFlowBlock = $routeBlocks['full-authoring-flow']
foreach ($requiredSnippet in @('then_chain:', 'cap4k-tactical-modeling', 'cap4k-verification-audit', 'from scratch', 'end-to-end')) {
  if (-not $fullFlowBlock.Contains($requiredSnippet)) {
    throw "full-authoring-flow route is missing required Task 1 signal or chain entry: $requiredSnippet"
  }
}

$requiredSignalsByRoute = @{
  'verification-audit' = @('positive_signals:', 'negative_signals:', 'static-only', 'focused-local', 'full-evidence')
  'service-integration' = @('positive_signals:', 'negative_signals:', 'Open Host Service', 'Published Language', 'external fact', 'integration event')
  'generation-review' = @('positive_signals:', 'negative_signals:', 'plan.json', 'generated output')
  'handwritten-implementation' = @('positive_signals:', 'negative_signals:', 'inside generated skeleton', 'missing generated skeleton')
}

foreach ($routeId in $requiredSignalsByRoute.Keys) {
  $block = $routeBlocks[$routeId]
  foreach ($requiredSnippet in $requiredSignalsByRoute[$routeId]) {
    if (-not $block.Contains($requiredSnippet)) {
      throw "$routeId route is missing required high-risk signal: $requiredSnippet"
    }
  }
}

$requiredReadsByRoute = @{
  'technical-design' = @(
    '../shared/rules/layer-and-runtime-boundaries.md',
    '../shared/references/generator-supported-skeletons.md',
    '../shared/workflows/skeleton-generation-gate.md'
  )
  'generator-inputs' = @(
    '../shared/rules/generator-input-source-of-truth.md',
    '../shared/references/generator-supported-skeletons.md',
    '../shared/workflows/skeleton-generation-gate.md'
  )
  'generation-review' = @(
    '../shared/references/output-ownership-taxonomy.md',
    '../shared/references/drift-gotchas.md',
    '../shared/workflows/skeleton-generation-gate.md'
  )
  'handwritten-implementation' = @(
    '../shared/rules/generated-skeleton-ownership.md',
    '../shared/references/generator-supported-skeletons.md',
    '../shared/references/runtime-capability-map.md',
    '../shared/workflows/skeleton-generation-gate.md'
  )
  'verification-audit' = @(
    '../shared/rules/verification-claim-policy.md',
    '../shared/references/runtime-capability-map.md',
    '../shared/references/drift-gotchas.md',
    '../shared/workflows/forced-rollback.md'
  )
  'service-integration' = @(
    '../shared/rules/layer-and-runtime-boundaries.md',
    '../shared/references/runtime-capability-map.md',
    '../shared/workflows/skeleton-generation-gate.md',
    '../cap4k-service-integration/rules/integration-event-boundaries.md'
  )
}

foreach ($routeId in $requiredReadsByRoute.Keys) {
  $block = $routeBlocks[$routeId]
  foreach ($requiredRead in $requiredReadsByRoute[$routeId]) {
    if (-not $block.Contains($requiredRead)) {
      throw "$routeId route is missing required read guardrail: $requiredRead"
    }
  }
}

if ($routingText -match '(?m)^\s*workflow:\s+workflows/') {
  throw 'routing.yaml still contains workflow shorthand starting with workflows/.'
}

$workflowMatches = [regex]::Matches($routingText, '(?m)^\s*workflow:\s+(.+?)\s*$')
foreach ($match in $workflowMatches) {
  $workflowPath = $match.Groups[1].Value.Trim()
  if (-not $workflowPath.StartsWith('../')) {
    throw "workflow path must be manifest-relative from routing.yaml: $workflowPath"
  }
}

if ($routingText -match '(?m)^\s*-\s+rules/integration-event-boundaries\.md\s*$') {
  throw 'routing.yaml still contains route-local integration-event-boundaries shorthand.'
}

if (-not $routingText.Contains('../cap4k-service-integration/rules/integration-event-boundaries.md')) {
  throw 'service-integration route must use manifest-relative integration-event-boundaries path.'
}

if ($routingText -match 'cap4k-tactical-modeling\s+or\s+cap4k-technical-design') {
  throw 'routing.yaml contains a combined route phrase instead of one real skill id.'
}

foreach ($file in @($authoringMarkdownFiles) + @(Get-Item -LiteralPath $routingPath)) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  if ($text -match '`cap4k-tactical-modeling\s+or\s+cap4k-technical-design`') {
    throw "Combined route code span found in authoring runtime text: $($file.FullName)"
  }
}