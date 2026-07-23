[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$pwsh = (Get-Command pwsh -ErrorAction Stop).Source
$validateScript = Join-Path $PSScriptRoot "validate-pr-body.ps1"
$createScript = Join-Path $PSScriptRoot "create-pr.ps1"

function Invoke-ScriptProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Script,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [int] $ExpectedExitCode,

        [string] $ExpectedOutputPattern,

        [string] $WorkingDirectory
    )

    if ($WorkingDirectory) {
        Push-Location -LiteralPath $WorkingDirectory
    }
    try {
        $output = & $pwsh -NoProfile -ExecutionPolicy Bypass -File $Script @Arguments 2>&1
        $actualExitCode = $LASTEXITCODE
    }
    finally {
        if ($WorkingDirectory) {
            Pop-Location
        }
    }

    $text = ($output | Out-String).Trim()

    if ($actualExitCode -ne $ExpectedExitCode) {
        throw "Expected exit code $ExpectedExitCode from $Script but got $actualExitCode.`n$text"
    }

    if ($ExpectedOutputPattern -and $text -notmatch $ExpectedOutputPattern) {
        throw "Expected output from $Script to match '$ExpectedOutputPattern'.`n$text"
    }

    return $text
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("cap4k-pr-workflow-test-" + [System.Guid]::NewGuid())
New-Item -ItemType Directory -Path $tempRoot | Out-Null

