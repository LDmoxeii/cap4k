$ErrorActionPreference = 'Stop'

$markdownFiles = Get-ChildItem -LiteralPath 'skills' -Recurse -File -Filter '*.md' |
  Sort-Object FullName

$brokenLinks = @()
$linkPattern = '\[[^\]]+\]\((?<target>[^)\s]+?\.md)(?:[#?][^)\s]*)?(?:\s+"[^"]*")?\)'

foreach ($file in $markdownFiles) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  $matches = [regex]::Matches($text, $linkPattern)
  foreach ($match in $matches) {
    $target = $match.Groups['target'].Value
    if ($target -match '^(?i:https?://)') {
      continue
    }

    $targetPath = Join-Path $file.DirectoryName $target
    if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) {
      $relativeFile = Resolve-Path -LiteralPath $file.FullName -Relative
      $brokenLinks += "$relativeFile -> $target"
    }
  }
}

if ($brokenLinks.Count -gt 0) {
  throw "Broken local Markdown links: $($brokenLinks -join ', ')"
}