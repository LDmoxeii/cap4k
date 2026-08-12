[CmdletBinding()]
param(
    [string] $RepoRoot,
    [Parameter(Mandatory = $true)]
    [string[]] $Paths,
    [string] $Label = 'Markdown'
)

$ErrorActionPreference = 'Stop'
$root = if ($RepoRoot) { (Resolve-Path -LiteralPath $RepoRoot).Path } else { (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path }
$markdownFiles = @()
foreach ($relativePath in $Paths) {
    $path = Join-Path $root $relativePath
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing Markdown validation path: $relativePath" }
    if (Test-Path -LiteralPath $path -PathType Container) {
        $markdownFiles += Get-ChildItem -LiteralPath $path -Recurse -File -Filter '*.md'
    } elseif ([IO.Path]::GetExtension($path) -ieq '.md') {
        $markdownFiles += Get-Item -LiteralPath $path
    }
}
$markdownFiles = @($markdownFiles | Sort-Object FullName -Unique)
$brokenLinks = @()
$linkPattern = '\[[^\]]+\]\((?<target>[^)\s]+?\.md)(?:[#?][^)\s]*)?(?:\s+"[^"]*")?\)'
$codeSpanPattern = '(?<!`)`(?<target>[^`\r\n]*?\.md(?:[#?][^`\s]+)?)`(?!`)'

function Get-MarkdownTargetPath { param([string] $Target) return ($Target -split '[#?]', 2)[0] }
function Test-CodeSpanMarkdownPath {
    param([string] $Target)
    if ($Target -match '^(?i:https?://)') { return $false }
    $targetPath = Get-MarkdownTargetPath -Target $Target
    return $targetPath -match '(?i)\.md$' -and ($targetPath -match '[\\/]' -or $targetPath -match '^\.')
}
function Test-LocalMarkdownTarget {
    param([System.IO.FileInfo] $File, [string] $Target)
    if ($Target -match '^(?i:https?://)') { return $true }
    $targetPath = Get-MarkdownTargetPath -Target $Target
    $resolvedTarget = if ([System.IO.Path]::IsPathRooted($targetPath)) { $targetPath } else { Join-Path $File.DirectoryName $targetPath }
    return Test-Path -LiteralPath $resolvedTarget -PathType Leaf
}

foreach ($file in $markdownFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    foreach ($match in [regex]::Matches($text, $linkPattern)) {
        $target = $match.Groups['target'].Value
        if (-not (Test-LocalMarkdownTarget -File $file -Target $target)) {
            $brokenLinks += "$([IO.Path]::GetRelativePath($root, $file.FullName).Replace('\','/')) -> $target"
        }
    }
    $lines = Get-Content -LiteralPath $file.FullName -Encoding UTF8
    $inFence = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*(```|~~~)') { $inFence = -not $inFence; continue }
        if ($inFence) { continue }
        foreach ($match in [regex]::Matches($line, $codeSpanPattern)) {
            $target = $match.Groups['target'].Value
            if (-not (Test-CodeSpanMarkdownPath -Target $target)) { continue }
            $before = if ($match.Index -gt 0) { $line[$match.Index - 1] } else { '' }
            $afterIndex = $match.Index + $match.Length
            $after = if ($afterIndex -lt $line.Length) { $line[$afterIndex] } else { '' }
            if ($before -eq '[' -and $after -eq ']') { continue }
            if (-not (Test-LocalMarkdownTarget -File $file -Target $target)) {
                $brokenLinks += "$([IO.Path]::GetRelativePath($root, $file.FullName).Replace('\','/')):$($i + 1) -> $target"
            }
        }
    }
}
if ($brokenLinks.Count -gt 0) { throw "Broken $Label local Markdown links: $($brokenLinks -join ', ')" }
Write-Output "OK: $Label local Markdown links resolve."
