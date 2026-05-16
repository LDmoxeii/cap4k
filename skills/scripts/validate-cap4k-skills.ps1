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

$allText = $skillTextFiles |
  ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }

$combined = $allText -join "`n"

$forbiddenPatterns = @(
  ('No design support for `integration_' + 'event`'),
  ('integration-event design support that does not exist ' + 'today'),
  ('unsupported design tags.*integration_' + 'event'),
  ('enumTranslation' + '\.set'),
  ('read docs/public/authoring during normal ' + 'operation')
)

foreach ($pattern in $forbiddenPatterns) {
  if ($combined -match $pattern) {
    throw "Forbidden stale skill text matched: $pattern"
  }
}

Write-Host "cap4k skill validation passed for $($skillDirs.Count) skills."
