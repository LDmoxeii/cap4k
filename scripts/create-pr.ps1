[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $Base,

    [string] $Head,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $Title,

    [Parameter(Mandatory = $true)]
    [string] $BodyFile,

    [string] $Template,

    [switch] $Draft,

    [switch] $DryRun,

    [switch] $AllowDirty,

    [string] $ExpectedHeadSha,
    [string] $FactsFile,
    [string] $ChangedFilesFile,
    [long] $ExpectedPullRequestNumber,
    [string] $ExpectedPullRequestUrl,
    [string] $ExpectedPullRequestBase,
    [string] $ExpectedPullRequestHead,
    [string] $ExpectedPullRequestHeadSha,
    [switch] $MachineReadable
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    $output = & git @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed.`n$($output | Out-String)"
    }
    return @($output)
}

function Get-TrackedPullRequestTemplate {
    $candidates = @(Get-TrackedPullRequestTemplates)

    if ($candidates.Count -eq 0) {
        throw "No tracked PR template found. Checked pull_request_template paths case-insensitively."
    }

    $preferredPatterns = @(
        "^\.github/PULL_REQUEST_TEMPLATE\.md$",
        "^PULL_REQUEST_TEMPLATE\.md$",
        "^\.github/PULL_REQUEST_TEMPLATE/.*\.md$"
    )

    foreach ($pattern in $preferredPatterns) {
        $match = @($candidates | Where-Object { $_ -match "(?i)$pattern" } | Select-Object -First 1)
        if ($match.Count -gt 0) {
            return $match[0]
        }
    }

    return $candidates[0]
}

function Get-TrackedPullRequestTemplates {
    $trackedFiles = Invoke-Git -Arguments @("ls-files")
    return @(
        $trackedFiles |
            Where-Object { $_ -match "(?i)(^|/)(pull_request_template\.md|pull_request_template/.*\.md)$" } |
            Sort-Object
    )
}

function ConvertTo-RepoRelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [string] $RepoRoot
    )

    $resolvedPath = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
    $resolvedRoot = (Resolve-Path -LiteralPath $RepoRoot -ErrorAction Stop).Path
    $relativePath = [System.IO.Path]::GetRelativePath($resolvedRoot, $resolvedPath)
    return ($relativePath -replace "\\", "/")
}

function Resolve-TrackedPullRequestTemplate {
    param(
        [string] $TemplateOverride,

        [Parameter(Mandatory = $true)]
        [string] $RepoRoot
    )

    $trackedTemplates = @(Get-TrackedPullRequestTemplates)
    if ($trackedTemplates.Count -eq 0) {
        throw "No tracked PR template found. Checked pull_request_template paths case-insensitively."
    }

    if (-not $TemplateOverride) {
        $selectedTemplate = Get-TrackedPullRequestTemplate
        return [pscustomobject]@{
            Display = $selectedTemplate
            Path = (Resolve-Path -LiteralPath $selectedTemplate -ErrorAction Stop).Path
        }
    }

    $relativeOverride = ConvertTo-RepoRelativePath -Path $TemplateOverride -RepoRoot $RepoRoot
    $selected = @($trackedTemplates | Where-Object { $_.ToLowerInvariant() -eq $relativeOverride.ToLowerInvariant() })
    if ($selected.Count -eq 0) {
        throw "Template override must reference a tracked PR template. Checked: $relativeOverride"
    }

    return [pscustomobject]@{
        Display = $selected[0]
        Path = (Resolve-Path -LiteralPath $selected[0] -ErrorAction Stop).Path
    }
}

function Assert-PrBranchPolicy {
    param(
        [Parameter(Mandatory = $true)]
        [string] $BaseBranch,

        [Parameter(Mandatory = $true)]
        [string] $HeadBranch
    )

    if ($BaseBranch -cne "master") {
        throw "Unsupported base branch '$BaseBranch'. Allowed base: master."
    }

    if ($HeadBranch -match '[:~^?*\[\\\s]' -or $HeadBranch.StartsWith('-') -or $HeadBranch.EndsWith('.') -or $HeadBranch.Contains('..') -or $HeadBranch.Contains('@{')) {
        throw "Working branch pull requests to master must use an unqualified same-repository head branch, not '$HeadBranch'."
    }

    if ($HeadBranch -notmatch '^(feature|fix|docs)/[^/]+(?:/[^/]+)*$') {
        throw "Working branch pull requests to master must use a short-lived feature/*, fix/*, or docs/* head branch, not '$HeadBranch'."
    }
}

function Invoke-ValidationScript {
    param(
        [Parameter(Mandatory = $true)]
        [string] $TemplatePath,

        [Parameter(Mandatory = $true)]
        [string] $ResolvedBodyFile,

        [Parameter(Mandatory = $true)]
        [string] $BaseBranch,
        [string] $ResolvedFactsFile,
        [string] $ResolvedChangedFilesFile
    )

    $pwsh = (Get-Command pwsh -ErrorAction Stop).Source
    $validationScript = Join-Path $PSScriptRoot "validate-pr-body.ps1"
    $arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $validationScript, "-Template", $TemplatePath, "-BodyFile", $ResolvedBodyFile, "-Base", $BaseBranch, "-RequireChangeType")
    if ($ResolvedFactsFile) { $arguments += @("-FactsFile", $ResolvedFactsFile, "-ChangedFilesFile", $ResolvedChangedFilesFile) }
    $output = & $pwsh @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "PR body validation failed.`n$($output | Out-String)"
    }
    return ($output | Out-String).Trim()
}

