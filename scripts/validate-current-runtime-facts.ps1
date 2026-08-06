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
    'Snowflake capability' = '\bSnowflake\b'
    'Snowflake Runtime module' = '\bddd-distributed-snowflake\b'
    'Snowflake starter' = '\bcap4k-ddd-snowflake-starter\b'
    'Snowflake policy' = '\bidentifier\.snowflake\b'
    'Worker-ID capability' = '\bWorker-?ID\b|\b__worker_id\b|\bworker_id\.sql\b'
}
$allowedRetiredTermsByPath = @{
    # These active Runtime contract specs intentionally name the retired boundary they define.
    # Keep this allowlist exact: new current-facts docs must still fail until their historical
    # wording is reviewed explicitly.
    'docs/comet/specs/runtime-agent-api-facts/spec.md' = @('Snowflake capability')
    'docs/comet/specs/runtime-agent-retired-descriptors/spec.md' = @('Snowflake capability')
    'docs/comet/specs/runtime-handler-contract/spec.md' = @('EventSubscriber<T>')
    'docs/comet/specs/runtime-roadmap/spec.md' = @('EventSubscriber<T>', 'Snowflake capability')
    'docs/comet/specs/runtime-surface-cleanup/spec.md' = @('Snowflake capability')
}

$expectedRetiredDescriptorIdentities = @('console', 'locker', 'saga', 'snowflake')
$retiredDescriptorPolicyFile = Join-Path $repoRoot 'cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RetiredRuntimeDescriptorPolicy.kt'
$retiredDescriptorPolicyText = Get-Content -LiteralPath $retiredDescriptorPolicyFile -Raw -Encoding UTF8
$declaredRetiredDescriptorIdentities = [regex]::Matches(
    $retiredDescriptorPolicyText,
    '(?m)^\s*"([a-z][a-z0-9-]*)",\s*$'
) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
if (Compare-Object $expectedRetiredDescriptorIdentities $declaredRetiredDescriptorIdentities) {
    throw ('Retired Runtime descriptor policy must declare exactly: ' + ($expectedRetiredDescriptorIdentities -join ', ') + '.')
}

$descriptorSourceFiles = Get-ChildItem -LiteralPath $repoRoot -Directory |
    ForEach-Object {
        $sourceRoot = Join-Path $_.FullName 'src/main/kotlin'
        if (Test-Path -LiteralPath $sourceRoot -PathType Container) {
            Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter '*.kt'
        }
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
            if ($allowedRetiredTermsByPath.ContainsKey($relativePath) -and $entry.Key -in $allowedRetiredTermsByPath[$relativePath]) {
                continue
            }
            if ($entry.Key -eq 'Console module' -and $relativePath -eq 'docs/comet/specs/runtime-console-retirement/spec.md') {
                continue
            }
            if (
                $relativePath -eq 'docs/comet/specs/runtime-snowflake-retirement/spec.md' -and
                $entry.Key -in @(
                    'Snowflake capability',
                    'Snowflake Runtime module',
                    'Snowflake starter',
                    'Snowflake policy',
                    'Worker-ID capability'
                )
            ) {
                continue
            }
            if (
                $relativePath -eq 'docs/comet/specs/runtime-jackson-only/spec.md' -and
                $entry.Key -in @(
                    'EventSubscriber<T>',
                    'Snowflake capability'
                )
            ) {
                continue
            }
            $violations.Add("${relativePath}: retired runtime term '$($entry.Key)'")
        }
    }
}

foreach ($file in $descriptorSourceFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $relativePath = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName).Replace('\', '/')
    foreach ($identity in $expectedRetiredDescriptorIdentities) {
        $escapedIdentity = [regex]::Escape($identity)
        $descriptorPatterns = @(
            ('\b(?:capabilityId|providerId)\s*=\s*["''](?:[^"'']*\.)?{0}["'']' -f $escapedIdentity),
            ('\boverride\s+val\s+id(?:\s*:\s*String)?\s*=\s*["'']{0}["'']' -f $escapedIdentity)
        )
        if ($descriptorPatterns | Where-Object { $text -match $_ }) {
            $violations.Add("${relativePath}: retired Runtime descriptor identity '$identity'")
        }
    }
}

if ($violations.Count -gt 0) {
    throw "Current runtime facts contain retired Runtime surfaces:`n$($violations -join "`n")"
}

Write-Output "OK: current runtime facts contain no retired Runtime surfaces."
