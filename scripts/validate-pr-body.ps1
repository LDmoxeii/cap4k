[CmdletBinding()]
param(
    [string] $Template = ".github/PULL_REQUEST_TEMPLATE.md",

    [Parameter(Mandatory = $true)]
    [string] $BodyFile,

    [string] $Base,

    [switch] $RequireChangeType
)

$ErrorActionPreference = "Stop"

function Get-MarkdownSection {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Markdown,

        [Parameter(Mandatory = $true)]
        [string] $Heading
    )

    $lines = $Markdown -split "\r?\n"
    $sectionLines = New-Object System.Collections.Generic.List[string]
    $insideSection = $false

    foreach ($line in $lines) {
        if ($line.Trim() -eq $Heading) {
            $insideSection = $true
            continue
        }

        if ($insideSection -and $line -match "^##\s+\S") {
            break
        }

        if ($insideSection) {
            $sectionLines.Add($line)
        }
    }

    return ($sectionLines -join "`n")
}

function Test-Checked {
    param([string] $Mark)
    return $Mark -match "^[xX]$"
}

try {
    $templatePath = (Resolve-Path -LiteralPath $Template -ErrorAction Stop).Path
    $bodyPath = (Resolve-Path -LiteralPath $BodyFile -ErrorAction Stop).Path

    $requiredHeadings = @(
        Get-Content -LiteralPath $templatePath -Encoding UTF8 |
            Where-Object { $_ -match "^##\s+\S" } |
            ForEach-Object { $_.Trim() }
    )

    if ($requiredHeadings.Count -eq 0) {
        throw "Template has no level-2 headings: $templatePath"
    }

    $bodyText = Get-Content -LiteralPath $bodyPath -Raw -Encoding UTF8
    $missing = @()

    foreach ($heading in $requiredHeadings) {
        $pattern = "(?m)^" + [regex]::Escape($heading) + "\s*$"
        if ($bodyText -notmatch $pattern) {
            $missing += $heading
        }
    }

    if ($missing.Count -gt 0) {
        foreach ($heading in $missing) {
            [Console]::Error.WriteLine("Missing required template heading: $heading")
        }
        exit 1
    }

    if ($Base) {
        $targetBranchSection = Get-MarkdownSection -Markdown $bodyText -Heading "## Target Branch"
        $targetBranchLines = @(
            $targetBranchSection -split "\n" |
                Where-Object { $_ -match '^\s*-\s*\[(?<mark>[ xX])\]\s*`(?<branch>[^`]+)`' } |
                ForEach-Object {
                    [pscustomobject]@{
                        Branch = $Matches.branch
                        Checked = Test-Checked -Mark $Matches.mark
                    }
                }
        )

        $baseLine = @($targetBranchLines | Where-Object { $_.Branch -ceq $Base })
        $checkedTargets = @($targetBranchLines | Where-Object { $_.Checked })

        if ($baseLine.Count -eq 0) {
            [Console]::Error.WriteLine("Target Branch section does not list base branch: $Base")
            exit 1
        }

        if (-not $baseLine[0].Checked -or $checkedTargets.Count -ne 1) {
            [Console]::Error.WriteLine("Target Branch selection must check exactly base branch: $Base")
            exit 1
        }
    }

    if ($RequireChangeType) {
        $changeTypeSection = Get-MarkdownSection -Markdown $bodyText -Heading "## Change Type"
        $checkedChangeTypes = @(
            $changeTypeSection -split "\n" |
                Where-Object { $_ -match '^\s*-\s*\[(?<mark>[ xX])\]' -and (Test-Checked -Mark $Matches.mark) }
        )

        if ($checkedChangeTypes.Count -eq 0) {
            [Console]::Error.WriteLine("Change Type section must check at least one item.")
            exit 1
        }
    }

    Write-Output "OK: PR body includes all required template headings."
    exit 0
}
catch {
    [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)")
    exit 1
}