try {
    $template = Join-Path $tempRoot "template.md"
    $validBody = Join-Path $tempRoot "valid-body.md"
    $missingBody = Join-Path $tempRoot "missing-body.md"
    $dryRunBody = Join-Path $tempRoot "dry-run-body.md"
    $wrongTargetBody = Join-Path $tempRoot "wrong-target-body.md"
    $customTemplate = Join-Path $tempRoot "custom-template.md"
    $wrongCaseTargetBody = Join-Path $tempRoot "wrong-case-target-body.md"
    $detachedWorktree = Join-Path $tempRoot "detached-worktree"

    @"
## Summary

-

## Verification

-

## Release Note

-
"@ | Set-Content -Path $template -Encoding UTF8

    @"
## Summary

-
"@ | Set-Content -Path $customTemplate -Encoding UTF8

    @"
## Summary

- Adds a workflow guard.

## Verification

- Static script test.

## Release Note

- N/A
"@ | Set-Content -Path $validBody -Encoding UTF8

    @"
## Summary

- Adds a workflow guard.

## Verification

- Static script test.
"@ | Set-Content -Path $missingBody -Encoding UTF8

    @"
## Summary

- Adds a workflow guard.

## Target Branch

- [x] ``master``
- [ ] ``publish/aliyun-private``
- [ ] ``publish/maven-central``

## Change Type

- [ ] Code, build, scripts, workflow, tests, fixtures, or templates
- [ ] Documentation-only
- [ ] Release promotion from ``master``
- [x] Repository governance or GitHub configuration

## Verification

- [ ] Full Gradle check: ``./gradlew check``
- [x] Focused tests:
  - ``./scripts/test-pr-workflow.ps1``
- [x] Static validation:
  - ``git diff --check``
- [ ] Not run because:

## Docs-Only Skip Reason

If this is documentation-only, list the changed doc/template paths that should
allow CI to skip Gradle:

- N/A

## Related Issue, Spec, Or Plan

- N/A

## Release Note

- N/A
"@ | Set-Content -Path $dryRunBody -Encoding UTF8

    @"
## Summary

- Adds a workflow guard.

## Target Branch

- [ ] ``master``
- [x] ``publish/aliyun-private``
- [ ] ``publish/maven-central``

## Change Type

- [x] Repository governance or GitHub configuration

## Verification

- [x] Static validation:
  - ``./scripts/test-pr-workflow.ps1``

## Docs-Only Skip Reason

- N/A

## Related Issue, Spec, Or Plan

- N/A

## Release Note

- N/A
"@ | Set-Content -Path $wrongTargetBody -Encoding UTF8

    @"
## Summary

- Adds a workflow guard.

## Target Branch

- [x] ``Master``
- [ ] ``publish/aliyun-private``
- [ ] ``publish/maven-central``

## Change Type

- [x] Repository governance or GitHub configuration

## Verification

- [x] Static validation:
  - ``./scripts/test-pr-workflow.ps1``

## Docs-Only Skip Reason

- N/A

## Related Issue, Spec, Or Plan

- N/A

## Release Note

- N/A
"@ | Set-Content -Path $wrongCaseTargetBody -Encoding UTF8

    Invoke-ScriptProcess `
        -Script $validateScript `
        -Arguments @("-Template", $template, "-BodyFile", $validBody) `
        -ExpectedExitCode 0 `
        -ExpectedOutputPattern "OK: PR body includes all required template headings" | Out-Null

    Invoke-ScriptProcess `
        -Script $validateScript `
        -Arguments @("-Template", $template, "-BodyFile", $missingBody) `
        -ExpectedExitCode 1 `
        -ExpectedOutputPattern "Missing required template heading: ## Release Note" | Out-Null

    Invoke-ScriptProcess `
        -Script $createScript `
        -Arguments @("-Base", "master", "-Head", "fix/pr-template-guard", "-Title", "test", "-BodyFile", $dryRunBody, "-DryRun", "-AllowDirty") `
        -ExpectedExitCode 0 `
        -ExpectedOutputPattern "DRY RUN.*\.github/PULL_REQUEST_TEMPLATE\.md" | Out-Null

    & git -C $repoRoot worktree add --detach $detachedWorktree HEAD | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create detached worktree for PR workflow test."
    }

    Invoke-ScriptProcess `
        -Script $createScript `
        -Arguments @("-Base", "master", "-Head", "fix/pr-template-guard", "-Title", "test", "-BodyFile", $dryRunBody, "-DryRun") `
        -ExpectedExitCode 0 `
        -ExpectedOutputPattern "DRY RUN.*\.github/PULL_REQUEST_TEMPLATE\.md" `
        -WorkingDirectory $detachedWorktree | Out-Null

    Invoke-ScriptProcess `
        -Script $createScript `
        -Arguments @("-Base", "master", "-Head", "fix/pr-template-guard", "-Title", "test", "-BodyFile", $dryRunBody, "-Template", $customTemplate, "-DryRun", "-AllowDirty") `
        -ExpectedExitCode 1 `
        -ExpectedOutputPattern "Template override must reference a tracked PR template" | Out-Null

    Invoke-ScriptProcess `
        -Script $createScript `
        -Arguments @("-Base", "master", "-Head", "fix/pr-template-guard", "-Title", "test", "-BodyFile", $wrongTargetBody, "-DryRun", "-AllowDirty") `
        -ExpectedExitCode 1 `
        -ExpectedOutputPattern "Target Branch selection must check exactly base branch: master" | Out-Null

    Invoke-ScriptProcess `
        -Script $createScript `
        -Arguments @("-Base", "master", "-Head", "fix/pr-template-guard", "-Title", "test", "-BodyFile", $wrongCaseTargetBody, "-DryRun", "-AllowDirty") `
        -ExpectedExitCode 1 `
        -ExpectedOutputPattern "Target Branch section does not list base branch: master" | Out-Null

    Invoke-ScriptProcess `
        -Script $createScript `
        -Arguments @("-Base", "publish/aliyun-private", "-Head", "fix/pr-template-guard", "-Title", "test", "-BodyFile", $dryRunBody, "-DryRun", "-AllowDirty") `
        -ExpectedExitCode 1 `
        -ExpectedOutputPattern "Publish branch pull requests must use same-repository master as the head branch" | Out-Null

    Invoke-ScriptProcess `
        -Script $createScript `
        -Arguments @("-Base", "publish/aliyun-private", "-Head", "LDmoxeii:master", "-Title", "test", "-BodyFile", $dryRunBody, "-DryRun", "-AllowDirty") `
        -ExpectedExitCode 1 `
        -ExpectedOutputPattern "Publish branch pull requests must use same-repository master as the head branch" | Out-Null

    Invoke-ScriptProcess `
        -Script $createScript `
        -Arguments @("-Base", "Publish/Aliyun-Private", "-Head", "master", "-Title", "test", "-BodyFile", $dryRunBody, "-DryRun", "-AllowDirty") `
        -ExpectedExitCode 1 `
        -ExpectedOutputPattern "Unsupported base branch 'Publish/Aliyun-Private'" | Out-Null
}
finally {
    if ($detachedWorktree -and (Test-Path -LiteralPath $detachedWorktree)) {
        & git -C $repoRoot worktree remove --force $detachedWorktree | Out-Null
    }
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
}

Write-Output "OK: PR workflow script tests passed."
