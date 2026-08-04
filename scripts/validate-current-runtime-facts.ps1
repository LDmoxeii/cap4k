$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$currentDirectories = @(
    'docs/public',
    'docs/comet/specs',
    'docs/superpowers/analysis',
    'skills'
)
$currentFiles = @(
    'AGENTS.md',
    'README.md',
    'cap4k-plugin-pipeline-gradle/README.md'
)
$retiredTerms = [ordered]@{
    'EventSubscriberManager' = '\b(?:Default)?EventSubscriberManager\b'
    'AbstractEventSubscriber' = '\bAbstractEventSubscriber\b'
    'EventSubscriber<T>' = '\bEventSubscriber\s*<'
    'Console module' = '\bcap4k-ddd-console\b'
    'Console starter' = '\bcap4k-ddd-console-starter\b'
    'Console auto-configuration' = '\bDDDConsoleAutoConfiguration\b'
    'Console runtime package' = '\bcom\.only4\.cap4k\.ddd\.console\b'
    'Console HTTP endpoint' = '/cap4k/console(?:/|\b)'
}

$files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
foreach ($relativeDirectory in $currentDirectories) {
    $directory = Join-Path $repoRoot $relativeDirectory
    if (Test-Path -LiteralPath $directory -PathType Container) {
        Get-ChildItem -LiteralPath $directory -Recurse -File |
            Where-Object { $_.Extension -in @('.md', '.yaml', '.yml') } |
            ForEach-Object { $files.Add($_) }
    }
}
foreach ($relativeFile in $currentFiles) {
    $file = Join-Path $repoRoot $relativeFile
    if (Test-Path -LiteralPath $file -PathType Leaf) {
        $files.Add((Get-Item -LiteralPath $file))
    }
}

$violations = [System.Collections.Generic.List[string]]::new()
foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $relativePath = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName).Replace('\', '/')
    foreach ($entry in $retiredTerms.GetEnumerator()) {
        if ($text -match $entry.Value) {
            if ($entry.Key -eq 'Console module' -and $relativePath -eq 'docs/comet/specs/runtime-console-retirement/spec.md') {
                continue
            }
            $violations.Add("${relativePath}: retired runtime term '$($entry.Key)'")
        }
    }
}

if ($violations.Count -gt 0) {
    throw "Current runtime facts contain retired Runtime surfaces:`n$($violations -join "`n")"
}

Write-Output "OK: current runtime facts contain no retired Runtime surfaces."
