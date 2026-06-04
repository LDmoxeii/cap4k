$ErrorActionPreference = 'Stop'

$skillDirs = Get-ChildItem -LiteralPath 'skills' -Directory |
  Where-Object { $_.Name -notin @('scripts', 'shared') }

$removedModelingSkillName = 'cap4k-' + 'modeling'
$removedGenerationSkillName = 'cap4k-' + 'generation'
$removedGeneratedOutputReviewSkillName = 'cap4k-' + 'generated-' + 'output-' + 'review'
$forbiddenSkillDirs = @(
  (Join-Path 'skills' $removedModelingSkillName),
  (Join-Path 'skills' $removedGenerationSkillName),
  (Join-Path 'skills' $removedGeneratedOutputReviewSkillName)
)

foreach ($forbiddenSkillDir in $forbiddenSkillDirs) {
  if (Test-Path -LiteralPath $forbiddenSkillDir) {
    throw "Forbidden removed skill directory still exists: $forbiddenSkillDir"
  }
}

if ($skillDirs.Count -lt 7) {
  throw "Expected at least 7 cap4k skill directories, found $($skillDirs.Count)."
}

$badLineCounts = @()
$missingFrontmatter = @()
$brokenLinks = @()

foreach ($dir in $skillDirs) {
  $skillFile = Join-Path $dir.FullName 'SKILL.md'
  if (-not (Test-Path -LiteralPath $skillFile)) {
    throw "Missing SKILL.md in $($dir.FullName)"
  }

  $lines = Get-Content -LiteralPath $skillFile
  if ($lines.Count -gt 100) {
    $badLineCounts += "$($dir.Name): $($lines.Count)"
  }

  $text = $lines -join "`n"
  if ($text -notmatch '(?s)^---\s*.*name:\s*.+description:\s*.+---') {
    $missingFrontmatter += $dir.Name
  }

  $matches = [regex]::Matches($text, '\(([^)]+?\.md)(?:[#?][^)\s]*)?(?:\s+"[^"]*")?\)')
  foreach ($match in $matches) {
    $target = $match.Groups[1].Value
    if ($target.StartsWith('http')) { continue }
    $targetPath = Join-Path $dir.FullName $target
    if (-not (Test-Path -LiteralPath $targetPath)) {
      $brokenLinks += "$($dir.Name): $target"
    }
  }
}

if ($badLineCounts.Count -gt 0) {
  throw "SKILL.md files over 100 lines: $($badLineCounts -join ', ')"
}

if ($missingFrontmatter.Count -gt 0) {
  throw "Missing required frontmatter: $($missingFrontmatter -join ', ')"
}

if ($brokenLinks.Count -gt 0) {
  throw "Broken local markdown links: $($brokenLinks -join ', ')"
}

$sharedRulePaths = @(
  'skills/shared/rules/cap4k-positioning.md',
  'skills/shared/rules/generated-skeleton-ownership.md',
  'skills/shared/rules/generator-input-source-of-truth.md',
  'skills/shared/rules/layer-and-runtime-boundaries.md',
  'skills/shared/rules/naming-layout-and-testing.md',
  'skills/shared/rules/verification-claim-policy.md',
  'skills/shared/workflows/forced-rollback.md',
  'skills/shared/workflows/skeleton-generation-gate.md'
)

$missingSharedRules = $sharedRulePaths |
  Where-Object { -not (Test-Path -LiteralPath $_) }

if ($missingSharedRules.Count -gt 0) {
  throw "Missing shared cap4k skill rules: $($missingSharedRules -join ', ')"
}

$requiredSkillRefs = @{
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
  'cap4k-implementation' = @(
    '../shared/rules/layer-and-runtime-boundaries.md',
    '../shared/rules/generated-skeleton-ownership.md',
    '../shared/workflows/skeleton-generation-gate.md'
  )
  'cap4k-service-integration' = @(
    '../shared/rules/layer-and-runtime-boundaries.md',
    '../shared/rules/naming-layout-and-testing.md',
    '../shared/workflows/forced-rollback.md'
  )
  'cap4k-verification' = @(
    '../shared/rules/verification-claim-policy.md',
    '../shared/rules/generated-skeleton-ownership.md',
    '../shared/rules/naming-layout-and-testing.md'
  )
}

