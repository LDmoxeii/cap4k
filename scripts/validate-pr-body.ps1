[CmdletBinding()]
param(
    [string] $Template = ".github/PULL_REQUEST_TEMPLATE.md",

    [Parameter(Mandatory = $true)]
    [string] $BodyFile,

    [string] $Base,

    [switch] $RequireChangeType,

    [string] $FactsFile,

    [string] $ChangedFilesFile
)

$ErrorActionPreference = "Stop"

function Get-MarkdownSection {
    param([string] $Markdown, [string] $Heading)
    $lines = $Markdown -split "\r?\n"
    $sectionLines = [System.Collections.Generic.List[string]]::new()
    $insideSection = $false
    foreach ($line in $lines) {
        if ($line.Trim() -eq $Heading) { $insideSection = $true; continue }
        if ($insideSection -and $line -match "^##\s+\S") { break }
        if ($insideSection) { $sectionLines.Add($line) }
    }
    return ($sectionLines -join "`n")
}

function Test-Checked { param([string] $Mark) return $Mark -match "^[xX]$" }

function Get-CheckboxItems {
    param([string] $Markdown, [string] $Heading)
    $section = Get-MarkdownSection -Markdown $Markdown -Heading $Heading
    return @($section -split "\r?\n" | Where-Object { $_ -match '^\s*-\s*\[(?<mark>[ xX])\]\s*(?<label>.+?)\s*$' } | ForEach-Object {
        [pscustomobject]@{ Checked = Test-Checked $Matches.mark; Label = $Matches.label.Trim() }
    })
}

function Assert-TemplateCheckboxContract {
    param([string] $TemplateMarkdown, [string] $BodyMarkdown, [string] $Heading)
    $templateItems = @(Get-CheckboxItems -Markdown $TemplateMarkdown -Heading $Heading)
    $bodyItems = @(Get-CheckboxItems -Markdown $BodyMarkdown -Heading $Heading)
    if ($templateItems.Count -eq 0 -or $bodyItems.Count -ne $templateItems.Count) { throw "$Heading must preserve every tracked template checkbox and may not add options." }
    $resolved = [System.Collections.Generic.List[object]]::new()
    foreach ($templateItem in $templateItems) {
        $allowsDetail = $templateItem.Label.EndsWith(':', [System.StringComparison]::Ordinal)
        $matches = @($bodyItems | Where-Object {
            if ($allowsDetail) { $_.Label.StartsWith($templateItem.Label, [System.StringComparison]::Ordinal) }
            else { [string]::Equals($_.Label, $templateItem.Label, [System.StringComparison]::Ordinal) }
        })
        if ($matches.Count -ne 1) { throw "$Heading must preserve tracked template option '$($templateItem.Label)'." }
        $resolved.Add([pscustomobject]@{ Checked=$matches[0].Checked; Label=$matches[0].Label; TemplateLabel=$templateItem.Label; AllowsDetail=$allowsDetail })
    }
    return @($resolved)
}

function Get-MeaningfulText {
    param([string] $Text)
    return (($Text -split "\r?\n") |
        Where-Object { $_ -notmatch '^\s*<!--' -and $_ -notmatch '^\s*Use ' -and $_ -notmatch '^\s*Allowed results:' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and $_ -notin @('-', 'N/A', 'NA', 'None', 'TBD') -and $_ -notmatch '^-?\s*(?:Name the descriptors|Changed contract nodes: N/A - replace|Closure evidence: State how|State cross-module|State what remains|Tell reviewers|If all changed paths)' }) -join "`n"
}

function Assert-MeaningfulSection {
    param([string] $Markdown, [string] $Heading)
    $section = Get-MarkdownSection -Markdown $Markdown -Heading $Heading
    if ([string]::IsNullOrWhiteSpace((Get-MeaningfulText $section))) {
        throw "$Heading must contain concrete content or a reasoned N/A."
    }
}

function Get-IssueValue {
    param([string] $Section, [string] $Label)
    $match = [regex]::Match($Section, "(?mi)^\s*-\s*" + [regex]::Escape($Label) + ":\s*(?<value>.+?)\s*$")
    if (-not $match.Success) { throw "Issue Hierarchy must declare '${Label}:'." }
    return $match.Groups['value'].Value.Trim()
}

