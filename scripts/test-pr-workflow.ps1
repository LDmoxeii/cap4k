[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$pwsh = (Get-Command pwsh -ErrorAction Stop).Source
$validateScript = Join-Path $PSScriptRoot "validate-pr-body.ps1"
$createScript = Join-Path $PSScriptRoot "create-pr.ps1"
$finishProviderScript = Join-Path $PSScriptRoot "comet-finish-pr.ps1"
$cometConfig = Join-Path $repoRoot ".comet/config.yaml"
$exportFactsScript = Join-Path $PSScriptRoot 'export-capability-contract-facts.ps1'
$classifyScript = Join-Path $PSScriptRoot 'classify-ci-change.ps1'
$ciWorkflow = Join-Path $repoRoot ".github/workflows/ci.yml"

function Invoke-ScriptProcess {
    param([string] $Script, [string[]] $Arguments, [int] $ExpectedExitCode, [string] $ExpectedOutputPattern, [string] $WorkingDirectory)
    $effectiveWorkingDirectory = if ($WorkingDirectory) { $WorkingDirectory } else { $repoRoot }
    Push-Location -LiteralPath $effectiveWorkingDirectory
    try {
        $output = & $pwsh -NoProfile -ExecutionPolicy Bypass -File $Script @Arguments 2>&1
        $actualExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $text = ($output | Out-String).Trim()
    if ($actualExitCode -ne $ExpectedExitCode) { throw "Expected exit code $ExpectedExitCode from $Script with arguments [$($Arguments -join ', ')] but got $actualExitCode.`n$text" }
    if ($ExpectedOutputPattern -and $text -notmatch $ExpectedOutputPattern) { throw "Expected output to match '$ExpectedOutputPattern'.`n$text" }
}

function Assert-CiClassification {
    param(
        [string] $Name,
        [string[]] $DiffLines,
        [bool] $RunGradle,
        [bool] $DocsOnly,
        [bool] $GovernanceSkillOnly,
        [bool] $GradleSkippable,
        [string] $TempRoot
    )

    $safeName = $Name -replace '[^a-zA-Z0-9.-]', '-'
    $diffFile = Join-Path $TempRoot "classification-$safeName.txt"
    [IO.File]::WriteAllLines($diffFile, $DiffLines, [System.Text.UTF8Encoding]::new($false))
    $output = & $pwsh -NoProfile -ExecutionPolicy Bypass -File $classifyScript -ChangedFilesFile $diffFile 2>&1
    $actualExitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    if ($actualExitCode -ne 0) { throw "Classification '$Name' failed with exit code $actualExitCode.`n$text" }
    $result = $text | ConvertFrom-Json

    $expectedChangedPathCount = 0
    foreach ($line in $DiffLines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "`t"
        $expectedChangedPathCount += if ($parts[0] -match '^[RC][0-9]*$' -and $parts.Count -ge 3) { 2 } else { 1 }
    }
    $expected = [ordered]@{
        run_gradle = $RunGradle
        docs_only = $DocsOnly
        governance_skill_only = $GovernanceSkillOnly
        gradle_skippable = $GradleSkippable
        changed_file_count = $expectedChangedPathCount
    }
    foreach ($entry in $expected.GetEnumerator()) {
        $actual = $result.($entry.Key)
        if ($actual -ne $entry.Value) {
            throw "Classification '$Name' expected $($entry.Key)=$($entry.Value) but got $actual."
        }
    }
}

function New-ValidPrBody {
    param(
        [string] $Parent = '#123',
        [string] $Direct = '#124',
        [string] $Closing = 'Closes #124',
        [hashtable] $Statuses = @{
            Runtime = 'verified-no-change'
            Generator = 'modified'
            Analyzer = 'verified-no-change'
            AgentFacts = 'verified-no-change'
            'Public Docs' = 'verified-no-change'
            Skill = 'verified-no-change'
        },
        [string] $ChangedNodes = '`surface.generator`'
    )
    return @"
## Summary

- Implements the child slice and preserves the parent contract.

## Target Branch

- [x] ``master``

## Change Type

- [x] Code, build, scripts, workflow, tests, fixtures, or templates
- [ ] Documentation-only
- [x] Repository governance or GitHub configuration

## Issue Hierarchy

- Parent: $Parent
- Direct issue: $Direct
- Closing target: $Closing

## Acceptance IDs

- A1
- A3

## Capability Impact

| Surface | Result | Evidence |
| --- | --- | --- |
| Runtime | $($Statuses['Runtime']) | Runtime contract and facts guard were checked. |
| Generator | $($Statuses['Generator']) | Descriptor projection and task registration tests pass. |
| Analyzer | $($Statuses['Analyzer']) | Analysis metadata compatibility was checked against the propagation closure. |
| AgentFacts | $($Statuses['AgentFacts']) | Agent section facts were checked against the shared catalog. |
| Public Docs | $($Statuses['Public Docs']) | Contract-marked reference tables were checked against generated facts. |
| Skill | $($Statuses['Skill']) | Routing sections were checked against generated facts. |

## Shared Contracts

- Pipeline descriptors, public tasks, Agent section catalog, Runtime facts, and PR governance.

## Propagation Closure

- Changed contract nodes: $ChangedNodes
- Closure evidence: Generated capability facts prove direct and transitive closure from the declared seeds.

## Composition Evidence

- Focused checks pass; parent closure will confirm all accepted child commits on one origin/master lineage.

## Sibling Slice Responsibility

- Issue #125 owns release follow-up and is not closed by this PR.

## Audit Focus

- Check dependency-edge direction, false negatives in marked sets, and parent/child closing semantics.

## Verification

- [ ] Full Gradle check: ``./gradlew check``
- [x] Focused tests: capability contract and PR workflow tests
- [x] Capability contract validation: ``./scripts/validate-capability-contract.ps1``
- [x] Static validation: PowerShell parser and YAML review
- [ ] Not run because:

## Full Gradle Skip Reason

- N/A - full Gradle required

## Related Spec Or Plan

- ``docs/comet/changes/public-surface-contract/specs/capability-contract-governance/spec.md``

## Agent Review

- [ ] Requested as non-blocking advisory review
- [x] Not requested because: deterministic validation is sufficient for this fixture.

## Release Note

- Capability contract governance and hierarchy checks.
"@
}

function Invoke-ProcessWithInput {
    param([string] $Script, [string] $InputText, [int] $ExpectedExitCode, [string] $ExpectedOutputPattern, [string] $WorkingDirectory)
    $psi = [Diagnostics.ProcessStartInfo]::new($pwsh)
    $psi.WorkingDirectory = $WorkingDirectory
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    foreach ($arg in @('-NoProfile','-ExecutionPolicy','Bypass','-File',$Script)) { [void]$psi.ArgumentList.Add($arg) }
    $process = [Diagnostics.Process]::Start($psi)
    $process.StandardInput.Write($InputText)
    $process.StandardInput.Close()
    $outText = $process.StandardOutput.ReadToEnd()
    $errText = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $text = ($outText + "`n" + $errText).Trim()
    if ($process.ExitCode -ne $ExpectedExitCode) { throw "Expected exit code $ExpectedExitCode but got $($process.ExitCode).`n$text" }
    if ($ExpectedOutputPattern -and $text -notmatch $ExpectedOutputPattern) { throw "Expected output to match '$ExpectedOutputPattern'.`n$text" }
    return [pscustomobject]@{ Stdout = $outText.Trim(); Stderr = $errText.Trim() }
}

function Write-Utf8File {
    param([string] $Path, [string] $Text)
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}

function Get-Sha256Hex {
    param([string] $Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-CanonicalTextSha256 {
    param([string] $Path)
    $text = [Text.UTF8Encoding]::new($false, $true).GetString([IO.File]::ReadAllBytes($Path))
    $canonical = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($canonical)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Get-AuthoringFingerprint {
    param([object] $Artifact)
    $normalize = {
        param([string] $Value)
        $result = $Value.Replace("`r`n", "`n").Replace("`r", "`n")
        if ($result.EndsWith("`n", [StringComparison]::Ordinal)) { return $result.Substring(0, $result.Length - 1) }
        return $result
    }
    # Canonical fingerprint input is compact JSON for this exact ordered shape. Text is newline-normalized,
    # specs are sorted ordinally by path, and the UTF-8 bytes (without BOM) are hashed with SHA-256.
    $canonical = [ordered]@{
        title = (& $normalize ([string]$Artifact.title))
        body = (& $normalize ([string]$Artifact.body))
        artifact = [ordered]@{
            schema = [string]$Artifact.schema
            change = [string]$Artifact.change
            baseBranch = [string]$Artifact.baseBranch
            headBranch = [string]$Artifact.headBranch
        }
        source = [ordered]@{ stateVersion = [long]$Artifact.source.stateVersion; verificationResult = [string]$Artifact.source.verificationResult }
        verification = [ordered]@{
            path = [string]$Artifact.source.verification.path
            candidateId = [string]$Artifact.source.verification.candidateId
            verifierExecutionRef = [string]$Artifact.source.verification.verifierExecutionRef
            iteration = [long]$Artifact.source.verification.iteration
            attempt = [long]$Artifact.source.verification.attempt
        }
        brief = [ordered]@{ path = [string]$Artifact.source.brief.path; sha256 = ([string]$Artifact.source.brief.sha256).ToLowerInvariant() }
        specs = @($Artifact.source.specs | Sort-Object { [string]$_.path } | ForEach-Object { [ordered]@{ path = [string]$_.path; sha256 = ([string]$_.sha256).ToLowerInvariant() } })
        template = [ordered]@{ path = [string]$Artifact.source.template.path; sha256 = ([string]$Artifact.source.template.sha256).ToLowerInvariant() }
        preArchiveHeadSha = ([string]$Artifact.source.preArchiveHeadSha).ToLowerInvariant()
        preArchiveTreeSha = ([string]$Artifact.source.preArchiveTreeSha).ToLowerInvariant()
        factsSha256 = ([string]$Artifact.source.facts.sha256).ToLowerInvariant()
    }
    $json = $canonical | ConvertTo-Json -Compress -Depth 20
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Invoke-TestGit {
    param([string] $Repository, [string[]] $Arguments)
    $output = & git -C $Repository @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git -C $Repository $($Arguments -join ' ') failed.`n$($output | Out-String)" }
    return @($output)
}

function Get-WorkingTreeSha {
    param([string] $Repository, [string] $TempRoot)
    $realIndexTreeBefore = (@(Invoke-TestGit $Repository @('write-tree'))[0]).Trim()
    $realIndexPathText = (@(Invoke-TestGit $Repository @('rev-parse','--git-path','index'))[0]).Trim()
    $realIndexPath = if ([IO.Path]::IsPathRooted($realIndexPathText)) { $realIndexPathText } else { Join-Path $Repository $realIndexPathText }
    $realIndexHashBefore = if (Test-Path -LiteralPath $realIndexPath -PathType Leaf) { Get-Sha256Hex $realIndexPath } else { $null }
    $indexPath = Join-Path $TempRoot ("snapshot-index-" + [Guid]::NewGuid().ToString('N'))
    $oldIndex = $env:GIT_INDEX_FILE
    try {
        $env:GIT_INDEX_FILE = $indexPath
        Invoke-TestGit $Repository @('read-tree','HEAD') | Out-Null
        Invoke-TestGit $Repository @('add','-A') | Out-Null
        $snapshotTree = (@(Invoke-TestGit $Repository @('write-tree'))[0]).Trim()
    } finally {
        $env:GIT_INDEX_FILE = $oldIndex
        Remove-Item -LiteralPath $indexPath -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath "$indexPath.lock" -Force -ErrorAction SilentlyContinue
    }
    $realIndexTreeAfter = (@(Invoke-TestGit $Repository @('write-tree'))[0]).Trim()
    $realIndexHashAfter = if (Test-Path -LiteralPath $realIndexPath -PathType Leaf) { Get-Sha256Hex $realIndexPath } else { $null }
    if ($realIndexTreeAfter -cne $realIndexTreeBefore) { throw 'Isolated working-tree snapshot changed the real Git index tree.' }
    if ($realIndexHashAfter -cne $realIndexHashBefore) { throw 'Isolated working-tree snapshot changed the real Git index file bytes.' }
    return $snapshotTree
}

function Assert-ProviderSuccess {
    param([object] $ProcessResult, [string] $ExpectedDisposition)
    if (-not [string]::IsNullOrWhiteSpace($ProcessResult.Stderr)) { throw "Provider success polluted stderr: $($ProcessResult.Stderr)" }
    $stdoutLines = @($ProcessResult.Stdout -split "\r?\n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($stdoutLines.Count -ne 1) { throw "Provider success must emit exactly one stdout line.`n$($ProcessResult.Stdout)" }
    $result = $stdoutLines[0] | ConvertFrom-Json
    if ($result.schema -cne 'comet.native.pull-request-finish-result.v1' -or $result.disposition -cne $ExpectedDisposition -or $result.remoteVerified -cne $true) { throw "Unexpected provider result: $($ProcessResult.Stdout)" }
    return $result
}

function Assert-ProviderFailure {
    param([object] $ProcessResult)
    if (-not [string]::IsNullOrWhiteSpace($ProcessResult.Stdout)) { throw "Provider failure polluted stdout: $($ProcessResult.Stdout)" }
    if ($ProcessResult.Stderr -notmatch '^ERROR:') { throw "Provider failure must diagnose on stderr: $($ProcessResult.Stderr)" }
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("cap4k-pr-workflow-test-" + [System.Guid]::NewGuid())
New-Item -ItemType Directory -Path $tempRoot | Out-Null
$detachedWorktree = Join-Path $tempRoot "detached-worktree"
try {
    $configText = Get-Content -LiteralPath $cometConfig -Raw -Encoding UTF8
    foreach ($requiredConfigPattern in @(
        '(?m)^\s+provider:\s+repository-command\s*$',
        '(?m)^\s+-\s+scripts/comet-finish-pr\.ps1\s*$',
        '(?m)^\s+timeout_ms:\s+120000\s*$',
        '(?m)^\s+include:\s*$',
        '(?m)^\s+-\s+"\*\*/\*"\s*$',
        '(?m)^\s+exclude:\s+\[\]\s*$',
        '(?m)^\s+max_files:\s+10000\s*$',
        '(?m)^\s+max_total_bytes:\s+268435456\s*$',
        '(?m)^\s+max_duration_ms:\s+60000\s*$'
    )) {
        if ($configText -notmatch $requiredConfigPattern) { throw "Comet config is missing required provider/snapshot contract: $requiredConfigPattern" }
    }

    $workflowText = Get-Content -LiteralPath $ciWorkflow -Raw -Encoding UTF8
    foreach ($required in @('classify-ci-change.ps1', 'validate-pr-body.ps1', 'export-capability-contract-facts.ps1', 'validate-capability-contract.ps1', 'validate-cap4k-skills.ps1', 'validate-current-runtime-facts.ps1', 'test-capability-contract.ps1', 'test-pr-workflow.ps1', 'ChangedFilesFile', 'git diff --name-status --find-renames')) {
        if (-not $workflowText.Contains($required)) { throw "CI workflow must invoke or declare $required." }
    }
    if (-not $workflowText.Contains("if (`$env:BASE_REF -cne 'master')")) { throw 'CI must reject pull requests whose base is not master.' }

    Assert-CiClassification -Name 'docs' -DiffLines @("M`tdocs/public/reference/agent-api.md") -RunGradle $false -DocsOnly $true -GovernanceSkillOnly $false -GradleSkippable $true -TempRoot $tempRoot
    Assert-CiClassification -Name 'agents' -DiffLines @("M`tAGENTS.md") -RunGradle $false -DocsOnly $false -GovernanceSkillOnly $true -GradleSkippable $true -TempRoot $tempRoot
    Assert-CiClassification -Name 'repo-skill' -DiffLines @("M`t.agents/skills/issue-governance/SKILL.md") -RunGradle $false -DocsOnly $false -GovernanceSkillOnly $true -GradleSkippable $true -TempRoot $tempRoot
    Assert-CiClassification -Name 'authoring-skill' -DiffLines @("M`tskills/cap4k-authoring/routing.yaml") -RunGradle $false -DocsOnly $false -GovernanceSkillOnly $true -GradleSkippable $true -TempRoot $tempRoot
    Assert-CiClassification -Name 'skill-validator' -DiffLines @("M`tskills/scripts/validate-cap4k-skills.ps1") -RunGradle $false -DocsOnly $false -GovernanceSkillOnly $true -GradleSkippable $true -TempRoot $tempRoot
    Assert-CiClassification -Name 'comet-config' -DiffLines @("M`t.comet/config.yaml") -RunGradle $false -DocsOnly $false -GovernanceSkillOnly $true -GradleSkippable $true -TempRoot $tempRoot
    Assert-CiClassification -Name 'mixed-lightweight' -DiffLines @("M`tdocs/public/reference/agent-api.md", "M`tskills/cap4k-authoring/routing.yaml") -RunGradle $false -DocsOnly $false -GovernanceSkillOnly $false -GradleSkippable $true -TempRoot $tempRoot
    Assert-CiClassification -Name 'workflow' -DiffLines @("M`t.github/workflows/ci.yml") -RunGradle $true -DocsOnly $false -GovernanceSkillOnly $false -GradleSkippable $false -TempRoot $tempRoot
    Assert-CiClassification -Name 'root-script' -DiffLines @("M`tscripts/validate-pr-body.ps1") -RunGradle $true -DocsOnly $false -GovernanceSkillOnly $false -GradleSkippable $false -TempRoot $tempRoot
    Assert-CiClassification -Name 'native-finish-provider' -DiffLines @("A`tscripts/comet-finish-pr.ps1") -RunGradle $true -DocsOnly $false -GovernanceSkillOnly $false -GradleSkippable $false -TempRoot $tempRoot
    Assert-CiClassification -Name 'source' -DiffLines @("M`tcap4k-plugin-pipeline-api/src/main/kotlin/Contract.kt") -RunGradle $true -DocsOnly $false -GovernanceSkillOnly $false -GradleSkippable $false -TempRoot $tempRoot
    Assert-CiClassification -Name 'mixed-impacting' -DiffLines @("M`tskills/cap4k-authoring/routing.yaml", "M`tscripts/validate-pr-body.ps1") -RunGradle $true -DocsOnly $false -GovernanceSkillOnly $false -GradleSkippable $false -TempRoot $tempRoot
    Assert-CiClassification -Name 'rename-to-lightweight' -DiffLines @("R100`tscripts/old.ps1`tskills/scripts/old.ps1") -RunGradle $true -DocsOnly $false -GovernanceSkillOnly $false -GradleSkippable $false -TempRoot $tempRoot
    Assert-CiClassification -Name 'rename-from-lightweight' -DiffLines @("R100`tskills/scripts/old.ps1`tscripts/old.ps1") -RunGradle $true -DocsOnly $false -GovernanceSkillOnly $false -GradleSkippable $false -TempRoot $tempRoot

    $validBody = Join-Path $tempRoot 'valid.md'
    $sameIssueBody = Join-Path $tempRoot 'same-issue.md'
    $missingDirectBody = Join-Path $tempRoot 'missing-direct.md'
    $wrongClosingBody = Join-Path $tempRoot 'wrong-closing.md'
    $bareNaBody = Join-Path $tempRoot 'bare-na.md'
    $missingSurfaceBody = Join-Path $tempRoot 'missing-surface.md'
    $placeholderEvidenceBody = Join-Path $tempRoot 'placeholder-evidence.md'
    $wrongTargetBody = Join-Path $tempRoot 'wrong-target.md'
    $wrongCaseTargetBody = Join-Path $tempRoot 'wrong-case-target.md'
    $customTemplate = Join-Path $tempRoot 'custom-template.md'
    $customBody = Join-Path $tempRoot 'custom-body.md'
    $factsFile = Join-Path $tempRoot 'facts.json'
    $generatorChanges = Join-Path $tempRoot 'generator-changes.txt'
    $publicDocsChanges = Join-Path $tempRoot 'public-docs-changes.txt'
    $governanceChanges = Join-Path $tempRoot 'governance-changes.txt'
    $renameChanges = Join-Path $tempRoot 'rename-changes.txt'

    & $exportFactsScript -OutputFile $factsFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to export capability facts for PR workflow tests.' }
    [IO.File]::WriteAllText($generatorChanges, "M`tcap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineCapabilityDescriptors.kt`n")
    [IO.File]::WriteAllText($publicDocsChanges, "M`tdocs/public/reference/agent-api.md`n")
    [IO.File]::WriteAllText($governanceChanges, "M`t.github/PULL_REQUEST_TEMPLATE.md`n")
    [IO.File]::WriteAllText($renameChanges, "R100`tcap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/OldContract.kt`tdocs/superpowers/OldContract.kt`n")

    $valid = New-ValidPrBody
    $markdownMaster = ([string][char]96) + 'master' + ([string][char]96)
    [IO.File]::WriteAllText($validBody, $valid)
    [IO.File]::WriteAllText($sameIssueBody, (New-ValidPrBody -Parent '#123' -Direct '#123' -Closing 'Closes #123'))
    [IO.File]::WriteAllText($missingDirectBody, (New-ValidPrBody -Parent '#123' -Direct 'N/A - no child linked' -Closing 'N/A - intermediate PR'))
    [IO.File]::WriteAllText($wrongClosingBody, (New-ValidPrBody -Parent '#123' -Direct '#124' -Closing 'Closes #125'))
    [IO.File]::WriteAllText($bareNaBody, (New-ValidPrBody -Parent 'N/A' -Direct 'N/A' -Closing 'N/A'))
    [IO.File]::WriteAllText($missingSurfaceBody, ($valid -replace '(?m)^\| Skill \|.*\r?\n', ''))
    [IO.File]::WriteAllText($placeholderEvidenceBody, ($valid -replace '\| Runtime \| verified-no-change \| Runtime contract and facts guard were checked\. \|', '| Runtime | verified-no-change | Explain the check. |'))
    [IO.File]::WriteAllText($wrongTargetBody, $valid.Replace("- [x] $markdownMaster", "- [ ] $markdownMaster"))
    [IO.File]::WriteAllText($wrongCaseTargetBody, $valid.Replace($markdownMaster, (([string][char]96) + 'Master' + ([string][char]96))))
    [IO.File]::WriteAllText($customTemplate, "## Summary`n`n-`n`n## Verification`n`n-`n")
    [IO.File]::WriteAllText($customBody, "## Summary`n`n- Custom validation.`n`n## Verification`n`n- Static test.`n")

    Invoke-ScriptProcess $validateScript @('-BodyFile', $validBody, '-Base', 'master', '-RequireChangeType') 0 'capability-governance evidence'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $sameIssueBody, '-Base', 'master', '-RequireChangeType') 1 'Parent and Direct issue must reference different issues'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $missingDirectBody, '-Base', 'master', '-RequireChangeType') 1 'must identify its direct Child issue'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $wrongClosingBody, '-Base', 'master', '-RequireChangeType') 1 'must close the Direct issue'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $bareNaBody, '-Base', 'master', '-RequireChangeType') 1 'N/A - <reason>'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $missingSurfaceBody, '-Base', 'master', '-RequireChangeType') 1 'Missing: Skill'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $placeholderEvidenceBody, '-Base', 'master', '-RequireChangeType') 1 'requires concrete evidence'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $wrongTargetBody, '-Base', 'master', '-RequireChangeType') 1 'check exactly base branch'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $wrongCaseTargetBody, '-Base', 'master', '-RequireChangeType') 1 'preserve tracked template option'
    Invoke-ScriptProcess $validateScript @('-Template', $customTemplate, '-BodyFile', $customBody) 0 'capability-governance evidence'
    $placeholderBody = Join-Path $tempRoot 'placeholder.md'
    $doubleAgentReviewBody = Join-Path $tempRoot 'double-agent-review.md'
    $emptyAgentReasonBody = Join-Path $tempRoot 'empty-agent-reason.md'
    $mixedVerificationBody = Join-Path $tempRoot 'mixed-verification.md'
    $emptyVerificationBody = Join-Path $tempRoot 'empty-verification.md'
    [IO.File]::WriteAllText($placeholderBody, $valid.Replace('Implements the child slice', 'TODO: Implements the child slice'))
    [IO.File]::WriteAllText($doubleAgentReviewBody, $valid.Replace('- [ ] Requested as non-blocking advisory review', '- [x] Requested as non-blocking advisory review'))
    [IO.File]::WriteAllText($emptyAgentReasonBody, $valid.Replace('Not requested because: deterministic validation is sufficient for this fixture.', 'Not requested because: TBD'))
    [IO.File]::WriteAllText($mixedVerificationBody, $valid.Replace('- [ ] Not run because:', '- [x] Not run because: CI unavailable'))
    [IO.File]::WriteAllText($emptyVerificationBody, ($valid -replace '(?m)^- \[x\] (Focused tests|Capability contract validation|Static validation):.*$', '- [ ] $1:'))
    Invoke-ScriptProcess $validateScript @('-BodyFile', $placeholderBody, '-Base', 'master', '-RequireChangeType') 1 'unresolved author placeholder'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $doubleAgentReviewBody, '-Base', 'master', '-RequireChangeType') 1 'Agent Review must check exactly one option'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $emptyAgentReasonBody, '-Base', 'master', '-RequireChangeType') 1 'unresolved author placeholder|concrete reason'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $mixedVerificationBody, '-Base', 'master', '-RequireChangeType') 1 'cannot select executed checks and Not run because together'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $emptyVerificationBody, '-Base', 'master', '-RequireChangeType') 1 'preserve tracked template option|Verification must check at least one executed item'

    $guidanceBody = Join-Path $tempRoot 'template-guidance.md'
    $arbitraryChangeTypeBody = Join-Path $tempRoot 'arbitrary-change-type.md'
    $arbitraryVerificationBody = Join-Path $tempRoot 'arbitrary-verification.md'
    $arbitraryAgentReviewBody = Join-Path $tempRoot 'arbitrary-agent-review.md'
    $issuePlaceholderBody = Join-Path $tempRoot 'issue-placeholder.md'
    [IO.File]::WriteAllText($guidanceBody, $valid.Replace('- Pipeline descriptors, public tasks, Agent section catalog, Runtime facts, and PR governance.', '- Name the descriptors, registries, canonical models, schemas, tasks, artifacts, or governance contracts touched. Use N/A only when no shared contract applies.'))
    [IO.File]::WriteAllText($arbitraryChangeTypeBody, $valid.Replace('Code, build, scripts, workflow, tests, fixtures, or templates', 'Arbitrary type'))
    [IO.File]::WriteAllText($arbitraryVerificationBody, $valid.Replace('Focused tests: capability contract and PR workflow tests', 'Looks good'))
    [IO.File]::WriteAllText($arbitraryAgentReviewBody, $valid.Replace('Requested as non-blocking advisory review', 'Arbitrary review choice'))
    [IO.File]::WriteAllText($issuePlaceholderBody, $valid.Replace('- Parent: #123', '- Parent: N/A - explain why this is a standalone change'))
    Invoke-ScriptProcess $validateScript @('-BodyFile', $guidanceBody, '-Base', 'master', '-RequireChangeType') 1 'unresolved author placeholder|concrete content'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $arbitraryChangeTypeBody, '-Base', 'master', '-RequireChangeType') 1 'preserve tracked template option'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $arbitraryVerificationBody, '-Base', 'master', '-RequireChangeType') 1 'preserve tracked template option'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $arbitraryAgentReviewBody, '-Base', 'master', '-RequireChangeType') 1 'preserve tracked template option'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $issuePlaceholderBody, '-Base', 'master', '-RequireChangeType') 1 'unresolved author placeholder'


    $diffArgs = @('-BodyFile', $validBody, '-Base', 'master', '-RequireChangeType', '-FactsFile', $factsFile, '-ChangedFilesFile', $generatorChanges)
    Invoke-ScriptProcess $validateScript $diffArgs 0 'capability-governance evidence'

    $generatorNotModified = Join-Path $tempRoot 'generator-not-modified.md'
    $generatorStatuses = @{ Runtime='verified-no-change'; Generator='verified-no-change'; Analyzer='verified-no-change'; AgentFacts='verified-no-change'; 'Public Docs'='verified-no-change'; Skill='verified-no-change' }
    [IO.File]::WriteAllText($generatorNotModified, (New-ValidPrBody -Statuses $generatorStatuses))
    Invoke-ScriptProcess $validateScript @('-BodyFile', $generatorNotModified, '-Base', 'master', '-RequireChangeType', '-FactsFile', $factsFile, '-ChangedFilesFile', $generatorChanges) 1 'Generator.*must be modified'

    $analyzerNa = Join-Path $tempRoot 'analyzer-na.md'
    $analyzerNaStatuses = @{ Runtime='verified-no-change'; Generator='modified'; Analyzer='not-applicable'; AgentFacts='verified-no-change'; 'Public Docs'='verified-no-change'; Skill='verified-no-change' }
    [IO.File]::WriteAllText($analyzerNa, (New-ValidPrBody -Statuses $analyzerNaStatuses))
    Invoke-ScriptProcess $validateScript @('-BodyFile', $analyzerNa, '-Base', 'master', '-RequireChangeType', '-FactsFile', $factsFile, '-ChangedFilesFile', $generatorChanges) 1 'Analyzer.*cannot be not-applicable'

    $wrongNodes = Join-Path $tempRoot 'wrong-nodes.md'
    [IO.File]::WriteAllText($wrongNodes, (New-ValidPrBody -ChangedNodes '`projection.skill`'))
    Invoke-ScriptProcess $validateScript @('-BodyFile', $wrongNodes, '-Base', 'master', '-RequireChangeType', '-FactsFile', $factsFile, '-ChangedFilesFile', $generatorChanges) 1 'Changed contract nodes do not match'

    $docsBody = Join-Path $tempRoot 'docs-only.md'
    $docsStatuses = @{ Runtime='verified-no-change'; Generator='verified-no-change'; Analyzer='verified-no-change'; AgentFacts='verified-no-change'; 'Public Docs'='modified'; Skill='verified-no-change' }
    [IO.File]::WriteAllText($docsBody, (New-ValidPrBody -Statuses $docsStatuses -ChangedNodes '`projection.public-docs`'))
    Invoke-ScriptProcess $validateScript @('-BodyFile', $docsBody, '-Base', 'master', '-RequireChangeType', '-FactsFile', $factsFile, '-ChangedFilesFile', $publicDocsChanges) 0 'capability-governance evidence'

    $governanceBody = Join-Path $tempRoot 'governance.md'
    $governanceStatuses = @{ Runtime='verified-no-change'; Generator='verified-no-change'; Analyzer='verified-no-change'; AgentFacts='verified-no-change'; 'Public Docs'='verified-no-change'; Skill='verified-no-change' }
    [IO.File]::WriteAllText($governanceBody, (New-ValidPrBody -Statuses $governanceStatuses -ChangedNodes 'N/A - governance files do not seed product capability surfaces'))
    Invoke-ScriptProcess $validateScript @('-BodyFile', $governanceBody, '-Base', 'master', '-RequireChangeType', '-FactsFile', $factsFile, '-ChangedFilesFile', $governanceChanges) 0 'capability-governance evidence'

    Invoke-ScriptProcess $validateScript @('-BodyFile', $validBody, '-Base', 'master', '-RequireChangeType', '-FactsFile', $factsFile) 1 'must be provided together'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $validBody, '-Base', 'master', '-RequireChangeType', '-ChangedFilesFile', $generatorChanges) 1 'must be provided together'
    Invoke-ScriptProcess $validateScript @('-BodyFile', $validBody, '-Base', 'master', '-RequireChangeType', '-FactsFile', $factsFile, '-ChangedFilesFile', $renameChanges) 0 'capability-governance evidence'

    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'fix/pr-template-guard', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 0 'DRY RUN.*PULL_REQUEST_TEMPLATE'
    Invoke-ScriptProcess $createScript @('-Base', 'release/candidate', '-Head', 'fix/pr-template-guard', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 1 'Unsupported base branch'
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'LDmoxeii:fix/pr-template-guard', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 1 'unqualified same-repository head branch'
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'master', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 1 'short-lived'
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'develop', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 1 'short-lived'
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'release/candidate', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 1 'short-lived'
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'verify/candidate', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 1 'short-lived'
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'fix/pr-template-guard', '-Title', 'test', '-BodyFile', $validBody, '-MachineReadable', '-AllowDirty') 1 'requires ExpectedHeadSha, FactsFile, and ChangedFilesFile'

    $providerRepo = Join-Path $tempRoot 'provider-repo'
    $providerRemote = Join-Path $tempRoot 'provider-remote.git'
    $fakeBin = Join-Path $tempRoot 'fake-bin'
    $fakeStateFile = Join-Path $tempRoot 'fake-gh-state.json'
    $fakeLogFile = Join-Path $tempRoot 'fake-gh.log'
    New-Item -ItemType Directory -Path $providerRepo, $fakeBin | Out-Null
    & git -C $providerRepo init -b master | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to initialize provider fixture repository.' }
    Invoke-TestGit $providerRepo @('config','user.name','Cap4k Test') | Out-Null
    Invoke-TestGit $providerRepo @('config','user.email','cap4k-test@example.invalid') | Out-Null
    Invoke-TestGit $providerRepo @('config','core.autocrlf','true') | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $providerRepo 'scripts'), (Join-Path $providerRepo '.github') | Out-Null
    Copy-Item -LiteralPath $finishProviderScript -Destination (Join-Path $providerRepo 'scripts/comet-finish-pr.ps1')
    Copy-Item -LiteralPath $createScript -Destination (Join-Path $providerRepo 'scripts/create-pr.ps1')
    Copy-Item -LiteralPath $validateScript -Destination (Join-Path $providerRepo 'scripts/validate-pr-body.ps1')
    Copy-Item -LiteralPath (Join-Path $repoRoot '.github/PULL_REQUEST_TEMPLATE.md') -Destination (Join-Path $providerRepo '.github/PULL_REQUEST_TEMPLATE.md')
    $exporterBase64 = 'cGFyYW0oW1BhcmFtZXRlcihNYW5kYXRvcnkgPSAkdHJ1ZSldW3N0cmluZ10gJE91dHB1dEZpbGUpCiRwYXlsb2FkID0gJ3sic2NoZW1hIjoiY2FwNGsuY2FwYWJpbGl0eS1jb250cmFjdC1mYWN0cy52MyIsInBhdGhNYXRjaFBvbGljeSI6ImZpcnN0X21hdGNoIiwicGF0aFJ1bGVzIjpbXSwic3VyZmFjZXMiOlt7Im5hbWUiOiJSdW50aW1lIiwibm9kZUlkIjoic3VyZmFjZS5ydW50aW1lIn0seyJuYW1lIjoiR2VuZXJhdG9yIiwibm9kZUlkIjoic3VyZmFjZS5nZW5lcmF0b3IifSx7Im5hbWUiOiJBbmFseXplciIsIm5vZGVJZCI6InN1cmZhY2UuYW5hbHl6ZXIifSx7Im5hbWUiOiJBZ2VudEZhY3RzIiwibm9kZUlkIjoicHJvamVjdGlvbi5hZ2VudC1mYWN0cyJ9LHsibmFtZSI6IlB1YmxpYyBEb2NzIiwibm9kZUlkIjoicHJvamVjdGlvbi5wdWJsaWMtZG9jcyJ9LHsibmFtZSI6IlNraWxsIiwibm9kZUlkIjoicHJvamVjdGlvbi5za2lsbCJ9XSwicHJvcGFnYXRpb25DbG9zdXJlIjp7fX0nCltJTy5GaWxlXTo6V3JpdGVBbGxUZXh0KCRPdXRwdXRGaWxlLCAkcGF5bG9hZCwgW1RleHQuVVRGOEVuY29kaW5nXTo6bmV3KCRmYWxzZSkpCldyaXRlLU91dHB1dCAkT3V0cHV0RmlsZQ=='
    [IO.File]::WriteAllBytes((Join-Path $providerRepo 'scripts/export-capability-contract-facts.ps1'), [Convert]::FromBase64String($exporterBase64))
    Write-Utf8File (Join-Path $providerRepo 'README.md') "provider fixture`n"
    Invoke-TestGit $providerRepo @('add','.') | Out-Null
    Invoke-TestGit $providerRepo @('commit','-m','fixture baseline') | Out-Null
    & git init --bare $providerRemote | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to initialize provider fixture remote.' }
    Invoke-TestGit $providerRepo @('remote','add','origin',$providerRemote) | Out-Null
    Invoke-TestGit $providerRepo @('push','-u','origin','master') | Out-Null
    Invoke-TestGit $providerRepo @('switch','-c','feature/provider-test') | Out-Null
    $preArchiveHead = (@(Invoke-TestGit $providerRepo @('rev-parse','HEAD'))[0]).Trim()
    $activeRoot = Join-Path $providerRepo 'docs/comet/changes/provider-test'
    $activeSpecDir = Join-Path $activeRoot 'specs/repository-pr-finish'
    New-Item -ItemType Directory -Path $activeSpecDir, (Join-Path $providerRepo '.comet') -Force | Out-Null
    Write-Utf8File (Join-Path $providerRepo 'implementation.txt') "accepted implementation`n"
    Write-Utf8File (Join-Path $providerRepo 'scripts/provider-change.ps1') "Write-Output 'accepted provider implementation'`n"
    Write-Utf8File (Join-Path $providerRepo 'scripts/test-provider-change.ps1') "Write-Output 'accepted provider test'`n"
    Write-Utf8File (Join-Path $providerRepo '.comet/config.yaml') "finish:`n  provider: repository-command`n"

    $archiveRoot = Join-Path $providerRepo 'docs/comet/archive/2026-08-16-provider-test'
    $archiveSpecDir = Join-Path $archiveRoot 'specs/repository-pr-finish'
    $briefText = "# Outcome`n`nProvider fixture.`n"
    $specText = "# Repository PR finish`n`nFixture specification.`n"
    $verificationText = @"
