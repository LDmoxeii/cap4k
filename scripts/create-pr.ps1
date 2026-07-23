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

    [switch] $AllowDirty
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
    $candidates = Get-TrackedPullRequestTemplates

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

    $trackedTemplates = Get-TrackedPullRequestTemplates
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

    $publishBranches = @("publish/aliyun-private", "publish/maven-central")
    $protectedBranches = @("master") + $publishBranches

    if ($BaseBranch -ceq "master") {
        if ($HeadBranch -match ":") {
            throw "Working branch pull requests to master must use an unqualified same-repository head branch, not '$HeadBranch'."
        }
        if ($protectedBranches -ccontains $HeadBranch) {
            throw "Working branch pull requests to master must use a short-lived head branch, not protected head '$HeadBranch'."
        }
        return
    }

    if ($publishBranches -ccontains $BaseBranch) {
        if ($HeadBranch -cne "master" -or $HeadBranch -match ":") {
            throw "Publish branch pull requests must use same-repository master as the head branch."
        }
        return
    }

    throw "Unsupported base branch '$BaseBranch'. Allowed bases: master, publish/aliyun-private, publish/maven-central."
}

function Invoke-ValidationScript {
    param(
        [Parameter(Mandatory = $true)]
        [string] $TemplatePath,

        [Parameter(Mandatory = $true)]
        [string] $ResolvedBodyFile,

        [Parameter(Mandatory = $true)]
        [string] $BaseBranch
    )

    $pwsh = (Get-Command pwsh -ErrorAction Stop).Source
    $validationScript = Join-Path $PSScriptRoot "validate-pr-body.ps1"
    $output = & $pwsh -NoProfile -ExecutionPolicy Bypass -File $validationScript -Template $TemplatePath -BodyFile $ResolvedBodyFile -Base $BaseBranch -RequireChangeType 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "PR body validation failed.`n$($output | Out-String)"
    }
    return ($output | Out-String).Trim()
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

    $bodyPath = (Resolve-Path -LiteralPath $BodyFile -ErrorAction Stop).Path

    $templateInfo = Resolve-TrackedPullRequestTemplate -TemplateOverride $Template -RepoRoot $repoRoot
    $templatePath = $templateInfo.Path
    $templateDisplay = $templateInfo.Display

    Invoke-ValidationScript -TemplatePath $templatePath -ResolvedBodyFile $bodyPath -BaseBranch $Base | Out-Null

    if ($DryRun) {
        Write-Output "DRY RUN: would create PR using template $templateDisplay from $Head to $Base."
        exit 0
    }

    Get-Command gh -ErrorAction Stop | Out-Null

    $ghArgs = @("pr", "create", "--base", $Base, "--head", $Head, "--title", $Title, "--body-file", $bodyPath)
    if ($Draft) {
        $ghArgs += "--draft"
    }

    $createOutput = & gh @ghArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "gh pr create failed.`n$($createOutput | Out-String)"
    }

    $prRef = @($createOutput | Where-Object { $_ -match "\S" } | Select-Object -Last 1)[0]
    if (-not $prRef) {
        throw "gh pr create did not return a PR reference."
    }

    $remoteBody = & gh pr view $prRef --json body --jq ".body" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "gh pr view failed after PR creation.`n$($remoteBody | Out-String)"
    }

    $tempBody = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($tempBody, ($remoteBody | Out-String), [System.Text.UTF8Encoding]::new($false))
        Invoke-ValidationScript -TemplatePath $templatePath -ResolvedBodyFile $tempBody -BaseBranch $Base | Out-Null
    }
    finally {
        Remove-Item -LiteralPath $tempBody -Force -ErrorAction SilentlyContinue
    }

    Write-Output $prRef
    exit 0
}
catch {
    [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)")
    exit 1
}
