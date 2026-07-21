$ErrorActionPreference = 'Stop'

$markdownFiles = Get-ChildItem -LiteralPath 'skills' -Recurse -File -Filter '*.md' |
  Sort-Object FullName

$brokenLinks = @()
$linkPattern = '\[[^\]]+\]\((?<target>[^)\s]+?\.md)(?:[#?][^)\s]*)?(?:\s+"[^"]*")?\)'
$codeSpanPattern = '(?<!`)`(?<target>[^`\r\n]*?\.md(?:[#?][^`\s]+)?)`(?!`)'

function Get-RelativeFilePath {
  param([System.IO.FileInfo] $File)

  return Resolve-Path -LiteralPath $File.FullName -Relative
}

function Get-MarkdownTargetPath {
  param([string] $Target)

  return ($Target -split '[#?]', 2)[0]
}

function Test-CodeSpanMarkdownPath {
  param([string] $Target)

  if ($Target -match '^(?i:https?://)') {
    return $false
  }

  $targetPath = Get-MarkdownTargetPath -Target $Target
  if ($targetPath -notmatch '(?i)\.md$') {
    return $false
  }

  return $targetPath -match '[\\/]' -or $targetPath -match '^\.'
}

function Test-LocalMarkdownTarget {
  param(
    [System.IO.FileInfo] $File,
    [string] $Target
  )

  if ($Target -match '^(?i:https?://)') {
    return $true
  }

  $targetPath = Get-MarkdownTargetPath -Target $Target
  $resolvedTarget = if ([System.IO.Path]::IsPathRooted($targetPath)) {
    $targetPath
  } else {
    Join-Path $File.DirectoryName $targetPath
  }

  return Test-Path -LiteralPath $resolvedTarget -PathType Leaf
}

foreach ($file in $markdownFiles) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  $matches = [regex]::Matches($text, $linkPattern)
  foreach ($match in $matches) {
    $target = $match.Groups['target'].Value
    if (-not (Test-LocalMarkdownTarget -File $file -Target $target)) {
      $relativeFile = Get-RelativeFilePath -File $file
      $brokenLinks += "$relativeFile -> $target"
    }
  }

  $lines = Get-Content -LiteralPath $file.FullName
  $inFence = $false
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    if ($line -match '^\s*(```|~~~)') {
      $inFence = -not $inFence
      continue
    }

    if ($inFence) {
      continue
    }

    $codeSpanMatches = [regex]::Matches($line, $codeSpanPattern)
    foreach ($match in $codeSpanMatches) {
      $target = $match.Groups['target'].Value
      if (-not (Test-CodeSpanMarkdownPath -Target $target)) {
        continue
      }

      $before = if ($match.Index -gt 0) { $line[$match.Index - 1] } else { '' }
      $afterIndex = $match.Index + $match.Length
      $after = if ($afterIndex -lt $line.Length) { $line[$afterIndex] } else { '' }
      if ($before -eq '[' -and $after -eq ']') {
        continue
      }

      if (-not (Test-LocalMarkdownTarget -File $file -Target $target)) {
        $relativeFile = Get-RelativeFilePath -File $file
        $lineNumber = $i + 1
        $brokenLinks += "$relativeFile`:$lineNumber -> $target"
      }
    }
  }
}

if ($brokenLinks.Count -gt 0) {
  throw "Broken local Markdown links: $($brokenLinks -join ', ')"
}
