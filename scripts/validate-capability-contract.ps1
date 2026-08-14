[CmdletBinding()]
param(
    [string] $FactsFile,
    [string] $RepoRoot
)

$ErrorActionPreference = "Stop"
$repoRoot = if ($RepoRoot) {
    (Resolve-Path -LiteralPath $RepoRoot).Path
} else {
    (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
}
$ownsFactsFile = $false
if (-not $FactsFile) {
    $FactsFile = Join-Path ([System.IO.Path]::GetTempPath()) ("cap4k-capability-contract-" + [System.Guid]::NewGuid() + '.json')
    $exportScript = Join-Path $PSScriptRoot 'export-capability-contract-facts.ps1'
    & $exportScript -OutputFile $FactsFile | Out-Null
    $ownsFactsFile = $true
}

function New-ContractDriftMessage {
    param(
        [string] $Problem,
        [string] $AuthoritativeCodeSource,
        [string] $ProjectionLocation,
        [string] $RecommendedRemediation,
        [string] $Details
    )
    return @(
        $Problem
        "authoritative code source: $AuthoritativeCodeSource"
        "projection location: $ProjectionLocation"
        "recommended remediation: $RecommendedRemediation"
        $Details
    ) -join "`n"
}

function Assert-SetEqual {
    param(
        [string] $Label,
        [string[]] $Expected,
        [string[]] $Actual,
        [string] $Location,
        [string] $AuthoritativeCodeSource,
        [string] $RecommendedRemediation
    )
    $expectedSet = @($Expected | Sort-Object -Unique)
    $actualSet = @($Actual | Sort-Object -Unique)
    $difference = @(Compare-Object -ReferenceObject $expectedSet -DifferenceObject $actualSet)
    if ($difference.Count -gt 0) {
        $missing = @($difference | Where-Object SideIndicator -eq '<=' | ForEach-Object InputObject)
        $unexpected = @($difference | Where-Object SideIndicator -eq '=>' | ForEach-Object InputObject)
        throw (New-ContractDriftMessage `
            -Problem "$Label drift" `
            -AuthoritativeCodeSource $AuthoritativeCodeSource `
            -ProjectionLocation $Location `
            -RecommendedRemediation $RecommendedRemediation `
            -Details "expected=[$($expectedSet -join ', ')] actual=[$($actualSet -join ', ')] missing=[$($missing -join ', ')] unexpected=[$($unexpected -join ', ')]")
    }
}

function Get-ContractBlocks {
    param(
        [string] $Text,
        [string] $Kind,
        [string] $Location,
        [string] $AuthoritativeCodeSource,
        [string] $RecommendedRemediation
    )
    $pattern = "(?s)<!--\s*CAPABILITY_CONTRACT:$([regex]::Escape($Kind))\s*-->(.*?)<!--\s*/CAPABILITY_CONTRACT:$([regex]::Escape($Kind))\s*-->"
    $matches = @([regex]::Matches($Text, $pattern))
    if ($matches.Count -eq 0) {
        throw (New-ContractDriftMessage `
            -Problem "Missing CAPABILITY_CONTRACT:$Kind block" `
            -AuthoritativeCodeSource $AuthoritativeCodeSource `
            -ProjectionLocation $Location `
            -RecommendedRemediation $RecommendedRemediation `
            -Details 'expected=[one or more marked blocks] actual=[none]')
    }
    return @($matches | ForEach-Object { $_.Groups[1].Value })
}

function Get-BacktickValues {
    param([string] $Text, [string] $Pattern)
    $expression = '\x60(?<value>' + $Pattern + ')\x60'
    return @([regex]::Matches($Text, $expression) | ForEach-Object { $_.Groups['value'].Value })
}

function Assert-MarkedSet {
    param(
        [string] $RelativePath,
        [string] $Kind,
        [string] $ValuePattern,
        [string[]] $Expected,
        [string] $AuthoritativeCodeSource,
        [string] $RecommendedRemediation
    )
    $path = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw (New-ContractDriftMessage `
            -Problem 'Missing capability contract projection' `
            -AuthoritativeCodeSource $AuthoritativeCodeSource `
            -ProjectionLocation $RelativePath `
            -RecommendedRemediation $RecommendedRemediation `
            -Details "expected=[projection file] actual=[missing]")
    }
    $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    $blocks = @(Get-ContractBlocks -Text $text -Kind $Kind -Location $RelativePath -AuthoritativeCodeSource $AuthoritativeCodeSource -RecommendedRemediation $RecommendedRemediation)
    for ($index = 0; $index -lt $blocks.Count; $index++) {
        $actual = Get-BacktickValues -Text $blocks[$index] -Pattern $ValuePattern
        Assert-SetEqual -Label $Kind -Expected $Expected -Actual $actual -Location "$RelativePath block $($index + 1)" -AuthoritativeCodeSource $AuthoritativeCodeSource -RecommendedRemediation $RecommendedRemediation
    }
}

function Assert-PublicTaskContracts {
    param([object[]] $Expected)
    $relativePath = 'docs/public/reference/gradle-plugin.md'
    $source = 'cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineCapabilityDescriptors.kt#PipelinePublicTasks'
    $remediation = "Update the PUBLIC_TASKS table in $relativePath to match PipelinePublicTasks.contracts; do not edit the exported facts JSON."
    $path = Join-Path $repoRoot $relativePath
    $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    $blocks = @(Get-ContractBlocks -Text $text -Kind 'PUBLIC_TASKS' -Location $relativePath -AuthoritativeCodeSource $source -RecommendedRemediation $remediation)
    $actual = @()
    foreach ($block in $blocks) {
        foreach ($line in ($block -split "\r?\n")) {
            $match = [regex]::Match($line, '^\|\s*`(?<name>cap4k[A-Z][A-Za-z0-9]*)`\s*\|\s*`(?<boundary>[a-z_]+)`\s*\|\s*`(?<external>true|false)`\s*\|')
            if ($match.Success) {
                $actual += "$($match.Groups['name'].Value)|$($match.Groups['boundary'].Value)|$($match.Groups['external'].Value)"
            }
        }
    }
    $expectedValues = @($Expected | ForEach-Object {
        $external = ([string]$_.readsLiveExternalInput).ToLowerInvariant()
        "$($_.name)|$($_.mutationBoundary)|$external"
    })
    Assert-SetEqual -Label 'PUBLIC_TASK_CONTRACTS' -Expected $expectedValues -Actual $actual -Location $relativePath -AuthoritativeCodeSource $source -RecommendedRemediation $remediation
}

try {
    & (Join-Path $PSScriptRoot 'validate-local-markdown-links.ps1') -RepoRoot $repoRoot -Paths @('README.md', 'README.en.md', 'docs/public') -Label 'Public surface' | Out-Null

    $resolvedFacts = (Resolve-Path -LiteralPath $FactsFile -ErrorAction Stop).Path
    $facts = Get-Content -LiteralPath $resolvedFacts -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($facts.schema -ne 'cap4k.capability-contract-facts.v3') {
        throw (New-ContractDriftMessage -Problem 'Unsupported capability contract facts schema' -AuthoritativeCodeSource 'cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/CapabilityContractFacts.kt#CAP4K_CAPABILITY_CONTRACT_FACTS_SCHEMA' -ProjectionLocation $resolvedFacts -RecommendedRemediation 'Regenerate the facts with scripts/export-capability-contract-facts.ps1 using the current checkout.' -Details "expected=[cap4k.capability-contract-facts.v3] actual=[$($facts.schema)]")
    }

    $publicTaskContracts = @($facts.publicTasks)
    $publicTasks = @($publicTaskContracts | ForEach-Object name)
    $agentSections = @($facts.agentSections | ForEach-Object id)
    $agentFiles = @('manifest.json') + @($facts.agentSections | ForEach-Object path)
    $runtimeCapabilities = @($facts.runtimeCapabilities | ForEach-Object capabilityId)
    $runtimeProviders = @($facts.runtimeProviders | ForEach-Object providerId)
    $outputKinds = @($facts.outputKinds)
    $analyzerCapabilities = @($facts.pipelineCapabilities | Where-Object { 'analysis' -in @($_.executionLanes) } | ForEach-Object capabilityId)
    $analyzerOutputs = @($facts.analyzerOutputs)
    $analyzerPartitions = @($facts.analyzerPartitions | ForEach-Object {
        "$($_.id)|$($_.nodeId)|$($_.sourceCapabilityId)|$(@($_.consumerCapabilityIds) -join ',')|$(@($_.outputIds) -join ',')"
    })

    $taskSource = 'cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineCapabilityDescriptors.kt#PipelinePublicTasks'
    $taskRemediation = 'Update the marked PUBLIC_TASKS block to match PipelinePublicTasks.contracts; do not edit the exported facts JSON.'
    Assert-MarkedSet -RelativePath 'README.md' -Kind 'PUBLIC_TASKS' -ValuePattern 'cap4k[A-Z][A-Za-z0-9]*' -Expected $publicTasks -AuthoritativeCodeSource $taskSource -RecommendedRemediation $taskRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/gradle-plugin.md' -Kind 'PUBLIC_TASKS' -ValuePattern 'cap4k[A-Z][A-Za-z0-9]*' -Expected $publicTasks -AuthoritativeCodeSource $taskSource -RecommendedRemediation $taskRemediation
    Assert-MarkedSet -RelativePath 'docs/public/generator/generation-tasks.md' -Kind 'PUBLIC_TASKS' -ValuePattern 'cap4k[A-Z][A-Za-z0-9]*' -Expected $publicTasks -AuthoritativeCodeSource $taskSource -RecommendedRemediation $taskRemediation
    Assert-PublicTaskContracts -Expected $publicTaskContracts

    $agentSource = 'cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentContractCatalog.kt and cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/AgentContracts.kt'
    $agentRemediation = 'Update the marked Agent API projection to match the code-derived section/status contract; do not edit the facts JSON manually.'
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_SECTIONS' -ValuePattern '(?:manifest|[a-z]+)\.json' -Expected $agentFiles -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_SNAPSHOT_STATUS' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.snapshot) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_CAPABILITY_VIEWS' -ValuePattern '(?:supported|effective)' -Expected @($facts.agentCapabilityViews) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_EFFECTIVE_STATUS' -ValuePattern '(?:configured|ready|blocked|not_applicable)' -Expected @($facts.agentStatusVocabulary.effectiveCapability) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_EVIDENCE_FRESHNESS' -ValuePattern '(?:fresh|stale|unknown|missing)' -Expected @($facts.agentStatusVocabulary.evidenceFreshness) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_VALIDATION_STATUS' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.validation) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_DIAGNOSTIC_LEVEL' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.diagnosticLevel) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_RUNTIME_FRAMEWORK_SUPPORT' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.runtimeFrameworkSupport) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_RUNTIME_APPLICATION_ASSEMBLY' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.runtimeApplicationAssembly) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_RUNTIME_OBSERVATION' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.runtimeObservation) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_RUNTIME_OPERATIONAL_STATE' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.runtimeOperationalState) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_RUNTIME_VERIFICATION' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.runtimeVerification) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'AGENT_RUNTIME_LIVE_STATE_SOURCE' -ValuePattern '[a-z_]+' -Expected @($facts.agentStatusVocabulary.runtimeLiveStateSource) -AuthoritativeCodeSource $agentSource -RecommendedRemediation $agentRemediation

    $runtimeSource = 'cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsCatalog.kt'
    $runtimeRemediation = 'Update the marked Runtime Agent API catalog to match RuntimeAgentFactsCatalog; do not add a separate hand-written catalog.'
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'RUNTIME_CAPABILITIES' -ValuePattern 'runtime\.[a-z0-9.-]+' -Expected $runtimeCapabilities -AuthoritativeCodeSource $runtimeSource -RecommendedRemediation $runtimeRemediation
    Assert-MarkedSet -RelativePath 'docs/public/reference/agent-api.md' -Kind 'RUNTIME_PROVIDERS' -ValuePattern 'integration-event-transport\.[a-z0-9.-]+' -Expected $runtimeProviders -AuthoritativeCodeSource $runtimeSource -RecommendedRemediation $runtimeRemediation

    Assert-MarkedSet -RelativePath 'docs/public/reference/plan-json.md' -Kind 'OUTPUT_KINDS' -ValuePattern '[A-Z][A-Z_]+' -Expected $outputKinds -AuthoritativeCodeSource 'cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineContracts.kt#ArtifactOutputKind' -RecommendedRemediation 'Update the marked OUTPUT_KINDS block to match ArtifactOutputKind entries.'
    Assert-MarkedSet -RelativePath 'docs/public/generator/analysis-evidence.md' -Kind 'ANALYZER_CAPABILITIES' -ValuePattern 'pipeline\.[a-z0-9.-]+' -Expected $analyzerCapabilities -AuthoritativeCodeSource 'built-in PipelineCapabilityDescriptor catalogs plus CapabilityContractFactsFactory analyzer projection' -RecommendedRemediation 'Update the marked Analyzer capability block to match analysis-lane descriptors.'
    Assert-MarkedSet -RelativePath 'docs/public/generator/analysis-evidence.md' -Kind 'ANALYZER_OUTPUTS' -ValuePattern '[a-z][a-z0-9-]+' -Expected $analyzerOutputs -AuthoritativeCodeSource 'built-in analysis-lane generator/artifact-addon descriptors' -RecommendedRemediation 'Update the marked Analyzer output block to match code-derived analysis provider outputs.'
    Assert-MarkedSet -RelativePath 'docs/public/generator/analysis-evidence.md' -Kind 'ANALYZER_PARTITIONS' -ValuePattern '[A-Za-z][A-Za-z0-9]*\|analyzer\.partition\.[a-z-]+\|pipeline\.[a-z0-9.-]+\|pipeline\.[a-z0-9.,-]+\|[A-Za-z0-9_.,-]+' -Expected $analyzerPartitions -AuthoritativeCodeSource 'cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt#AnalyzerContractCatalog' -RecommendedRemediation 'Update the marked Analyzer partition block to match AnalyzerContractCatalog; do not hand-maintain a second partition graph.'

    $routingPath = Join-Path $repoRoot 'skills/cap4k-authoring/routing.yaml'
    $routing = Get-Content -LiteralPath $routingPath -Raw -Encoding UTF8
    $routeSections = @([regex]::Matches($routing, '(?m)^\s+agent_sections:\s*\[(?<values>[^]]*)\]\s*$') | ForEach-Object { $_.Groups['values'].Value.Split(',') | ForEach-Object Trim })
    Assert-SetEqual -Label 'Skill Agent section coverage' -Expected $agentSections -Actual $routeSections -Location 'skills/cap4k-authoring/routing.yaml' -AuthoritativeCodeSource 'cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/AgentContractCatalog.kt' -RecommendedRemediation 'Update routing.yaml agent_sections so every production Agent section is covered and no retired section remains.'

    $skillFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'skills/cap4k-authoring') -Recurse -File)
    $unknownSkillTasks = @()
    foreach ($file in $skillFiles) {
        $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        $unknownSkillTasks += @([regex]::Matches($text, '`(?<task>cap4k[A-Z][A-Za-z0-9]*)`') | ForEach-Object { $_.Groups['task'].Value } | Where-Object { $_ -notin $publicTasks })
    }
    if ($unknownSkillTasks.Count -gt 0) {
        $unknownTaskList = @($unknownSkillTasks | Sort-Object -Unique) -join ', '
        throw (New-ContractDriftMessage -Problem 'Skill references unknown public tasks' -AuthoritativeCodeSource $taskSource -ProjectionLocation 'skills/cap4k-authoring/**' -RecommendedRemediation 'Replace/remove the unknown task reference, or intentionally register it in PipelinePublicTasks.contracts and regenerate facts.' -Details "expected=[$($publicTasks -join ', ')] actual-unknown=[$unknownTaskList]")
    }

    $currentSurfaceFiles = @(
        Get-Item -LiteralPath (Join-Path $repoRoot 'README.md'), (Join-Path $repoRoot 'README.en.md')
        Get-ChildItem -LiteralPath (Join-Path $repoRoot 'docs/public') -Recurse -File -Filter '*.md'
        Get-ChildItem -LiteralPath (Join-Path $repoRoot 'skills/cap4k-authoring') -Recurse -File | Where-Object { $_.Extension -in @('.md', '.yaml', '.yml') }
    )
    $forbiddenCurrentSurfacePatterns = [ordered]@{
        'internal image production metadata' = '<!--\s*IMAGE_PROMPT:'
        'public issue-history dependency wording' = '\bissue history\b'
        'public internal-spec dependency wording' = '\binternal specs\b'
        'public phase-map dependency wording' = '\bPhase 1 maps\b'
        'retired bootstrap authoring history' = '(?i)\bbootstrap\b'
        'removed nullable migration wording' = '旧 nullable 字段已移除'
        'removed storage migration wording' = '旧 storage 字段已移除'
        'removed generator-id migration wording' = '不保留 `design-\*` 旧名'
        'future projection roadmap wording' = '后续 relational/embedded projection 可以扩展'
        'plugin retirement narration' = '不再提供项目初始化能力'
        'retired machinery narration' = 'retired bootstrap machinery'
    }
    $currentSurfaceViolations = @()
    foreach ($file in $currentSurfaceFiles) {
        $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        $relativePath = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName).Replace('\', '/')
        foreach ($entry in $forbiddenCurrentSurfacePatterns.GetEnumerator()) {
            if ($text -match $entry.Value) { $currentSurfaceViolations += "${relativePath}: $($entry.Key)" }
        }
    }
    if ($currentSurfaceViolations.Count -gt 0) {
        throw (New-ContractDriftMessage -Problem 'Current public/skill surfaces contain history or internal-process wording' -AuthoritativeCodeSource 'docs/comet/changes/public-surface-contract/specs/capability-contract-governance/spec.md#Current-only 内容规则' -ProjectionLocation 'README.md, README.en.md, docs/public/**, skills/cap4k-authoring/**' -RecommendedRemediation 'Rewrite the current surface in present-tense terms; preserve history only in release, migration, archive, Issue, or internal historical material.' -Details "actual-violations=[$($currentSurfaceViolations -join '; ')]")
    }

    $matrixPath = Join-Path $repoRoot 'docs/superpowers/capability-matrix.md'
    if ((Get-Content -LiteralPath $matrixPath -Raw -Encoding UTF8) -match 'current human-readable truth source') {
        throw (New-ContractDriftMessage -Problem 'Capability matrix claims current truth-source authority' -AuthoritativeCodeSource 'CapabilityContractFactsFactory and production descriptor/catalog sources' -ProjectionLocation 'docs/superpowers/capability-matrix.md' -RecommendedRemediation 'Remove the truth-source claim and direct current consumers to exported code-derived capability facts.' -Details 'expected=[historical/internal analysis asset] actual=[current human-readable truth source]')
    }

    Write-Output "OK: capability contract facts align with Public Docs, Skill routing, and current-state content."
}
finally {
    if ($ownsFactsFile -and (Test-Path -LiteralPath $FactsFile)) { Remove-Item -LiteralPath $FactsFile -Force }
}
