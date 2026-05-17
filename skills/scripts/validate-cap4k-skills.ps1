$ErrorActionPreference = 'Stop'

$skillDirs = Get-ChildItem -LiteralPath 'skills' -Directory |
  Where-Object { $_.Name -ne 'scripts' }

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

Write-Host "cap4k skill validation passed for $($skillDirs.Count) skills."