foreach ($skillName in $requiredSkillRefs.Keys) {
  $skillFile = Join-Path 'skills' (Join-Path $skillName 'SKILL.md')
  if (-not (Test-Path -LiteralPath $skillFile)) {
    throw "Missing required focused skill SKILL.md: $skillFile"
  }

  $skillText = Get-Content -LiteralPath $skillFile -Raw
  foreach ($requiredRef in $requiredSkillRefs[$skillName]) {
    if ($skillText -notlike "*$requiredRef*") {
      throw "$skillName SKILL.md is missing shared rule reference: $requiredRef"
    }
  }
}

$skillTextFiles = Get-ChildItem -LiteralPath 'skills' -Recurse -File |
  Where-Object { $_.Extension -in '.md', '.yaml', '.yml' }

$rootTextFiles = @()
if (Test-Path -LiteralPath 'AGENTS.md') {
  $rootTextFiles += Get-Item -LiteralPath 'AGENTS.md'
}

$runtimeShellTextFiles = @($skillTextFiles) + @($rootTextFiles)

$removedOutputReviewPhrase = 'generated-' + 'output-' + 'review'
$removedSkillRefPatterns = @(
  @{
    Name = $removedModelingSkillName
    Pattern = [regex]::Escape($removedModelingSkillName)
  },
  @{
    Name = $removedGenerationSkillName
    Pattern = [regex]::Escape($removedGenerationSkillName) + '($|[^-])'
  },
  @{
    Name = $removedGeneratedOutputReviewSkillName
    Pattern = [regex]::Escape($removedGeneratedOutputReviewSkillName)
  },
  @{
    Name = $removedOutputReviewPhrase
    Pattern = [regex]::Escape($removedOutputReviewPhrase)
  }
)

foreach ($file in $runtimeShellTextFiles) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  foreach ($removedSkillRefPattern in $removedSkillRefPatterns) {
    if ($text -match $removedSkillRefPattern.Pattern) {
      throw "Forbidden removed skill reference matched in skills runtime text: $($file.FullName): $($removedSkillRefPattern.Name)"
    }
  }
}

$staleScanFiles = $runtimeShellTextFiles

$allText = $staleScanFiles |
  ForEach-Object {
    $text = Get-Content -LiteralPath $_.FullName -Raw
    if ($_.FullName -like '*\skills\shared\references\drift-gotchas.md') {
      $text = $text -replace [regex]::Escape('src-generated/main/kotlin'), 'stale-generated-source-path'
      $text = $text -replace [regex]::Escape('client/cli'), 'stale-client-cli-boundary'
    }
    $text
  }

$combined = $allText -join "`n"

$sharedCoreCombined = $sharedRulePaths |
  ForEach-Object { Get-Content -LiteralPath $_ -Raw }

$sharedCoreCombined = $sharedCoreCombined -join "`n"

$requiredSharedCorePatterns = @(
  'DDD tactical framework plus generator-backed authoring system',
  'Treat cap4k skeletons as generated by cap4k',
  'HTTP and message transport handle consume, parse, register, and dispatch',
  'Treat plan.json as generated evidence',
  'Make role inferable from file name plus directory',
  'Match every claim to its evidence mode'
)

foreach ($pattern in $requiredSharedCorePatterns) {
  if (-not $sharedCoreCombined.Contains($pattern)) {
    throw "Missing required shared core wording: $pattern"
  }
}

$forbiddenPatterns = @(
  ('No design support for `integration_' + 'event`'),
  ('integration-event design support that does not exist ' + 'today'),
  ('unsupported design tags.*integration_' + 'event'),
  ('enumTranslation' + '\.set'),
  ('read docs/public/authoring during normal ' + 'operation'),
  ('cap4k-runtime-' + 'integration'),
  ('runtime-' + 'integration'),
  ('value ' + 'concepts'),
  ('src-generated/main/' + 'kotlin'),
  ('enum translation core ' + 'DSL'),
  ('controller / job / ' + 'subscriber'),
  ('client/' + 'cli\b')
)