function Invoke-Gh {
    param([Parameter(Mandatory = $true)][string[]] $Arguments, [string] $Description = "gh command")
    $output = & gh @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "$Description failed.`n$($output | Out-String)" }
    return @($output)
}

function ConvertTo-ComparableBody {
    param([AllowEmptyString()][string] $Text)
    $normalized = $Text.Replace("`r`n", "`n").Replace("`r", "`n")
    if ($normalized.EndsWith("`n", [System.StringComparison]::Ordinal)) { return $normalized.Substring(0, $normalized.Length - 1) }
    return $normalized
}

try {
    $repoRoot = (@(Invoke-Git -Arguments @("rev-parse", "--show-toplevel"))[0]).Trim()
    Set-Location -LiteralPath $repoRoot

    $currentBranchOutput = @(Invoke-Git -Arguments @("branch", "--show-current"))
    $currentBranch = if ($currentBranchOutput.Count -gt 0) { $currentBranchOutput[0].Trim() } else { "" }
    if (-not $Head) {
        if (-not $currentBranch) {
            throw "Head branch was not provided and the checkout is detached."
        }
        $Head = $currentBranch
    }

    Assert-PrBranchPolicy -BaseBranch $Base -HeadBranch $Head

    $statusLines = @(Invoke-Git -Arguments @("status", "--porcelain"))
    if ($statusLines.Count -gt 0 -and -not $AllowDirty) {
        throw "Working tree has uncommitted changes. Commit or stash them before creating a PR, or pass -AllowDirty for dry-run validation."
    }

    if ([string]::IsNullOrWhiteSpace($FactsFile) -xor [string]::IsNullOrWhiteSpace($ChangedFilesFile)) { throw "FactsFile and ChangedFilesFile must be provided together." }
    if ($ExpectedHeadSha -and $ExpectedHeadSha -notmatch '^(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$') { throw "ExpectedHeadSha must be a complete 40- or 64-character hexadecimal object ID." }
    if ($MachineReadable -and -not $DryRun -and ([string]::IsNullOrWhiteSpace($ExpectedHeadSha) -or [string]::IsNullOrWhiteSpace($FactsFile) -or [string]::IsNullOrWhiteSpace($ChangedFilesFile))) {
        throw "MachineReadable remote mutation requires ExpectedHeadSha, FactsFile, and ChangedFilesFile."
    }
    $expectedPrValues = @($ExpectedPullRequestUrl, $ExpectedPullRequestBase, $ExpectedPullRequestHead, $ExpectedPullRequestHeadSha)
    $expectedPrStringCount = @($expectedPrValues | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
    $hasExpectedPullRequest = $ExpectedPullRequestNumber -gt 0 -or $expectedPrStringCount -gt 0
    if ($hasExpectedPullRequest -and ($ExpectedPullRequestNumber -lt 1 -or $expectedPrStringCount -ne 4)) {
        throw "Expected pull request recovery identity requires number, URL, base, head, and head SHA together."
    }
    if ($hasExpectedPullRequest -and $ExpectedPullRequestHeadSha -notmatch '^(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$') { throw "ExpectedPullRequestHeadSha must be a complete Git object ID." }
    if ($hasExpectedPullRequest -and ($ExpectedPullRequestBase -cne $Base -or $ExpectedPullRequestHead -cne $Head -or -not [string]::Equals($ExpectedPullRequestHeadSha, $ExpectedHeadSha, [System.StringComparison]::OrdinalIgnoreCase))) {
        throw "Expected pull request recovery identity does not match the requested base, head, and head SHA."
    }
    $bodyPath = (Resolve-Path -LiteralPath $BodyFile -ErrorAction Stop).Path
    $factsPath = if ($FactsFile) { (Resolve-Path -LiteralPath $FactsFile -ErrorAction Stop).Path } else { $null }
    $changedFilesPath = if ($ChangedFilesFile) { (Resolve-Path -LiteralPath $ChangedFilesFile -ErrorAction Stop).Path } else { $null }

    $templateInfo = Resolve-TrackedPullRequestTemplate -TemplateOverride $Template -RepoRoot $repoRoot
    $templatePath = $templateInfo.Path
    $templateDisplay = $templateInfo.Display

    Invoke-ValidationScript -TemplatePath $templatePath -ResolvedBodyFile $bodyPath -BaseBranch $Base -ResolvedFactsFile $factsPath -ResolvedChangedFilesFile $changedFilesPath | Out-Null

    if ($DryRun) {
        Write-Output "DRY RUN: would create PR using template $templateDisplay from $Head to $Base."
        exit 0
    }

    Get-Command gh -ErrorAction Stop | Out-Null

    $listJson = (Invoke-Gh -Arguments @("pr", "list", "--state", "open", "--base", $Base, "--head", $Head, "--limit", "2", "--json", "number,url,state,baseRefName,headRefName,headRefOid") -Description "gh pr list") -join "`n"
    $openPullRequests = @($listJson | ConvertFrom-Json)
    if ($openPullRequests.Count -gt 1) { throw "More than one open pull request exists from '$Head' to '$Base'; refusing to choose one." }
    if ($hasExpectedPullRequest) {
        if ($openPullRequests.Count -ne 1) { throw "The recovery input identifies an existing pull request, but it was not observed uniquely; refusing remote mutation." }
        $observed = $openPullRequests[0]
        if ([long]$observed.number -ne $ExpectedPullRequestNumber -or [string]$observed.url -cne $ExpectedPullRequestUrl -or [string]$observed.state -cne 'OPEN' -or [string]$observed.baseRefName -cne $ExpectedPullRequestBase -or [string]$observed.headRefName -cne $ExpectedPullRequestHead -or -not [string]::Equals([string]$observed.headRefOid, $ExpectedPullRequestHeadSha, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "The remotely observed pull request conflicts with the recovery identity; refusing remote mutation."
        }
    }
    if ($openPullRequests.Count -eq 0) {
        $ghArgs = @("pr", "create", "--base", $Base, "--head", $Head, "--title", $Title, "--body-file", $bodyPath)
        if ($Draft) { $ghArgs += "--draft" }
        $createOutput = Invoke-Gh -Arguments $ghArgs -Description "gh pr create"
        $prRef = @($createOutput | Where-Object { $_ -match '^https?://\S+$' } | Select-Object -First 1)[0]
        if (-not $prRef) { throw "gh pr create did not return a pull request URL." }
        $disposition = "created"
    } else { $prRef = [string]$openPullRequests[0].number; $disposition = "reused" }
    $viewJson = (Invoke-Gh -Arguments @("pr", "view", $prRef, "--json", "number,url,state,baseRefName,headRefName,headRefOid,title,body") -Description "gh pr view") -join "`n"
    $remote = $viewJson | ConvertFrom-Json
    if ([string]$remote.state -cne "OPEN") { throw "Remote pull request state must be OPEN; got '$($remote.state)'." }
    if ([string]$remote.baseRefName -cne $Base) { throw "Remote pull request base '$($remote.baseRefName)' does not match expected '$Base'." }
    if ([string]$remote.headRefName -cne $Head) { throw "Remote pull request head '$($remote.headRefName)' does not match expected '$Head'." }
    if ($ExpectedHeadSha -and -not [string]::Equals([string]$remote.headRefOid, $ExpectedHeadSha, [System.StringComparison]::OrdinalIgnoreCase)) { throw "Remote pull request head OID '$($remote.headRefOid)' does not match expected '$ExpectedHeadSha'." }
    if ($hasExpectedPullRequest -and ([long]$remote.number -ne $ExpectedPullRequestNumber -or [string]$remote.url -cne $ExpectedPullRequestUrl)) { throw "Remote pull request number or URL conflicts with the recovery identity." }
    if (-not [string]::Equals([string]$remote.title, $Title, [System.StringComparison]::Ordinal)) { throw "Remote pull request title does not exactly match the requested title; refusing to edit it." }
    $localBody = [System.IO.File]::ReadAllText($bodyPath)
    if (-not [string]::Equals((ConvertTo-ComparableBody $localBody), (ConvertTo-ComparableBody ([string]$remote.body)), [System.StringComparison]::Ordinal)) { throw "Remote pull request body does not exactly match the local body after newline normalization; refusing to edit it." }
    $tempBody = [System.IO.Path]::GetTempFileName()
    try { [System.IO.File]::WriteAllText($tempBody, [string]$remote.body, [System.Text.UTF8Encoding]::new($false)); Invoke-ValidationScript -TemplatePath $templatePath -ResolvedBodyFile $tempBody -BaseBranch $Base -ResolvedFactsFile $factsPath -ResolvedChangedFilesFile $changedFilesPath | Out-Null } finally { Remove-Item -LiteralPath $tempBody -Force -ErrorAction SilentlyContinue }
    if ($MachineReadable) { [pscustomobject]@{ schema = "cap4k.pull-request-result.v1"; disposition = $disposition; remoteVerified = $true; pullRequest = [ordered]@{ number = [long]$remote.number; url = [string]$remote.url; baseBranch = [string]$remote.baseRefName; headBranch = [string]$remote.headRefName; headSha = [string]$remote.headRefOid } } | ConvertTo-Json -Depth 4 -Compress | Write-Output } else { Write-Output ([string]$remote.url) }
    exit 0
}
catch {
    [Console]::Error.WriteLine("ERROR: $($_.Exception.Message) [line $($_.InvocationInfo.ScriptLineNumber)]")
    exit 1
}