function Test-ReasonedNa { param([string] $Value) return $Value -match '^N/A\s+-\s+\S.+' }
function Test-IssueReference { param([string] $Value) return $Value -match '^#(?<number>[1-9][0-9]*)$' }
function Test-ClosingReference { param([string] $Value) return $Value -match '^Closes\s+#(?<number>[1-9][0-9]*)$' }

function Assert-IssueHierarchy {
    param([string] $Markdown)
    $section = Get-MarkdownSection -Markdown $Markdown -Heading '## Issue Hierarchy'
    $parent = Get-IssueValue -Section $section -Label 'Parent'
    $direct = Get-IssueValue -Section $section -Label 'Direct issue'
    $closing = Get-IssueValue -Section $section -Label 'Closing target'
    if (-not (Test-IssueReference $parent) -and -not (Test-ReasonedNa $parent)) { throw 'Parent must be #<number> or N/A - <reason>.' }
    if (-not (Test-IssueReference $direct) -and -not (Test-ReasonedNa $direct)) { throw 'Direct issue must be #<number> or N/A - <reason>.' }
    if ((Test-IssueReference $parent) -and -not (Test-IssueReference $direct)) { throw 'A PR with a Parent must identify its direct Child issue as #<number>.' }
    if (-not (Test-ClosingReference $closing) -and -not (Test-ReasonedNa $closing)) { throw 'Closing target must be Closes #<number> or N/A - <reason>.' }
    if ((Test-IssueReference $parent) -and (Test-IssueReference $direct)) {
        $parentNumber = [regex]::Match($parent, '\d+').Value
        $directNumber = [regex]::Match($direct, '\d+').Value
        if ($parentNumber -eq $directNumber) { throw 'Parent and Direct issue must reference different issues.' }
    }
    if (Test-ClosingReference $closing) {
        if (-not (Test-IssueReference $direct)) { throw 'Closing target requires a concrete Direct issue.' }
        $directNumber = [regex]::Match($direct, '\d+').Value
        $closingNumber = [regex]::Match($closing, '\d+').Value
        if ($directNumber -ne $closingNumber) { throw 'Closing target must close the Direct issue, not the Parent or a sibling issue.' }
    }
}

function Assert-AcceptanceIds {
    param([string] $Markdown)
    $section = Get-MarkdownSection -Markdown $Markdown -Heading '## Acceptance IDs'
    if ($section -match '\bA[1-9][0-9]*\b') { return }
    $meaningful = Get-MeaningfulText $section
    if ($meaningful -notmatch '(?m)^-?\s*N/A\s+-\s+\S.+') { throw 'Acceptance IDs must list at least one A<number> or N/A - <reason>.' }
}

function Get-CapabilityImpactRows {
    param([string] $Markdown)
    $section = Get-MarkdownSection -Markdown $Markdown -Heading '## Capability Impact'
    $expectedSurfaces = @('Runtime', 'Generator', 'Analyzer', 'AgentFacts', 'Public Docs', 'Skill')
    $rows = @{}
    foreach ($line in ($section -split "\r?\n")) {
        $match = [regex]::Match($line, '^\|\s*(?<surface>Runtime|Generator|Analyzer|AgentFacts|Public Docs|Skill)\s*\|\s*(?<result>modified|verified-no-change|not-applicable)\s*\|\s*(?<evidence>.*?)\s*\|$')
        if (-not $match.Success) { continue }
        $surface = $match.Groups['surface'].Value
        if ($rows.ContainsKey($surface)) { throw "Capability Impact contains duplicate surface row: $surface" }
        $evidence = $match.Groups['evidence'].Value.Trim()
        if ($evidence -in @('', '-', 'N/A', 'TBD') -or $evidence -match '^Explain\b|^Link changed\b') { throw "Capability Impact row '$surface' requires concrete evidence or a reason." }
        $rows[$surface] = [pscustomobject]@{ Status = $match.Groups['result'].Value; Evidence = $evidence }
    }
    $missing = @($expectedSurfaces | Where-Object { -not $rows.ContainsKey($_) })
    if ($missing.Count -gt 0) { throw "Capability Impact must cover every surface. Missing: $($missing -join ', ')." }
    return $rows
}

