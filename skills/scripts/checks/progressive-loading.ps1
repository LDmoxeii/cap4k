$ErrorActionPreference = 'Stop'

$focusedSkillDirs = Get-ChildItem -LiteralPath 'skills' -Directory |
  Where-Object { $_.Name -notin @('scripts', 'shared') } |
  Sort-Object Name

$badLineCounts = @()
$missingFrontmatter = @()
$missingAlwaysRead = @()
$tooManyAlwaysReads = @()
$affordanceTableCopies = @()

foreach ($dir in $focusedSkillDirs) {
  $skillFile = Join-Path $dir.FullName 'SKILL.md'
  if (-not (Test-Path -LiteralPath $skillFile -PathType Leaf)) {
    throw "Missing SKILL.md in focused skill directory: $($dir.FullName)"
  }

  $lines = Get-Content -LiteralPath $skillFile
  if ($lines.Count -gt 100) {
    $badLineCounts += "$($dir.Name): $($lines.Count)"
  }

  $text = $lines -join "`n"
  $frontmatterMatch = [regex]::Match($text, '(?s)^---\s*(.*?)\s*---')
  if (-not $frontmatterMatch.Success -or
      $frontmatterMatch.Groups[1].Value -notmatch '(?m)^name:\s*\S+' -or
      $frontmatterMatch.Groups[1].Value -notmatch '(?m)^description:\s*\S+') {
    $missingFrontmatter += $dir.Name
  }

  $alwaysReadMatch = [regex]::Match($text, '(?ms)^## Always Read\s*(.*?)(?=^##\s|\z)')
  if (-not $alwaysReadMatch.Success) {
    $missingAlwaysRead += $dir.Name
  } else {
    $alwaysReadCount = ([regex]::Matches($alwaysReadMatch.Groups[1].Value, '(?m)^\s*\d+\.\s+')).Count
    if ($alwaysReadCount -gt 3) {
      $tooManyAlwaysReads += "$($dir.Name): $alwaysReadCount"
    }
  }

  if ($text -match '(?m)^\|\s*Business Signal\s*\|\s*Cap4k Carrier\s*\|' -or
      $text -match '(?m)^\|\s*state-changing intent\s*\|\s*Command\s*\|') {
    $affordanceTableCopies += $dir.Name
  }
}

if ($badLineCounts.Count -gt 0) {
  throw "SKILL.md files over 100 lines: $($badLineCounts -join ', ')"
}

if ($missingFrontmatter.Count -gt 0) {
  throw "Missing required SKILL.md frontmatter name/description: $($missingFrontmatter -join ', ')"
}

if ($missingAlwaysRead.Count -gt 0) {
  throw "Missing Always Read section in SKILL.md: $($missingAlwaysRead -join ', ')"
}

if ($tooManyAlwaysReads.Count -gt 0) {
  throw "SKILL.md Always Read sections over 3 entries: $($tooManyAlwaysReads -join ', ')"
}

if ($affordanceTableCopies.Count -gt 0) {
  throw "SKILL.md contains copied tactical affordance table: $($affordanceTableCopies -join ', ')"
}

$requiredSkillRefs = @{
  'cap4k-authoring' = @(
    'routing.yaml',
    '../shared/workflows/forced-rollback.md'
  )
  'cap4k-business-discovery' = @(
    '../shared/workflows/forced-rollback.md',
    'workflows/discover-business-intent.md',
    'references/business-signals.md'
  )
  'cap4k-tactical-modeling' = @(
    '../shared/references/tactical-affordance-map.md',
    '../shared/workflows/forced-rollback.md',
    'workflows/map-tactical-carriers.md'
  )
  'cap4k-technical-design' = @(
    '../shared/rules/layer-and-runtime-boundaries.md',
    '../shared/workflows/skeleton-generation-gate.md',
    'workflows/write-technical-design-contract.md'
  )
  'cap4k-generator-inputs' = @(
    '../shared/rules/generator-input-source-of-truth.md',
    '../shared/workflows/skeleton-generation-gate.md',
    'workflows/project-generator-inputs.md'
  )
  'cap4k-generation-review' = @(
    'rules/generation-stop-policy.md',
    '../shared/references/output-ownership-taxonomy.md',
    'workflows/review-plan-and-generate.md'
  )
  'cap4k-handwritten-implementation' = @(
    'rules/implementation-entry-gates.md',
    '../shared/rules/generated-skeleton-ownership.md',
    'workflows/implement-inside-generated-skeletons.md'
  )
  'cap4k-service-integration' = @(
    'rules/integration-event-boundaries.md',
    '../shared/rules/layer-and-runtime-boundaries.md',
    'workflows/design-open-host-service.md',
    'workflows/consume-external-capability.md',
    'workflows/handle-inbound-integration-event.md'
  )
  'cap4k-verification-audit' = @(
    '../shared/rules/verification-claim-policy.md',
    'references/evidence-modes.md',
    'workflows/run-verification-audit.md'
  )
}

foreach ($skillName in $requiredSkillRefs.Keys) {
  $skillFile = Join-Path 'skills' (Join-Path $skillName 'SKILL.md')
  if (-not (Test-Path -LiteralPath $skillFile -PathType Leaf)) {
    throw "Missing required focused skill SKILL.md: $skillFile"
  }

  $skillText = Get-Content -LiteralPath $skillFile -Raw
  foreach ($requiredRef in $requiredSkillRefs[$skillName]) {
    if (-not $skillText.Contains($requiredRef)) {
      throw "$skillName SKILL.md is missing required progressive-loading reference: $requiredRef"
    }
  }
}