---
generated_from_state_version: 7
---

# Verification

- Result: **Passed**
- Iteration: 1
- Verifier attempt: 1
"@
    $stateText = @"
schema: comet.native.v4
name: provider-test
language: zh-CN
phase: archive
status: done
state_version: 7
brief: brief.md
spec_changes:
  - capability: repository-pr-finish
    operation: create
    source: specs/repository-pr-finish/spec.md
workspace:
  isolation: worktree
  change_branch: feature/provider-test
  target_branch: master
  finish: pull-request
loop:
  stage: done
  goal_cycle: 1
  iteration: 1
  attempt: 1
verification:
  candidate_id: candidate-provider-test
  identity_provider: skill-coordinated
  verifier_execution_ref: verifier-provider-test
  iteration: 1
  attempt: 1
  assurance: skill-coordinated
  verdict: pass
  completed_at: 2026-08-16T01:02:03.000Z
history: []
verification_result: pass
verification_report: verification.md
archived: true
created_at: 2026-08-16T00:00:00.000Z
"@
    $activeStateText = $stateText.Replace('phase: archive', 'phase: verify').Replace('status: done', 'status: active').Replace('state_version: 7', 'state_version: 6').Replace('archived: true', 'archived: false')
    $activeVerificationText = $verificationText.Replace('generated_from_state_version: 7', 'generated_from_state_version: 6')
    Write-Utf8File (Join-Path $activeRoot 'brief.md') $briefText
    Write-Utf8File (Join-Path $activeSpecDir 'spec.md') $specText
    Write-Utf8File (Join-Path $activeRoot 'verification.md') $activeVerificationText
    Write-Utf8File (Join-Path $activeRoot 'comet-state.yaml') $activeStateText
    $preArchiveTreeWithoutSelection = Get-WorkingTreeSha $providerRepo $tempRoot
    if ((@(Invoke-TestGit $providerRepo @('cat-file','-t',$preArchiveTreeWithoutSelection))[0]).Trim() -cne 'tree') { throw 'Fixture pre-Archive snapshot without current-change selection is not a Git tree.' }
    Write-Utf8File (Join-Path $providerRepo '.comet/current-change.json') '{"schema":"comet.selection.v2","workflow":"native","change":"provider-test","branch":null}'
    $preArchiveTree = Get-WorkingTreeSha $providerRepo $tempRoot
    if ((@(Invoke-TestGit $providerRepo @('cat-file','-t',$preArchiveTree))[0]).Trim() -cne 'tree') { throw 'Fixture pre-Archive snapshot is not a Git tree.' }
    Write-Utf8File (Join-Path $activeRoot 'brief.md') "# Outcome`n`nTampered accepted source inside the allowed active change root.`n"
    $allowedPathDriftTree = Get-WorkingTreeSha $providerRepo $tempRoot
    if ($allowedPathDriftTree -ceq $preArchiveTree) { throw 'Allowed-path drift fixture did not create a distinct valid Git tree.' }
    Write-Utf8File (Join-Path $activeRoot 'brief.md') $briefText

    Remove-Item -LiteralPath $activeRoot -Recurse -Force
    New-Item -ItemType Directory -Path $archiveSpecDir, (Join-Path $providerRepo 'docs/comet/specs/repository-pr-finish') -Force | Out-Null
    Write-Utf8File (Join-Path $archiveRoot 'brief.md') $briefText
    Write-Utf8File (Join-Path $archiveSpecDir 'spec.md') $specText
    Write-Utf8File (Join-Path $archiveRoot 'verification.md') $verificationText
    Write-Utf8File (Join-Path $archiveRoot 'comet-state.yaml') $stateText
    Write-Utf8File (Join-Path $providerRepo 'docs/comet/specs/repository-pr-finish/spec.md') $specText
    Remove-Item -LiteralPath (Join-Path $providerRepo '.comet/current-change.json') -Force
    Invoke-TestGit $providerRepo @('add','-A') | Out-Null
    Invoke-TestGit $providerRepo @('commit','-m','chore(native): archive provider-test') | Out-Null
    $providerHead = (@(Invoke-TestGit $providerRepo @('rev-parse','HEAD'))[0]).Trim()
    $providerFinalTree = (@(Invoke-TestGit $providerRepo @('rev-parse',"$providerHead^{tree}"))[0]).Trim()
    $checkoutTextPaths = @(
        '.github/PULL_REQUEST_TEMPLATE.md',
        'docs/comet/archive/2026-08-16-provider-test/brief.md',
        'docs/comet/archive/2026-08-16-provider-test/specs/repository-pr-finish/spec.md'
    )
    foreach ($relativePath in $checkoutTextPaths) { Remove-Item -LiteralPath (Join-Path $providerRepo $relativePath) -Force }
    Invoke-TestGit $providerRepo (@('checkout','--') + $checkoutTextPaths) | Out-Null
    if (@(Invoke-TestGit $providerRepo @('status','--porcelain')).Count -ne 0) { throw 'Autocrlf checkout fixture must remain Git-clean.' }
    foreach ($relativePath in $checkoutTextPaths) {
        $workingOid = (@(Invoke-TestGit $providerRepo @('hash-object','--no-filters',$relativePath))[0]).Trim()
        $blobOid = (@(Invoke-TestGit $providerRepo @('rev-parse',"HEAD:$relativePath"))[0]).Trim()
        if ($workingOid -ceq $blobOid) { throw "Autocrlf fixture did not create a working-tree/blob byte difference for $relativePath." }
    }

    $factsOutput = Join-Path $tempRoot 'provider-facts.json'
    & (Join-Path $providerRepo 'scripts/export-capability-contract-facts.ps1') -OutputFile $factsOutput | Out-Null
    $noCapabilityStatuses = @{ Runtime='verified-no-change'; Generator='verified-no-change'; Analyzer='verified-no-change'; AgentFacts='verified-no-change'; 'Public Docs'='verified-no-change'; Skill='verified-no-change' }
    $providerBody = New-ValidPrBody -Statuses $noCapabilityStatuses -ChangedNodes 'N/A - fixture paths do not seed product capability surfaces'
    $providerTitle = 'Enable repository-owned Native PR finish provider'
    $artifact = [ordered]@{
        schema = 'cap4k.native-pr-authoring.v1'
        change = 'provider-test'
        baseBranch = 'master'
        headBranch = 'feature/provider-test'
        title = $providerTitle
        body = $providerBody
        source = [ordered]@{
            stateVersion = 6
            verificationResult = 'pass'
            verification = [ordered]@{ path='verification.md'; candidateId='candidate-provider-test'; verifierExecutionRef='verifier-provider-test'; iteration=1; attempt=1 }
            brief = [ordered]@{ path='brief.md'; sha256=(Get-CanonicalTextSha256 (Join-Path $archiveRoot 'brief.md')) }
            specs = @([ordered]@{ path='specs/repository-pr-finish/spec.md'; sha256=(Get-CanonicalTextSha256 (Join-Path $archiveSpecDir 'spec.md')) })
            template = [ordered]@{ path='.github/PULL_REQUEST_TEMPLATE.md'; sha256=(Get-CanonicalTextSha256 (Join-Path $providerRepo '.github/PULL_REQUEST_TEMPLATE.md')) }
            preArchiveHeadSha = $preArchiveHead
            preArchiveTreeSha = $preArchiveTree
            facts = [ordered]@{ sha256=(Get-Sha256Hex $factsOutput) }
        }
        contentFingerprint = [ordered]@{ algorithm='sha256'; digest='' }
    }
    $artifact.contentFingerprint.digest = Get-AuthoringFingerprint $artifact
    $artifactWithoutSelection = $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
    $artifactWithoutSelection.source.preArchiveTreeSha = $preArchiveTreeWithoutSelection
    $artifactWithoutSelection.contentFingerprint.digest = Get-AuthoringFingerprint $artifactWithoutSelection
    if ($artifactWithoutSelection.contentFingerprint.digest -ceq $artifact.contentFingerprint.digest) { throw 'Selection-absent fixture did not refresh the canonical fingerprint.' }
    $artifactPathText = (@(Invoke-TestGit $providerRepo @('rev-parse','--git-path','comet/pr-authoring/provider-test.json'))[0]).Trim()
    $artifactPath = if ([IO.Path]::IsPathRooted($artifactPathText)) { $artifactPathText } else { Join-Path $providerRepo $artifactPathText }
    function Write-ProviderArtifact([object] $Value) { Write-Utf8File $artifactPath ($Value | ConvertTo-Json -Depth 20) }
    Write-ProviderArtifact $artifact

    $fakeGhBase64 = 'cGFyYW0oW1BhcmFtZXRlcihWYWx1ZUZyb21SZW1haW5pbmdBcmd1bWVudHMgPSAkdHJ1ZSldW3N0cmluZ1tdXSAkR2hBcmdzKQokRXJyb3JBY3Rpb25QcmVmZXJlbmNlID0gJ1N0b3AnCiRzdGF0ZSA9IEdldC1Db250ZW50IC1MaXRlcmFsUGF0aCAkZW52OkNBUDRLX0ZBS0VfR0hfU1RBVEUgLVJhdyAtRW5jb2RpbmcgVVRGOCB8IENvbnZlcnRGcm9tLUpzb24KW0lPLkZpbGVdOjpBcHBlbmRBbGxUZXh0KCRlbnY6Q0FQNEtfRkFLRV9HSF9MT0csICgoJEdoQXJncyAtam9pbiAiYHQiKSArICJgbiIpLCBbVGV4dC5VVEY4RW5jb2RpbmddOjpuZXcoJGZhbHNlKSkKJHBycyA9IEAoJHN0YXRlLnB1bGxSZXF1ZXN0cykKaWYgKCRHaEFyZ3MuQ291bnQgLWdlIDIgLWFuZCAkR2hBcmdzWzBdIC1lcSAncHInIC1hbmQgJEdoQXJnc1sxXSAtZXEgJ2xpc3QnKSB7CiAgICBbQ29uc29sZV06Ok91dC5Xcml0ZUxpbmUoKENvbnZlcnRUby1Kc29uIC1JbnB1dE9iamVjdCBAKCRwcnMpIC1EZXB0aCAxMCAtQ29tcHJlc3MpKTsgZXhpdCAwCn0KaWYgKCRHaEFyZ3MuQ291bnQgLWdlIDIgLWFuZCAkR2hBcmdzWzBdIC1lcSAncHInIC1hbmQgJEdoQXJnc1sxXSAtZXEgJ2NyZWF0ZScpIHsKICAgIGZ1bmN0aW9uIFZhbHVlQWZ0ZXIoW3N0cmluZ10gJE5hbWUpIHsgJGluZGV4ID0gW0FycmF5XTo6SW5kZXhPZigkR2hBcmdzLCAkTmFtZSk7IGlmICgkaW5kZXggLWx0IDAgLW9yICRpbmRleCArIDEgLWdlICRHaEFyZ3MuQ291bnQpIHsgdGhyb3cgIk1pc3NpbmcgJE5hbWUiIH07IHJldHVybiAkR2hBcmdzWyRpbmRleCArIDFdIH0KICAgICRudW1iZXIgPSAxMDEKICAgICR1cmwgPSAiaHR0cHM6Ly9naXRodWIuY29tL2V4YW1wbGUvY2FwNGsvcHVsbC8kbnVtYmVyIgogICAgJHByID0gW3BzY3VzdG9tb2JqZWN0XUB7IG51bWJlcj0kbnVtYmVyOyB1cmw9JHVybDsgc3RhdGU9J09QRU4nOyBiYXNlUmVmTmFtZT0oVmFsdWVBZnRlciAnLS1iYXNlJyk7IGhlYWRSZWZOYW1lPShWYWx1ZUFmdGVyICctLWhlYWQnKTsgaGVhZFJlZk9pZD0kZW52OkNBUDRLX0ZBS0VfSEVBRF9TSEE7IHRpdGxlPShWYWx1ZUFmdGVyICctLXRpdGxlJyk7IGJvZHk9W0lPLkZpbGVdOjpSZWFkQWxsVGV4dCgoVmFsdWVBZnRlciAnLS1ib2R5LWZpbGUnKSkgfQogICAgJHBycyA9IEAoJHBycykgKyAkcHIKICAgICRzdGF0ZS5wdWxsUmVxdWVzdHMgPSBAKCRwcnMpCiAgICAkc3RhdGUuY3JlYXRlQ291bnQgPSBbaW50XSRzdGF0ZS5jcmVhdGVDb3VudCArIDEKICAgIFtJTy5GaWxlXTo6V3JpdGVBbGxUZXh0KCRlbnY6Q0FQNEtfRkFLRV9HSF9TVEFURSwgKCRzdGF0ZSB8IENvbnZlcnRUby1Kc29uIC1EZXB0aCAxMCksIFtUZXh0LlVURjhFbmNvZGluZ106Om5ldygkZmFsc2UpKQogICAgW0NvbnNvbGVdOjpPdXQuV3JpdGVMaW5lKCR1cmwpOyBleGl0IDAKfQppZiAoJEdoQXJncy5Db3VudCAtZ2UgMyAtYW5kICRHaEFyZ3NbMF0gLWVxICdwcicgLWFuZCAkR2hBcmdzWzFdIC1lcSAndmlldycpIHsKICAgICRyZWYgPSBbc3RyaW5nXSRHaEFyZ3NbMl0KICAgICRtYXRjaCA9IEAoJHBycyB8IFdoZXJlLU9iamVjdCB7IFtzdHJpbmddJF8ubnVtYmVyIC1jZXEgJHJlZiAtb3IgW3N0cmluZ10kXy51cmwgLWNlcSAkcmVmIH0pCiAgICBpZiAoJG1hdGNoLkNvdW50IC1uZSAxKSB7IFtDb25zb2xlXTo6RXJyb3IuV3JpdGVMaW5lKCdub3QgZm91bmQnKTsgZXhpdCAxIH0KICAgIFtDb25zb2xlXTo6T3V0LldyaXRlTGluZSgoJG1hdGNoWzBdIHwgQ29udmVydFRvLUpzb24gLURlcHRoIDEwIC1Db21wcmVzcykpOyBleGl0IDAKfQpbQ29uc29sZV06OkVycm9yLldyaXRlTGluZSgidW5zdXBwb3J0ZWQgZmFrZSBnaCBpbnZvY2F0aW9uOiAkKCRHaEFyZ3MgLWpvaW4gJyAnKSIpOyBleGl0IDE='
    [IO.File]::WriteAllBytes((Join-Path $fakeBin 'fake-gh.ps1'), [Convert]::FromBase64String($fakeGhBase64))
    if ($IsWindows) {
        Write-Utf8File (Join-Path $fakeBin 'gh.cmd') "@echo off`r`npwsh -NoProfile -ExecutionPolicy Bypass -File `"%~dp0fake-gh.ps1`" %*`r`n"
    } else {
        $fakeGhCommand = Join-Path $fakeBin 'gh'
        $fakeGhWrapper = (@('#!/usr/bin/env pwsh', '& "$PSScriptRoot/fake-gh.ps1" @args', 'exit $LASTEXITCODE') -join "`n") + "`n"
        Write-Utf8File $fakeGhCommand $fakeGhWrapper
        & chmod +x -- $fakeGhCommand
        if ($LASTEXITCODE -ne 0) { throw 'Failed to make the Unix fake gh wrapper executable.' }
    }
    Write-Utf8File $fakeStateFile '{"pullRequests":[],"createCount":0}'
    Write-Utf8File $fakeLogFile ''
    $oldPath = $env:PATH
    $oldFakeState = $env:CAP4K_FAKE_GH_STATE
    $oldFakeLog = $env:CAP4K_FAKE_GH_LOG
    $oldFakeHead = $env:CAP4K_FAKE_HEAD_SHA
    $env:PATH = "$fakeBin$([IO.Path]::PathSeparator)$oldPath"
    $resolvedGhPath = [IO.Path]::GetFullPath((Get-Command gh -CommandType Application -ErrorAction Stop).Source)
    $fakeBinPrefix = [IO.Path]::GetFullPath($fakeBin) + [IO.Path]::DirectorySeparatorChar
    $pathComparison = if ($IsWindows) { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    if (-not $resolvedGhPath.StartsWith($fakeBinPrefix, $pathComparison)) { throw "Provider fixture resolved gh outside fake-bin: $resolvedGhPath" }
    $env:CAP4K_FAKE_GH_STATE = $fakeStateFile
    $env:CAP4K_FAKE_GH_LOG = $fakeLogFile
    $env:CAP4K_FAKE_HEAD_SHA = $providerHead
    try {
        $providerInput = [ordered]@{ schema='comet.native.pull-request-finish-input.v1'; projectRoot=$providerRepo; change=[ordered]@{ name='provider-test'; branch='feature/provider-test'; headSha=$providerHead }; target=[ordered]@{ branch='master' }; remote='origin'; transactionId='transaction-provider-test'; existingPullRequest=$null }
        $createdProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 0 'pull-request-finish-result' $providerRepo
        $createdResult = Assert-ProviderSuccess $createdProcess 'created'
        if ($createdResult.pullRequest.headSha -cne $providerHead) { throw 'Provider result did not preserve the exact Archive head SHA.' }
        $createdState = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([int]$createdState.createCount -ne 1 -or @($createdState.pullRequests).Count -ne 1) { throw 'Provider create path did not create exactly one PR.' }
        $remotePr = @($createdState.pullRequests)[0]
        $providerInput.existingPullRequest = [ordered]@{ number=[long]$remotePr.number; url=[string]$remotePr.url; baseBranch=[string]$remotePr.baseRefName; headBranch=[string]$remotePr.headRefName; headSha=[string]$remotePr.headRefOid; state='OPEN' }
        $reusedProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 0 'pull-request-finish-result' $providerRepo
        [void](Assert-ProviderSuccess $reusedProcess 'reused')
        $retryState = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([int]$retryState.createCount -ne 1) { throw 'Provider retry created a duplicate PR.' }

        Write-ProviderArtifact $artifactWithoutSelection
        $withoutSelectionProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 0 'pull-request-finish-result' $providerRepo
        [void](Assert-ProviderSuccess $withoutSelectionProcess 'reused')
        $withoutSelectionState = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([int]$withoutSelectionState.createCount -ne 1) { throw 'Selection-absent Archive retry created a duplicate PR.' }
        Write-ProviderArtifact $artifact

        $driftState = $withoutSelectionState
        @($driftState.pullRequests)[0].title = 'drifted title'
        Write-Utf8File $fakeStateFile ($driftState | ConvertTo-Json -Depth 10)
        $driftProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'title' $providerRepo
        Assert-ProviderFailure $driftProcess
        @($driftState.pullRequests)[0].title = $providerTitle
        Write-Utf8File $fakeStateFile ($driftState | ConvertTo-Json -Depth 10)

        @($driftState.pullRequests)[0].body = "$providerBody`nremote drift"
        Write-Utf8File $fakeStateFile ($driftState | ConvertTo-Json -Depth 10)
        $bodyDriftProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'body' $providerRepo
        Assert-ProviderFailure $bodyDriftProcess
        @($driftState.pullRequests)[0].body = $providerBody
        Write-Utf8File $fakeStateFile ($driftState | ConvertTo-Json -Depth 10)

        $mismatchInput = $providerInput | ConvertTo-Json -Depth 10 | ConvertFrom-Json
        $mismatchInput.existingPullRequest.number = 999
        $mismatchInput.existingPullRequest.url = 'https://github.com/example/cap4k/pull/999'
        $mismatchProcess = Invoke-ProcessWithInput $finishProviderScript ($mismatchInput | ConvertTo-Json -Depth 10) 1 'conflicts with the recovery identity' $providerRepo
        Assert-ProviderFailure $mismatchProcess

        $multipleState = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $duplicatePr = ($multipleState.pullRequests[0] | ConvertTo-Json -Depth 10 | ConvertFrom-Json)
        $duplicatePr.number = 102; $duplicatePr.url = 'https://github.com/example/cap4k/pull/102'
        $multipleState.pullRequests = @($multipleState.pullRequests[0], $duplicatePr)
        Write-Utf8File $fakeStateFile ($multipleState | ConvertTo-Json -Depth 10)
        $noExistingInput = $providerInput | ConvertTo-Json -Depth 10 | ConvertFrom-Json
        $noExistingInput.existingPullRequest = $null
        $multipleProcess = Invoke-ProcessWithInput $finishProviderScript ($noExistingInput | ConvertTo-Json -Depth 10) 1 'More than one open pull request' $providerRepo
        Assert-ProviderFailure $multipleProcess
        $multipleState.pullRequests = @($multipleState.pullRequests[0])
        Write-Utf8File $fakeStateFile ($multipleState | ConvertTo-Json -Depth 10)

        Remove-Item -LiteralPath $artifactPath -Force
        $missingArtifactProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'Authoring artifact is missing' $providerRepo
        Assert-ProviderFailure $missingArtifactProcess
        Write-ProviderArtifact $artifact

        $staleArtifact = $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $staleArtifact.source.stateVersion = 5
        Write-ProviderArtifact $staleArtifact
        $staleProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'direct successor' $providerRepo
        Assert-ProviderFailure $staleProcess
        $fingerprintArtifact = $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $fingerprintArtifact.contentFingerprint.digest = ('0' * 64)
        Write-ProviderArtifact $fingerprintArtifact
        $fingerprintProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'fingerprint' $providerRepo
        Assert-ProviderFailure $fingerprintProcess
        $sourceArtifact = $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $sourceArtifact.source.brief.path = '../brief.md'
        Write-ProviderArtifact $sourceArtifact
        $sourceProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'accepted change source' $providerRepo
        Assert-ProviderFailure $sourceProcess
        Write-ProviderArtifact $artifact

        $invalidTreeArtifact = $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $invalidTreeArtifact.source.preArchiveTreeSha = ('z' * 40)
        Write-ProviderArtifact $invalidTreeArtifact
        $invalidTreeProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'preArchiveTreeSha is not a Git OID' $providerRepo
        Assert-ProviderFailure $invalidTreeProcess
        $nonTreeArtifact = $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $nonTreeArtifact.source.preArchiveTreeSha = $preArchiveHead
        Write-ProviderArtifact $nonTreeArtifact
        $nonTreeProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'existing Git tree' $providerRepo
        Assert-ProviderFailure $nonTreeProcess
        Write-ProviderArtifact $artifact

        $remoteStateBeforeValidTreeTamper = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $remoteLogBeforeValidTreeTamper = @((Get-Content -LiteralPath $fakeLogFile -Encoding UTF8) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
        $validTreeTamperArtifact = $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $validTreeTamperArtifact.source.preArchiveTreeSha = $providerFinalTree
        $validTreeTamperArtifact.contentFingerprint.digest = Get-AuthoringFingerprint $validTreeTamperArtifact
        if ($validTreeTamperArtifact.contentFingerprint.digest -ceq $artifact.contentFingerprint.digest) { throw 'Valid-tree tamper fixture did not refresh the canonical fingerprint.' }
        Write-ProviderArtifact $validTreeTamperArtifact
        $validTreeTamperProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'snapshot|accepted|Archive progression' $providerRepo
        Assert-ProviderFailure $validTreeTamperProcess
        $remoteStateAfterValidTreeTamper = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $remoteLogAfterValidTreeTamper = @((Get-Content -LiteralPath $fakeLogFile -Encoding UTF8) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
        if ([int]$remoteStateAfterValidTreeTamper.createCount -ne [int]$remoteStateBeforeValidTreeTamper.createCount -or @($remoteStateAfterValidTreeTamper.pullRequests).Count -ne @($remoteStateBeforeValidTreeTamper.pullRequests).Count -or $remoteLogAfterValidTreeTamper -ne $remoteLogBeforeValidTreeTamper) { throw 'Valid-tree tamper reached the fake remote create/reuse path.' }

        $remoteStateBeforeAllowedPathDrift = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $remoteLogBeforeAllowedPathDrift = @((Get-Content -LiteralPath $fakeLogFile -Encoding UTF8) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
        $allowedPathDriftArtifact = $artifact | ConvertTo-Json -Depth 20 | ConvertFrom-Json
        $allowedPathDriftArtifact.source.preArchiveTreeSha = $allowedPathDriftTree
        $allowedPathDriftArtifact.contentFingerprint.digest = Get-AuthoringFingerprint $allowedPathDriftArtifact
        if ($allowedPathDriftArtifact.contentFingerprint.digest -ceq $artifact.contentFingerprint.digest) { throw 'Allowed-path drift fixture did not refresh the canonical fingerprint.' }
        Write-ProviderArtifact $allowedPathDriftArtifact
        $allowedPathDriftProcess = Invoke-ProcessWithInput $finishProviderScript ($providerInput | ConvertTo-Json -Depth 10) 1 'snapshot|accepted|Archive progression' $providerRepo
        Assert-ProviderFailure $allowedPathDriftProcess
        $remoteStateAfterAllowedPathDrift = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $remoteLogAfterAllowedPathDrift = @((Get-Content -LiteralPath $fakeLogFile -Encoding UTF8) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
        if ([int]$remoteStateAfterAllowedPathDrift.createCount -ne [int]$remoteStateBeforeAllowedPathDrift.createCount -or @($remoteStateAfterAllowedPathDrift.pullRequests).Count -ne @($remoteStateBeforeAllowedPathDrift.pullRequests).Count -or $remoteLogAfterAllowedPathDrift -ne $remoteLogBeforeAllowedPathDrift) { throw 'Allowed-path internal drift reached the fake remote create/reuse path.' }
        Write-ProviderArtifact $artifact

        $invalidSchemaInput = $providerInput | ConvertTo-Json -Depth 10 | ConvertFrom-Json
        $invalidSchemaInput.schema = 'wrong.schema'
        $schemaProcess = Invoke-ProcessWithInput $finishProviderScript ($invalidSchemaInput | ConvertTo-Json -Depth 10) 1 'Unsupported input schema' $providerRepo
        Assert-ProviderFailure $schemaProcess
        $extraInput = $providerInput | ConvertTo-Json -Depth 10 | ConvertFrom-Json
        $extraInput | Add-Member -NotePropertyName extra -NotePropertyValue 'forbidden'
        $extraProcess = Invoke-ProcessWithInput $finishProviderScript ($extraInput | ConvertTo-Json -Depth 10) 1 'unsupported property' $providerRepo
        Assert-ProviderFailure $extraProcess
        $oversizedProcess = Invoke-ProcessWithInput $finishProviderScript ('x' * 1048577) 1 'exceeds 1048576 bytes' $providerRepo
        Assert-ProviderFailure $oversizedProcess

        Write-ProviderArtifact $artifactWithoutSelection
        Write-Utf8File (Join-Path $providerRepo '.comet/current-change.json') '{"schema":"comet.selection.v2","workflow":"native","change":"provider-test","branch":null}'
        Invoke-TestGit $providerRepo @('add','.comet/current-change.json') | Out-Null
        Invoke-TestGit $providerRepo @('commit','-m','introduce current-change after accepted snapshot') | Out-Null
        $introducedSelectionHead = (@(Invoke-TestGit $providerRepo @('rev-parse','HEAD'))[0]).Trim()
        $introducedSelectionInput = $providerInput | ConvertTo-Json -Depth 10 | ConvertFrom-Json
        $introducedSelectionInput.change.headSha = $introducedSelectionHead
        $introducedSelectionInput.existingPullRequest = $null
        $env:CAP4K_FAKE_HEAD_SHA = $introducedSelectionHead
        $introducedSelectionProcess = Invoke-ProcessWithInput $finishProviderScript ($introducedSelectionInput | ConvertTo-Json -Depth 10) 1 'introduced .comet/current-change.json' $providerRepo
        Assert-ProviderFailure $introducedSelectionProcess
        Remove-Item -LiteralPath (Join-Path $providerRepo '.comet/current-change.json') -Force
        Invoke-TestGit $providerRepo @('add','-A') | Out-Null
        Invoke-TestGit $providerRepo @('commit','-m','restore absent current-change selection') | Out-Null
        Write-ProviderArtifact $artifact

        Write-Utf8File (Join-Path $providerRepo 'post-snapshot-drift.txt') "unsupported post-snapshot drift`n"
        Invoke-TestGit $providerRepo @('add','post-snapshot-drift.txt') | Out-Null
        Invoke-TestGit $providerRepo @('commit','-m','introduce unsupported post-snapshot drift') | Out-Null
        $driftHead = (@(Invoke-TestGit $providerRepo @('rev-parse','HEAD'))[0]).Trim()
        $driftInput = $providerInput | ConvertTo-Json -Depth 10 | ConvertFrom-Json
        $driftInput.change.headSha = $driftHead
        $driftInput.existingPullRequest = $null
        $env:CAP4K_FAKE_HEAD_SHA = $driftHead
        $snapshotDriftProcess = Invoke-ProcessWithInput $finishProviderScript ($driftInput | ConvertTo-Json -Depth 10) 1 'changed-path set does not exactly match Runtime-owned Archive progression' $providerRepo
        Assert-ProviderFailure $snapshotDriftProcess

        $finalFakeState = Get-Content -LiteralPath $fakeStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ([int]$finalFakeState.createCount -ne 1) { throw 'A failure or retry path performed an unexpected remote create.' }
    } finally {
        $env:PATH = $oldPath
        $env:CAP4K_FAKE_GH_STATE = $oldFakeState
        $env:CAP4K_FAKE_GH_LOG = $oldFakeLog
        $env:CAP4K_FAKE_HEAD_SHA = $oldFakeHead
    }

    & git -C $repoRoot worktree add --detach $detachedWorktree HEAD | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create detached worktree for PR workflow test.' }
    Copy-Item -LiteralPath (Join-Path $repoRoot '.github/PULL_REQUEST_TEMPLATE.md') -Destination (Join-Path $detachedWorktree '.github/PULL_REQUEST_TEMPLATE.md') -Force
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'fix/pr-template-guard', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 0 'DRY RUN.*PULL_REQUEST_TEMPLATE' $detachedWorktree
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'fix/pr-template-guard', '-Title', 'test', '-BodyFile', $validBody, '-Template', $customTemplate, '-DryRun', '-AllowDirty') 1 'Template override must reference a tracked PR template'
} finally {
    if (Test-Path -LiteralPath $detachedWorktree) { & git -C $repoRoot worktree remove --force $detachedWorktree | Out-Null }
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
}

Write-Output 'OK: PR workflow script tests passed.'
