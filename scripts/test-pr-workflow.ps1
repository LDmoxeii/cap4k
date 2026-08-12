[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$pwsh = (Get-Command pwsh -ErrorAction Stop).Source
$validateScript = Join-Path $PSScriptRoot "validate-pr-body.ps1"
$createScript = Join-Path $PSScriptRoot "create-pr.ps1"
$exportFactsScript = Join-Path $PSScriptRoot 'export-capability-contract-facts.ps1'
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

## Docs-Only Skip Reason

- N/A - not documentation-only

## Related Spec Or Plan

- ``docs/comet/changes/public-surface-contract/specs/capability-contract-governance/spec.md``

## Agent Review

- [ ] Requested as non-blocking advisory review
- [x] Not requested because: deterministic validation is sufficient for this fixture.

## Release Note

- Capability contract governance and hierarchy checks.
"@
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("cap4k-pr-workflow-test-" + [System.Guid]::NewGuid())
New-Item -ItemType Directory -Path $tempRoot | Out-Null
$detachedWorktree = Join-Path $tempRoot "detached-worktree"
try {
    $workflowText = Get-Content -LiteralPath $ciWorkflow -Raw -Encoding UTF8
    foreach ($required in @('validate-pr-body.ps1', 'export-capability-contract-facts.ps1', 'validate-capability-contract.ps1', 'validate-cap4k-skills.ps1', 'ChangedFilesFile', 'git diff --name-status --find-renames')) {
        if (-not $workflowText.Contains($required)) { throw "CI workflow must invoke or declare $required." }
    }
    if (-not $workflowText.Contains('if [[ "$BASE_REF" != "master" ]]; then')) { throw 'CI must reject pull requests whose base is not master.' }

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
    Invoke-ScriptProcess $validateScript @('-BodyFile', $wrongCaseTargetBody, '-Base', 'master', '-RequireChangeType') 1 'does not list base branch'
    Invoke-ScriptProcess $validateScript @('-Template', $customTemplate, '-BodyFile', $customBody) 0 'capability-governance evidence'

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
    Invoke-ScriptProcess $createScript @('-Base', 'master', '-Head', 'master', '-Title', 'test', '-BodyFile', $validBody, '-DryRun', '-AllowDirty') 1 'short-lived head branch'

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
