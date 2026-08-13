[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ChangedFilesFile,

    [string] $GitHubOutputFile
)

$ErrorActionPreference = 'Stop'

function Normalize-RepositoryPath {
    param([string] $Path)
    return $Path.Trim().Replace('\', '/')
}

function Test-DocumentationPath {
    param([string] $Path)
    $normalized = Normalize-RepositoryPath -Path $Path

    if ($normalized -ieq 'AGENTS.md') { return $false }
    if ($normalized.StartsWith('docs/', [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
    if ($normalized.StartsWith('.github/ISSUE_TEMPLATE/', [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
    if ($normalized -ieq '.github/PULL_REQUEST_TEMPLATE.md') { return $true }
    if ($normalized -match '^(?i:README(?:\..*)?|CHANGELOG\.md|CONTRIBUTING\.md|CODE_OF_CONDUCT\.md)$') { return $true }
    return $normalized -notmatch '/' -and $normalized.EndsWith('.md', [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-GovernanceSkillPath {
    param([string] $Path)
    $normalized = Normalize-RepositoryPath -Path $Path

    if ($normalized -ieq 'AGENTS.md') { return $true }
    if ($normalized.StartsWith('.agents/skills/', [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
    if ($normalized.StartsWith('skills/', [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
    return $normalized -ieq '.comet/config.yaml'
}

function Get-ChangedPaths {
    param([string] $Path)
    $paths = [System.Collections.Generic.List[string]]::new()

    foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "`t"
        if ($parts.Count -eq 1) {
            $paths.Add((Normalize-RepositoryPath -Path $parts[0]))
            continue
        }

        $status = $parts[0]
        if ($status -match '^[RC][0-9]*$' -and $parts.Count -ge 3) {
            $paths.Add((Normalize-RepositoryPath -Path $parts[1]))
            $paths.Add((Normalize-RepositoryPath -Path $parts[2]))
        } elseif ($parts.Count -ge 2) {
            $paths.Add((Normalize-RepositoryPath -Path $parts[1]))
        } else {
            throw "Malformed git diff --name-status line: $line"
        }
    }

    return @($paths | Sort-Object -Unique)
}

$changedFilesPath = (Resolve-Path -LiteralPath $ChangedFilesFile -ErrorAction Stop).Path
$changedPaths = @(Get-ChangedPaths -Path $changedFilesPath)
$hasChanges = $changedPaths.Count -gt 0
$docsOnly = $hasChanges
$governanceSkillOnly = $hasChanges
$gradleSkippable = $hasChanges

foreach ($path in $changedPaths) {
    $isDocumentation = Test-DocumentationPath -Path $path
    $isGovernanceSkill = Test-GovernanceSkillPath -Path $path
    if (-not $isDocumentation) { $docsOnly = $false }
    if (-not $isGovernanceSkill) { $governanceSkillOnly = $false }
    if (-not ($isDocumentation -or $isGovernanceSkill)) { $gradleSkippable = $false }
}

$result = [ordered]@{
    run_gradle = -not $gradleSkippable
    docs_only = $docsOnly
    governance_skill_only = $governanceSkillOnly
    gradle_skippable = $gradleSkippable
    changed_file_count = $changedPaths.Count
}

if (-not [string]::IsNullOrWhiteSpace($GitHubOutputFile)) {
    $outputLines = foreach ($entry in $result.GetEnumerator()) {
        $value = if ($entry.Value -is [bool]) { $entry.Value.ToString().ToLowerInvariant() } else { [string]$entry.Value }
        "$($entry.Key)=$value"
    }
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::AppendAllText($GitHubOutputFile, (($outputLines -join [Environment]::NewLine) + [Environment]::NewLine), $encoding)
}

[pscustomobject]$result | ConvertTo-Json -Compress