foreach ($pattern in $forbiddenPatterns) {
  if ($combined -match $pattern) {
    throw "Forbidden stale skill text matched: $pattern"
  }
}

$authoringTextFiles = @()
if (Test-Path -LiteralPath 'docs/public/authoring') {
  $authoringTextFiles += Get-ChildItem -LiteralPath 'docs/public/authoring' -Recurse -File |
    Where-Object { $_.Extension -eq '.md' }
}

$analysisTextFiles = @()
if (Test-Path -LiteralPath 'docs/superpowers/analysis') {
  $analysisTextFiles += Get-ChildItem -LiteralPath 'docs/superpowers/analysis' -Recurse -File |
    Where-Object { $_.Extension -eq '.md' }
}

$authoringText = $authoringTextFiles |
  ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }

$authoringCombined = $authoringText -join "`n"

$authoringForbiddenPatterns = @(
  ('value ' + 'concepts'),
  ('client/' + 'cli\b'),
  ('controller / job / ' + 'subscriber'),
  ([string]::Concat('cli ', [char]0x662F, [char]0x9632, [char]0x8150, [char]0x8FB9, [char]0x754C)),
  ('RPC' + [char]0x3001 + 'message ' + 'listener'),
  ('controller surfaces'),
  ('job surfaces'),
  ('subscriber surfaces'),
  ('process ' + 'step')
)

foreach ($pattern in $authoringForbiddenPatterns) {
  if ($authoringCombined -match $pattern) {
    throw "Forbidden stale authoring text matched: $pattern"
  }
}

function Assert-NoForbiddenPattern {
  param(
    [Parameter(Mandatory = $true)]
    [object[]] $Files,
    [Parameter(Mandatory = $true)]
    [string[]] $Patterns,
    [Parameter(Mandatory = $true)]
    [string] $Scope
  )

  foreach ($file in $Files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    foreach ($pattern in $Patterns) {
      if ($text -match $pattern) {
        throw "Forbidden removed event guidance matched in ${Scope}: $($file.FullName): $pattern"
      }
    }
  }
}

$removedEventGuidancePatterns = @(
  ('Auto' + 'Attach'),
  ('Auto' + 'Request'),
  ('Auto' + 'Requests'),
  ('Auto' + 'Release'),
  ('Auto' + 'Releases'),
  ('Mediator\.events\.' + 'publish'),
  ('IntegrationEventSupervisor\.' + 'publish'),
  ('IntegrationEventSupervisor\.instance\.' + 'publish'),
  ('attach ' + 'or ' + 'publish'),
  ('attach ' + '/ ' + 'publish'),
  ('attach ' + [char]0x6216 + ' ' + 'publish')
)

Assert-NoForbiddenPattern `
  -Files (@($skillTextFiles) + @($authoringTextFiles) + @($analysisTextFiles)) `
  -Patterns $removedEventGuidancePatterns `
  -Scope 'active skills, public authoring docs, and superpowers analysis docs'

$runtimeSourceRoots = @(
  'ddd-core',
  'cap4k-ddd-starter',
  'ddd-application-request-jpa',
  'ddd-domain-event-jpa',
  'ddd-integration-event-http',
  'ddd-integration-event-http-jpa',
  'ddd-integration-event-rabbitmq',
  'ddd-integration-event-rocketmq'
)

$runtimeSourceFiles = @()
foreach ($root in $runtimeSourceRoots) {
  if (-not (Test-Path -LiteralPath $root)) { continue }
  $runtimeSourceFiles += Get-ChildItem -LiteralPath $root -Recurse -File |
    Where-Object { $_.Extension -in '.kt', '.java' }
}

if ($runtimeSourceFiles.Count -gt 0) {
  Assert-NoForbiddenPattern `
    -Files $runtimeSourceFiles `
    -Patterns $removedEventGuidancePatterns `
    -Scope 'runtime source'
}

Write-Host "cap4k skill validation passed for $($skillDirs.Count) skills."