function Convert-GlobToRegex {
    param([string] $Pattern)
    $builder = [System.Text.StringBuilder]::new('^')
    for ($index = 0; $index -lt $Pattern.Length; $index++) {
        $character = $Pattern[$index]
        if ($character -eq '*') {
            if ($index + 1 -lt $Pattern.Length -and $Pattern[$index + 1] -eq '*') {
                [void]$builder.Append('.*')
                $index++
            } else {
                [void]$builder.Append('[^/]*')
            }
        } elseif ($character -eq '?') {
            [void]$builder.Append('[^/]')
        } else {
            [void]$builder.Append([regex]::Escape([string]$character))
        }
    }
    [void]$builder.Append('$')
    return $builder.ToString()
}

function Get-ChangedPaths {
    param([string] $Path)
    $paths = [System.Collections.Generic.List[string]]::new()
    foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "`t"
        if ($parts.Count -eq 1) {
            $paths.Add($parts[0].Replace('\', '/'))
            continue
        }
        $status = $parts[0]
        if ($status -match '^[RC][0-9]*$' -and $parts.Count -ge 3) {
            $paths.Add($parts[1].Replace('\', '/'))
            $paths.Add($parts[2].Replace('\', '/'))
        } elseif ($parts.Count -ge 2) {
            $paths.Add($parts[1].Replace('\', '/'))
        }
    }
    return @($paths | Sort-Object -Unique)
}

function Resolve-ChangedContractSeeds {
    param([string[]] $ChangedPaths, [object[]] $PathRules)
    $seeds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $matched = @{}
    foreach ($path in $ChangedPaths) {
        foreach ($rule in $PathRules) {
            if ($path -match (Convert-GlobToRegex -Pattern ([string]$rule.pattern))) {
                $ruleSeeds = @($rule.seedNodes)
                foreach ($seed in $ruleSeeds) { [void]$seeds.Add([string]$seed) }
                $matched[$path] = [pscustomobject]@{ Pattern = [string]$rule.pattern; Seeds = $ruleSeeds; Classification = [string]$rule.classification }
                break
            }
        }
    }
    return [pscustomobject]@{ SeedNodes = @($seeds | Sort-Object); MatchedPaths = $matched }
}

function Get-DeclaredChangedNodes {
    param([string] $Markdown, [string[]] $ExpectedSeeds)
    $section = Get-MarkdownSection -Markdown $Markdown -Heading '## Propagation Closure'
    $match = [regex]::Match($section, '(?mi)^\s*-\s*Changed contract nodes:\s*(?<value>.+?)\s*$')
    if (-not $match.Success) { throw 'Propagation Closure must declare "Changed contract nodes:".' }
    $value = $match.Groups['value'].Value.Trim()
    if ($ExpectedSeeds.Count -eq 0) {
        if (-not (Test-ReasonedNa $value)) { throw 'Changed contract nodes must be N/A - <reason> when the diff has no capability-contract seed.' }
        return @()
    }
    if (Test-ReasonedNa $value) { throw "Changed contract nodes must list the diff-derived seeds: $($ExpectedSeeds -join ', ')." }
    $declared = @([regex]::Matches($value, '`(?<node>[a-z][a-z0-9.-]+)`') | ForEach-Object { $_.Groups['node'].Value } | Sort-Object -Unique)
    $difference = @(Compare-Object -ReferenceObject @($ExpectedSeeds | Sort-Object -Unique) -DifferenceObject $declared)
    if ($difference.Count -gt 0) {
        throw "Changed contract nodes do not match the code-derived diff seeds. expected=[$($ExpectedSeeds -join ', ')] actual=[$($declared -join ', ')]."
    }
    return $declared
}

function Assert-CapabilityImpactAlignment {
    param([string] $Markdown, [object] $Facts, [string] $ChangedFilesPath)
    if ($Facts.schema -ne 'cap4k.capability-contract-facts.v3') { throw "Capability impact alignment requires facts schema cap4k.capability-contract-facts.v3; got $($Facts.schema)." }
    if ([string]$Facts.pathMatchPolicy -ne 'first_match') { throw "Unsupported capability path match policy: $($Facts.pathMatchPolicy)" }

    $rows = Get-CapabilityImpactRows -Markdown $Markdown
    $changedPaths = Get-ChangedPaths -Path $ChangedFilesPath
    $resolved = Resolve-ChangedContractSeeds -ChangedPaths $changedPaths -PathRules @($Facts.pathRules)
    $seeds = @($resolved.SeedNodes)
    [void](Get-DeclaredChangedNodes -Markdown $Markdown -ExpectedSeeds $seeds)

    $impacted = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($seed in $seeds) {
        [void]$impacted.Add($seed)
        $closureProperty = $Facts.propagationClosure.PSObject.Properties[$seed]
        if ($null -ne $closureProperty) {
            foreach ($target in @($closureProperty.Value)) { [void]$impacted.Add([string]$target) }
        }
    }

    foreach ($surface in @($Facts.surfaces)) {
        $name = [string]$surface.name
        $nodeId = [string]$surface.nodeId
        $status = [string]$rows[$name].Status
        $isDirect = $nodeId -in $seeds
        $isImpacted = $impacted.Contains($nodeId)
        if ($isDirect -and $status -ne 'modified') {
            throw "Capability Impact row '$name' must be modified because the diff directly maps to $nodeId."
        }
        if (-not $isDirect -and $status -eq 'modified') {
            throw "Capability Impact row '$name' claims modified but no changed path maps directly to $nodeId."
        }
        if ($isImpacted -and $status -eq 'not-applicable') {
            throw "Capability Impact row '$name' cannot be not-applicable because $nodeId is in the direct/transitive propagation closure for seeds [$($seeds -join ', ')]."
        }
    }
}

function Assert-NoAuthorPlaceholders {
    param([string] $Markdown)
    foreach ($line in ($Markdown -split "\r?\n")) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed -match '^<!--' -or $trimmed -match '^Use ' -or $trimmed -match '^Allowed results:') { continue }
        if ($trimmed -eq '-') { throw 'PR body contains a standalone placeholder dash.' }
        if ($trimmed -match '(?i)\b(?:TBD|TODO)\b|<summary>|<reason>|\bExplain the\b|\bLink changed\b|N/A\s+-\s+explain why|^-?\s*(?:Name the descriptors|Changed contract nodes: N/A - replace|Closure evidence: State how|State cross-module|State what remains|Tell reviewers|If all changed paths)') {
            throw "PR body contains an unresolved author placeholder and requires concrete evidence: $trimmed"
        }
    }
}
function Assert-AgentReview {
    param([string] $TemplateMarkdown, [string] $Markdown)
    $options = @(Assert-TemplateCheckboxContract -TemplateMarkdown $TemplateMarkdown -BodyMarkdown $Markdown -Heading '## Agent Review')
    $checked = @($options | Where-Object Checked)
    if ($checked.Count -ne 1) { throw 'Agent Review must check exactly one option.' }
    if ($checked[0].TemplateLabel -ceq 'Not requested because:') {
        $reason = $checked[0].Label.Substring($checked[0].TemplateLabel.Length).Trim()
        if (-not $reason -or $reason -match '^(?:-|N/?A|None|TBD|TODO)$') { throw 'Agent Review "Not requested" must include a concrete reason after the colon.' }
    }
}
function Assert-Verification {
    param([string] $TemplateMarkdown, [string] $Markdown)
    $items = @(Assert-TemplateCheckboxContract -TemplateMarkdown $TemplateMarkdown -BodyMarkdown $Markdown -Heading '## Verification')
    $checked = @($items | Where-Object Checked)
    $notRun = @($checked | Where-Object { $_.TemplateLabel -ceq 'Not run because:' })
    $executed = @($checked | Where-Object { $_.TemplateLabel -cne 'Not run because:' })
    if ($executed.Count -gt 0 -and $notRun.Count -gt 0) { throw 'Verification cannot select executed checks and Not run because together.' }
    if ($executed.Count -gt 0) {
        foreach ($item in $executed) {
            if ($item.AllowsDetail -and [string]::IsNullOrWhiteSpace($item.Label.Substring($item.TemplateLabel.Length))) { throw "Checked Verification item requires concrete execution detail: $($item.Label)" }
        }
        return
    }
    if ($notRun.Count -ne 1) { throw 'Verification must check at least one executed item, or check Not run because with a concrete reason.' }
    $reason = $notRun[0].Label.Substring($notRun[0].TemplateLabel.Length).Trim()
    if (-not $reason -or $reason -match '^(?:-|N/?A|None|TBD|TODO)$') { throw 'Verification "Not run because" must include a concrete reason after the colon.' }
}

try {
    $templatePath = (Resolve-Path -LiteralPath $Template -ErrorAction Stop).Path
    $bodyPath = (Resolve-Path -LiteralPath $BodyFile -ErrorAction Stop).Path
    $templateText = Get-Content -LiteralPath $templatePath -Raw -Encoding UTF8
    $requiredHeadings = @($templateText -split "\r?\n" | Where-Object { $_ -match "^##\s+\S" } | ForEach-Object Trim)
    if ($requiredHeadings.Count -eq 0) { throw "Template has no level-2 headings: $templatePath" }

    $bodyText = Get-Content -LiteralPath $bodyPath -Raw -Encoding UTF8
    Assert-NoAuthorPlaceholders -Markdown $bodyText
    $missing = @($requiredHeadings | Where-Object { $bodyText -notmatch ("(?m)^" + [regex]::Escape($_) + "\s*$") })
    if ($missing.Count -gt 0) { throw "Missing required template heading: $($missing -join ', ')" }

    if ($Base) {
        $targetBranchLines = @(Assert-TemplateCheckboxContract -TemplateMarkdown $templateText -BodyMarkdown $bodyText -Heading '## Target Branch')
        $baseLabel = ([string][char]96) + $Base + ([string][char]96)
        $baseLine = @($targetBranchLines | Where-Object { $_.TemplateLabel -ceq $baseLabel })
        $checkedTargets = @($targetBranchLines | Where-Object Checked)
        if ($baseLine.Count -eq 0) { throw "Target Branch section does not list base branch: $Base" }
        if (-not $baseLine[0].Checked -or $checkedTargets.Count -ne 1) { throw "Target Branch selection must check exactly base branch: $Base" }
    }

    if ($RequireChangeType) {
        $changeTypes = @(Assert-TemplateCheckboxContract -TemplateMarkdown $templateText -BodyMarkdown $bodyText -Heading '## Change Type')
        if (@($changeTypes | Where-Object Checked).Count -eq 0) { throw 'Change Type section must check at least one item.' }
    }

    if ('## Issue Hierarchy' -in $requiredHeadings) {
        Assert-IssueHierarchy -Markdown $bodyText
        Assert-AcceptanceIds -Markdown $bodyText
        [void](Get-CapabilityImpactRows -Markdown $bodyText)
        foreach ($heading in @('## Summary', '## Shared Contracts', '## Propagation Closure', '## Composition Evidence', '## Sibling Slice Responsibility', '## Audit Focus', '## Full Gradle Skip Reason', '## Related Spec Or Plan', '## Release Note')) {
            Assert-MeaningfulSection -Markdown $bodyText -Heading $heading
        }
        Assert-Verification -TemplateMarkdown $templateText -Markdown $bodyText
        Assert-AgentReview -TemplateMarkdown $templateText -Markdown $bodyText
        if ([string]::IsNullOrWhiteSpace($FactsFile) -xor [string]::IsNullOrWhiteSpace($ChangedFilesFile)) {
            throw 'FactsFile and ChangedFilesFile must be provided together for diff-aware capability impact validation.'
        }
        if (-not [string]::IsNullOrWhiteSpace($FactsFile)) {
            $facts = Get-Content -LiteralPath (Resolve-Path -LiteralPath $FactsFile -ErrorAction Stop) -Raw -Encoding UTF8 | ConvertFrom-Json
            $changedFilesPath = (Resolve-Path -LiteralPath $ChangedFilesFile -ErrorAction Stop).Path
            Assert-CapabilityImpactAlignment -Markdown $bodyText -Facts $facts -ChangedFilesPath $changedFilesPath
        }
    }

    Write-Output 'OK: PR body includes required headings and capability-governance evidence.'
    exit 0
}
catch {
    [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)")
    exit 1
}
