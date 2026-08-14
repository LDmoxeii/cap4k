[CmdletBinding()]
param(
    [string] $FactsFile
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$pwsh = (Get-Command pwsh -ErrorAction Stop).Source
$validator = Join-Path $PSScriptRoot 'validate-capability-contract.ps1'
$ownsFactsFile = $false
if ([string]::IsNullOrWhiteSpace($FactsFile)) {
    $FactsFile = Join-Path ([System.IO.Path]::GetTempPath()) ("cap4k-capability-test-facts-" + [System.Guid]::NewGuid() + '.json')
    & (Join-Path $PSScriptRoot 'export-capability-contract-facts.ps1') -OutputFile $FactsFile | Out-Null
    $ownsFactsFile = $true
}
$resolvedFactsFile = (Resolve-Path -LiteralPath $FactsFile).Path
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("cap4k-capability-contract-test-" + [System.Guid]::NewGuid())

function Invoke-Validator {
    param([string] $Root, [int] $ExpectedExitCode, [string[]] $ExpectedPatterns)
    $output = & $pwsh -NoProfile -ExecutionPolicy Bypass -File $validator -FactsFile $resolvedFactsFile -RepoRoot $Root 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()
    if ($exitCode -ne $ExpectedExitCode) {
        throw "Expected validator exit code $ExpectedExitCode but got $exitCode.`n$text"
    }
    foreach ($pattern in @($ExpectedPatterns)) {
        if ($pattern -and $text -notmatch $pattern) {
            throw "Expected validator output to match '$pattern'.`n$text"
        }
    }
}

function Invoke-ManagedDriftFixture {
    param([string] $Root, [string[]] $Patterns)
    Invoke-Validator -Root $Root -ExpectedExitCode 1 -ExpectedPatterns (@($Patterns) + @('(?s)authoritative.*code.*source:', '(?s)recommended.*remediation:'))
}

New-Item -ItemType Directory -Path $tempRoot | Out-Null
try {
    foreach ($relativePath in @('README.md', 'README.en.md', 'docs/public', 'docs/superpowers/capability-matrix.md', 'skills/cap4k-authoring')) {
        $source = Join-Path $repoRoot $relativePath
        $destination = Join-Path $tempRoot $relativePath
        $destinationParent = Split-Path -Parent $destination
        New-Item -ItemType Directory -Path $destinationParent -Force | Out-Null
        if (Test-Path -LiteralPath $source -PathType Container) {
            Copy-Item -LiteralPath $source -Destination $destinationParent -Recurse
        } else {
            Copy-Item -LiteralPath $source -Destination $destination
        }
    }

    Invoke-Validator -Root $tempRoot -ExpectedExitCode 0 -ExpectedPatterns @('align with Public Docs')

    $variantFactsPath = Join-Path $tempRoot 'variant-facts.json'
    $originalFacts = Get-Content -LiteralPath $resolvedFactsFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $originalResolvedFactsFile = $resolvedFactsFile
    $agentApiPath = Join-Path $tempRoot 'docs/public/reference/agent-api.md'

    $agentSectionFacts = $originalFacts | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $agentSectionFacts.agentSections += [pscustomobject]@{ id = 'advice'; path = 'advice.json'; schema = 'cap4k.agent.advice.v1' }
    $agentSectionFacts | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $variantFactsPath -Encoding utf8NoBOM
    $agentApi = Get-Content -LiteralPath $agentApiPath -Raw -Encoding UTF8
    $agentApi = $agentApi.Replace('| `diagnostics.json` | 稳定 diagnostic identity、level、stage、path、message 与 actionable hint。 |', "| ``diagnostics.json`` | 稳定 diagnostic identity、level、stage、path、message 与 actionable hint。 |`n| ``advice.json`` | Synthetic section used to prove Skill route drift detection. |")
    Set-Content -LiteralPath $agentApiPath -Value $agentApi -Encoding utf8NoBOM
    $resolvedFactsFile = $variantFactsPath
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)Skill Agent section coverage drift.*advice')
    $resolvedFactsFile = $originalResolvedFactsFile
    Copy-Item -LiteralPath (Join-Path $repoRoot 'docs/public/reference/agent-api.md') -Destination $agentApiPath -Force

    $statusFixtures = @(
        [pscustomobject]@{ property = 'snapshot'; kind = 'AGENT_SNAPSHOT_STATUS'; value = 'retired_snapshot' },
        [pscustomobject]@{ property = 'effectiveCapability'; kind = 'AGENT_EFFECTIVE_STATUS'; value = 'retired_effective' },
        [pscustomobject]@{ property = 'validation'; kind = 'AGENT_VALIDATION_STATUS'; value = 'retired_validation' },
        [pscustomobject]@{ property = 'evidenceFreshness'; kind = 'AGENT_EVIDENCE_FRESHNESS'; value = 'retired_freshness' },
        [pscustomobject]@{ property = 'diagnosticLevel'; kind = 'AGENT_DIAGNOSTIC_LEVEL'; value = 'critical' },
        [pscustomobject]@{ property = 'runtimeFrameworkSupport'; kind = 'AGENT_RUNTIME_FRAMEWORK_SUPPORT'; value = 'unsupported' },
        [pscustomobject]@{ property = 'runtimeApplicationAssembly'; kind = 'AGENT_RUNTIME_APPLICATION_ASSEMBLY'; value = 'assembled' },
        [pscustomobject]@{ property = 'runtimeObservation'; kind = 'AGENT_RUNTIME_OBSERVATION'; value = 'performed' },
        [pscustomobject]@{ property = 'runtimeOperationalState'; kind = 'AGENT_RUNTIME_OPERATIONAL_STATE'; value = 'healthy' },
        [pscustomobject]@{ property = 'runtimeVerification'; kind = 'AGENT_RUNTIME_VERIFICATION'; value = 'verified' },
        [pscustomobject]@{ property = 'runtimeLiveStateSource'; kind = 'AGENT_RUNTIME_LIVE_STATE_SOURCE'; value = 'alternate_registry' }
    )
    foreach ($fixture in $statusFixtures) {
        $statusFacts = $originalFacts | ConvertTo-Json -Depth 100 | ConvertFrom-Json
        $property = $fixture.property
        $statusFacts.agentStatusVocabulary.$property += $fixture.value
        $statusFacts | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $variantFactsPath -Encoding utf8NoBOM
        $resolvedFactsFile = $variantFactsPath
        Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @("(?s)$($fixture.kind) drift.*$($fixture.value)")
    }
    $resolvedFactsFile = $originalResolvedFactsFile

    $runtimeProviderFacts = $originalFacts | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $provider = $runtimeProviderFacts.runtimeProviders[0] | Select-Object *
    $provider.providerId = 'integration-event-transport.test'
    $provider.displayName = 'Test Integration Event Transport'
    $runtimeProviderFacts.runtimeProviders += $provider
    $runtimeProviderFacts | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $variantFactsPath -Encoding utf8NoBOM
    $resolvedFactsFile = $variantFactsPath
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)RUNTIME_PROVIDERS drift.*integration-event-transport.test')
    $resolvedFactsFile = $originalResolvedFactsFile

    $analyzerFacts = $originalFacts | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $analyzer = $analyzerFacts.pipelineCapabilities[0] | Select-Object *
    $analyzer.capabilityId = 'pipeline.generator.analysis-test'
    $analyzer.executionLanes = @('analysis')
    $analyzerFacts.pipelineCapabilities += $analyzer
    $analyzerFacts | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $variantFactsPath -Encoding utf8NoBOM
    $resolvedFactsFile = $variantFactsPath
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)ANALYZER_CAPABILITIES drift.*pipeline.generator.analysis-test')
    $resolvedFactsFile = $originalResolvedFactsFile

    $analyzerOutputFacts = $originalFacts | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $analyzerOutputFacts.analyzerOutputs += 'analysis-test'
    $analyzerOutputFacts | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $variantFactsPath -Encoding utf8NoBOM
    $resolvedFactsFile = $variantFactsPath
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)ANALYZER_OUTPUTS drift.*analysis-test')
    $resolvedFactsFile = $originalResolvedFactsFile

    $analyzerPartitionFacts = $originalFacts | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $analyzerPartitionFacts.analyzerPartitions[0].outputIds += 'partition-test-output'
    $analyzerPartitionFacts | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $variantFactsPath -Encoding utf8NoBOM
    $resolvedFactsFile = $variantFactsPath
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)ANALYZER_PARTITIONS drift.*partition-test-output')
    $resolvedFactsFile = $originalResolvedFactsFile

    $outputFacts = $originalFacts | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $outputFacts.outputKinds += 'analysis_test_artifact'
    $outputFacts | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $variantFactsPath -Encoding utf8NoBOM
    $resolvedFactsFile = $variantFactsPath
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)OUTPUT_KINDS drift.*analysis_test_artifact')
    $resolvedFactsFile = $originalResolvedFactsFile

    $taskBoundaryFacts = $originalFacts | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $taskBoundaryFacts.publicTasks[0].mutationBoundary = 'project_sources'
    $taskBoundaryFacts | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $variantFactsPath -Encoding utf8NoBOM
    $resolvedFactsFile = $variantFactsPath
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)PUBLIC_TASK_CONTRACTS drift.*project_sources')
    $resolvedFactsFile = $originalResolvedFactsFile

    $readmePath = Join-Path $tempRoot 'README.md'
    $readme = Get-Content -LiteralPath $readmePath -Raw -Encoding UTF8
    $readme = $readme.Replace('- `cap4kAnalysisGenerate`：生成代码分析、流程和 drawing-board 等证据。', '')
    Set-Content -LiteralPath $readmePath -Value $readme -Encoding utf8NoBOM
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)PUBLIC_TASKS drift.*cap4kAnalysisGenerate')
    Copy-Item -LiteralPath (Join-Path $repoRoot 'README.md') -Destination $readmePath -Force

    $missingBlockText = Get-Content -LiteralPath $agentApiPath -Raw -Encoding UTF8
    $missingBlockText = $missingBlockText.Replace('<!-- CAPABILITY_CONTRACT:AGENT_SNAPSHOT_STATUS -->', '<!-- removed marker -->')
    Set-Content -LiteralPath $agentApiPath -Value $missingBlockText -Encoding utf8NoBOM
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('Missing CAPABILITY_CONTRACT:AGENT_SNAPSHOT_STATUS block')
    Copy-Item -LiteralPath (Join-Path $repoRoot 'docs/public/reference/agent-api.md') -Destination $agentApiPath -Force

    $skillPath = Join-Path $tempRoot 'skills/cap4k-authoring/SKILL.md'
    Add-Content -LiteralPath $skillPath -Value "`n- Run ``cap4kUnknownTask``." -Encoding UTF8
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('(?s)unknown public tasks.*cap4kUnknownTask')
    Copy-Item -LiteralPath (Join-Path $repoRoot 'skills/cap4k-authoring/SKILL.md') -Destination $skillPath -Force

    $brokenLinkPage = Join-Path $tempRoot 'docs/public/index.md'
    Add-Content -LiteralPath $brokenLinkPage -Value "`n[Broken](missing-page.md)" -Encoding UTF8
    Invoke-Validator -Root $tempRoot -ExpectedExitCode 1 -ExpectedPatterns @('(?s)Broken Public surface local Markdown links.*missing-page.md')
    Copy-Item -LiteralPath (Join-Path $repoRoot 'docs/public/index.md') -Destination $brokenLinkPage -Force

    $publicIndex = Join-Path $tempRoot 'docs/public/index.md'
    Add-Content -LiteralPath $publicIndex -Value "`n<!-- IMAGE_PROMPT: internal-only -->" -Encoding UTF8
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('production\s+metadata')
    Copy-Item -LiteralPath (Join-Path $repoRoot 'docs/public/index.md') -Destination $publicIndex -Force

    $matrixPath = Join-Path $tempRoot 'docs/superpowers/capability-matrix.md'
    Add-Content -LiteralPath $matrixPath -Value "`nThis is the current human-readable truth source." -Encoding UTF8
    Invoke-ManagedDriftFixture -Root $tempRoot -Patterns @('truth-source authority')
} finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
    if ($ownsFactsFile -and (Test-Path -LiteralPath $FactsFile)) { Remove-Item -LiteralPath $FactsFile -Force }
}

Write-Output 'OK: capability contract validator tests passed.'
$global:LASTEXITCODE = 0
