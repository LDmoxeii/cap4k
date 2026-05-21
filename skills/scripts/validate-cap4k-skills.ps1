$ErrorActionPreference = 'Stop'

$skillDirs = Get-ChildItem -LiteralPath 'skills' -Directory |
  Where-Object { $_.Name -notin @('scripts', 'shared') }

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
  'skills/shared/rules/core-positioning.md',
  'skills/shared/rules/default-path-and-write-boundaries.md',
  'skills/shared/rules/ownership-and-generation-flow.md',
  'skills/shared/rules/naming-layout-and-testing.md',
  'skills/shared/rules/advanced-mode-gates.md'
)

$missingSharedRules = $sharedRulePaths |
  Where-Object { -not (Test-Path -LiteralPath $_) }

if ($missingSharedRules.Count -gt 0) {
  throw "Missing shared cap4k skill rules: $($missingSharedRules -join ', ')"
}

$requiredSkillRefs = @{
  'cap4k-modeling' = @(
    '../shared/rules/core-positioning.md',
    '../shared/rules/default-path-and-write-boundaries.md',
    '../shared/rules/advanced-mode-gates.md'
  )
  'cap4k-generation' = @(
    '../shared/rules/core-positioning.md',
    '../shared/rules/ownership-and-generation-flow.md'
  )
  'cap4k-implementation' = @(
    '../shared/rules/default-path-and-write-boundaries.md',
    '../shared/rules/ownership-and-generation-flow.md'
  )
  'cap4k-service-integration' = @(
    '../shared/rules/default-path-and-write-boundaries.md',
    '../shared/rules/naming-layout-and-testing.md'
  )
  'cap4k-verification' = @(
    '../shared/rules/default-path-and-write-boundaries.md',
    '../shared/rules/ownership-and-generation-flow.md',
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

$staleScanFiles = @($skillTextFiles) + @($rootTextFiles)

$allText = $staleScanFiles |
  ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }

$combined = $allText -join "`n"

$sharedCoreCombined = $sharedRulePaths |
  ForEach-Object { Get-Content -LiteralPath $_ -Raw }

$sharedCoreCombined = $sharedCoreCombined -join "`n"

$requiredSharedCorePatterns = @(
  'One command path may persist only one aggregate root',
  'zero-trust validation',
  '`cap4kPlan` establishes `plan.json` ownership before `cap4kGenerate`',
  'Copied generated snapshots are evidence only',
  'Start from the conservative cap4k default path',
  'File name plus directory should make the role inferable',
  'domain behavior tests and application orchestration tests'
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
  ('client/' + 'cli')
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

$authoringText = $authoringTextFiles |
  ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }

$authoringCombined = $authoringText -join "`n"

$authoringForbiddenPatterns = @(
  ('value ' + 'concepts'),
  ('client/' + 'cli'),
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
  ('attach ' + 'or ' + 'publish'),
  ('attach ' + '/ ' + 'publish'),
  ('attach ' + [char]0x6216 + ' ' + 'publish')
)

Assert-NoForbiddenPattern `
  -Files (@($skillTextFiles) + @($authoringTextFiles)) `
  -Patterns $removedEventGuidancePatterns `
  -Scope 'active skills and public authoring docs'

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
