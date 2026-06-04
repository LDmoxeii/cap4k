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
    return ''
  }

  return $match.Value
}

function Get-SkillActivationText {
  param(
    [Parameter(Mandatory = $true)]
    [string] $SkillName,
    [Parameter(Mandatory = $true)]
    [string] $RoutingText
  )

  $skillPath = Join-Path 'skills' $SkillName
  $skillText = ''
  if (Test-Path -LiteralPath $skillPath -PathType Container) {
    $skillText = (Get-ChildItem -LiteralPath $skillPath -Recurse -File |
      Where-Object { $_.Extension -in @('.md', '.yaml', '.yml') } |
      ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
  }

  $routeId = $SkillName -replace '^cap4k-', ''
  $routeBlock = Get-RouteBlock -Text $RoutingText -RouteId $routeId
  return ($skillText + "`n" + $routeBlock)
}

$routingPath = 'skills/cap4k-authoring/routing.yaml'
if (-not (Test-Path -LiteralPath $routingPath -PathType Leaf)) {
  throw "Missing routing manifest required for gate reference validation: $routingPath"
}

$routingText = Get-Content -LiteralPath $routingPath -Raw

$skeletonGateSkills = @(
  'cap4k-technical-design',
  'cap4k-generator-inputs',
  'cap4k-generation-review',
  'cap4k-handwritten-implementation',
  'cap4k-service-integration'
)

foreach ($skillName in $skeletonGateSkills) {
  $activationText = Get-SkillActivationText -SkillName $skillName -RoutingText $routingText
  if ($activationText -notlike '*../shared/workflows/skeleton-generation-gate.md*' -and
      $activationText -notlike '*../../shared/workflows/skeleton-generation-gate.md*') {
    throw "$skillName does not activate ../shared/workflows/skeleton-generation-gate.md"
  }
}

$forcedRollbackSkills = @(
  'cap4k-business-discovery',
  'cap4k-tactical-modeling',
  'cap4k-verification-audit'
)

foreach ($skillName in $forcedRollbackSkills) {
  $activationText = Get-SkillActivationText -SkillName $skillName -RoutingText $routingText
  if ($activationText -notlike '*../shared/workflows/forced-rollback.md*' -and
      $activationText -notlike '*../../shared/workflows/forced-rollback.md*') {
    throw "$skillName does not activate ../shared/workflows/forced-rollback.md"
  }
}

$workflowText = (Get-ChildItem -LiteralPath 'skills' -Recurse -File -Filter '*.md' |
  Where-Object { $_.FullName -like '*\workflows\*' } |
  ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"

$highRiskSharedRefs = @(
  '../shared/references/generator-supported-skeletons.md',
  '../shared/references/runtime-capability-map.md',
  '../shared/references/drift-gotchas.md'
)

foreach ($sharedRef in $highRiskSharedRefs) {
  $alternateRef = $sharedRef -replace '^\.\./', '../../'
  if (-not $routingText.Contains($sharedRef) -and
      -not $workflowText.Contains($sharedRef) -and
      -not $workflowText.Contains($alternateRef)) {
    throw "High-risk shared reference has no inbound routing.yaml or workflow activation: $sharedRef"
  }
}