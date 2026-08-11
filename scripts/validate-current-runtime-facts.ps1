$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path

function Get-EffectiveRuntimeSpecText {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Capability
    )

    $canonicalFile = Join-Path $repoRoot "docs/comet/specs/$Capability/spec.md"
    $selectionFile = Join-Path $repoRoot '.comet/current-change.json'
    if (Test-Path -LiteralPath $selectionFile -PathType Leaf) {
        try {
            $selection = Get-Content -LiteralPath $selectionFile -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($selection.workflow -eq 'native' -and -not [string]::IsNullOrWhiteSpace($selection.change)) {
                $proposedFile = Join-Path $repoRoot "docs/comet/changes/$($selection.change)/specs/$Capability/spec.md"
                if (Test-Path -LiteralPath $proposedFile -PathType Leaf) {
                    return Get-Content -LiteralPath $proposedFile -Raw -Encoding UTF8
                }
            }
        }
        catch {
            throw "Unable to read the selected Comet Native change while validating Runtime contracts: $($_.Exception.Message)"
        }
    }

    if (-not (Test-Path -LiteralPath $canonicalFile -PathType Leaf)) {
        throw "Missing canonical Runtime spec: docs/comet/specs/$Capability/spec.md"
    }
    return Get-Content -LiteralPath $canonicalFile -Raw -Encoding UTF8
}

function Assert-ContainsRuntimeFact {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text,
        [Parameter(Mandatory = $true)]
        [string]$Pattern,
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if ($Text -notmatch $Pattern) {
        throw $Message
    }
}
$roadmapContractText = Get-EffectiveRuntimeSpecText -Capability 'runtime-roadmap'
if ($roadmapContractText -match '(?s)Runtime Repository Contract and\s+Runtime Surface Cleanup may proceed independently') {
    throw 'Runtime roadmap must not describe Repository Contract or Surface Cleanup as pending.'
}
Assert-ContainsRuntimeFact $roadmapContractText 'complete on `master` through PR #183' 'Runtime roadmap must record completion through PR #183.'
Assert-ContainsRuntimeFact $roadmapContractText 'No Actuator endpoint\s+currently exists' 'Runtime roadmap must state that no Actuator endpoint currently exists.'
Assert-ContainsRuntimeFact $roadmapContractText 'future optional endpoint' 'Runtime roadmap must limit Actuator projection to a future optional endpoint.'

$jacksonContractText = Get-EffectiveRuntimeSpecText -Capability 'runtime-jackson-only'
if ($jacksonContractText -match 'HTTP self-routing 和 subscriber registry') {
    throw 'Runtime Jackson-only contract must not preserve the retired HTTP subscriber registry.'
}
Assert-ContainsRuntimeFact $jacksonContractText '当前 Runtime 不存在 HTTP subscriber registry' 'Runtime Jackson-only contract must state that the HTTP subscriber registry is absent.'

$deliveryContextContractText = Get-EffectiveRuntimeSpecText -Capability 'reliable-event-delivery-context'
if ($deliveryContextContractText -match '(?s)shall not modify Integration Event routes, attach/detach behavior,.*HTTP subscriber registry') {
    throw 'Reliable delivery context contract must not preserve retired Integration Event attach/registry wording.'
}
Assert-ContainsRuntimeFact $deliveryContextContractText 'DomainEventSupervisor\.attach/detach' 'Reliable delivery context contract must preserve the Domain Event/UoW attach boundary.'
Assert-ContainsRuntimeFact $deliveryContextContractText 'IntegrationEventSupervisor.*enqueue, schedule, and delay' 'Reliable delivery context contract must identify the public Integration Event operations.'
Assert-ContainsRuntimeFact $deliveryContextContractText 'EventAttachment' 'Reliable delivery context contract must classify transport-internal attachment hooks.'
Assert-ContainsRuntimeFact $deliveryContextContractText 'neither an archive path nor an HTTP subscriber registry' 'Reliable delivery context contract must record the retired archive and HTTP registry surfaces.'

$transportContractText = Get-EffectiveRuntimeSpecText -Capability 'runtime-integration-event-transport'
if ($transportContractText -match 'with\s+optional Actuator projection') {
    throw 'Integration Event transport contract must not imply that an Actuator projection currently exists.'
}
Assert-ContainsRuntimeFact $transportContractText 'No Actuator endpoint\s+currently exists' 'Integration Event transport contract must state that no Actuator endpoint currently exists.'
Assert-ContainsRuntimeFact $transportContractText 'RuntimeProviderStateRegistry\.snapshot\(\)' 'Integration Event transport contract must bind any future projection to the live registry snapshot.'

$uowContractText = Get-EffectiveRuntimeSpecText -Capability 'application-execution-uow-stabilization'
if ($uowContractText -match 'retry/archive') {
    throw 'Application execution/UoW contract must not reference the retired archive path.'
}
Assert-ContainsRuntimeFact $uowContractText 'persistence, claim, retry, redrive, and the final terminal transition' 'Application execution/UoW contract must describe current origin-context propagation.'
Assert-ContainsRuntimeFact $uowContractText 'there is no current archive path' 'Application execution/UoW contract must state that no archive path currently exists.'

$retryContractText = Get-EffectiveRuntimeSpecText -Capability 'runtime-retry-policy-snapshot'
$requiredRetryFacts = [ordered]@{
    'policy version 1' = 'policy version is `1`'
    'ANY_EXCEPTION classification' = 'classification is `ANY_EXCEPTION`'
    '@Retry overrides' = '`@Retry` overrides `retryTimes` and `retryIntervals`'
    'attempts 1-10 delay' = 'attempts 1-10 wait 1 minute'
    'attempts 11-20 delay' = 'attempts 11-20 wait 5'
    'attempts 21+ delay' = 'attempts 21 and later wait 10'
    'custom interval repetition' = 'repeat the final configured interval'
    'carrier-specific fallback limits' = 'does\s+not unify or change those different fallback limits'
}
foreach ($entry in $requiredRetryFacts.GetEnumerator()) {
    if ($retryContractText -notmatch $entry.Value) {
        throw "Reliable retry-policy snapshot contract is missing $($entry.Key)."
    }
}

$integrationEventSupervisorFile = Join-Path $repoRoot 'ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/event/IntegrationEventSupervisor.kt'
$integrationEventSupervisorText = Get-Content -LiteralPath $integrationEventSupervisorFile -Raw -Encoding UTF8
if ($integrationEventSupervisorText -match '附加和解除附加') {
    throw 'IntegrationEventSupervisor KDoc must not describe retired public attach/detach semantics.'
}
if ($integrationEventSupervisorText -notmatch '可靠登记、计划与延迟发布') {
    throw 'IntegrationEventSupervisor KDoc must describe reliable registration, scheduling, and delayed publication.'
}

$currentDirectories = @(
    'docs/public',
    'docs/comet/specs',
    'docs/superpowers/analysis',
    'skills'
)
$currentFiles = @(
    'AGENTS.md',
    'README.md',
    'cap4k-plugin-pipeline-gradle/README.md'
)
$retiredTerms = [ordered]@{
    'EventSubscriberManager' = '\b(?:Default)?EventSubscriberManager\b'
    'AbstractEventSubscriber' = '\bAbstractEventSubscriber\b'
    'EventSubscriber<T>' = '\bEventSubscriber\s*<'
    'Console module' = '\bcap4k-ddd-console\b'
    'Console starter' = '\bcap4k-ddd-console-starter\b'
    'Console auto-configuration' = '\bDDDConsoleAutoConfiguration\b'
    'Console runtime package' = '\bcom\.only4\.cap4k\.ddd\.console\b'
    'Console HTTP endpoint' = '/cap4k/console(?:/|\b)'
    'Snowflake capability' = '\bSnowflake\b'
    'Snowflake Runtime module' = '\bddd-distributed-snowflake\b'
    'Snowflake starter' = '\bcap4k-ddd-snowflake-starter\b'
    'Snowflake policy' = '\bidentifier\.snowflake\b'
    'Worker-ID capability' = '\bWorker-?ID\b|\b__worker_id\b|\bworker_id\.sql\b'
    'Locker Runtime module' = '\bddd-distributed-locker-jdbc\b'
    'Locker starter' = '\bcap4k-ddd-locker-jdbc-starter\b'
    'Locker auto-configuration' = '\bJdbcLockerAutoConfiguration\b'
    'Locker SQL' = '\b__locker\b|\blocker\.sql\b'
}
$allowedRetiredTermsByPath = @{
    # These active Runtime contract specs intentionally name the retired boundary they define.
    # Keep this allowlist exact: new current-facts docs must still fail until their historical
    # wording is reviewed explicitly.
    'docs/comet/specs/runtime-agent-api-facts/spec.md' = @('Snowflake capability')
    'docs/comet/specs/runtime-agent-retired-descriptors/spec.md' = @('Snowflake capability')
    'docs/comet/specs/runtime-handler-contract/spec.md' = @('EventSubscriber<T>')
    'docs/comet/specs/runtime-roadmap/spec.md' = @('EventSubscriber<T>', 'Snowflake capability')
    'docs/comet/specs/runtime-surface-cleanup/spec.md' = @(
        'Snowflake capability',
        'Locker Runtime module',
        'Locker starter',
        'Locker auto-configuration',
        'Locker SQL'
    )
}

$activeRuntimePatterns = [ordered]@{
    'Locker public SPI' = '\binterface\s+Locker\b|\bcom\.only4\.cap4k\.ddd\.core\.application\.distributed\.Locker\b'
    'Locker implementation' = '\bJdbcLocker(?:AutoConfiguration|Properties)?\b'
    'Locker configuration prefix' = '\bcap4k\.ddd\.distributed\.locker\.jdbc\b'
    'Locker Runtime module' = '\bddd-distributed-locker-jdbc\b'
    'Locker starter' = '\bcap4k-ddd-locker-jdbc-starter\b'
    'Locker SQL' = '\b__locker\b|\blocker\.sql\b'
    'Console active surface' = '\bcap4k-ddd-console(?:-starter)?\b|\bDDDConsoleAutoConfiguration\b|\bcom\.only4\.cap4k\.ddd\.console\b'
    'Snowflake active surface' = '\bddd-distributed-snowflake\b|\bcap4k-ddd-snowflake-starter\b|\bcom\.only4\.cap4k\.ddd\.application\.distributed\.snowflake\b'
    'Saga active surface' = '\bddd-(?:application-)?saga(?:-jpa)?\b|\bcap4k-ddd-saga(?:-jpa)?-starter\b|\bcom\.only4\.cap4k\.ddd\.application\.saga\b'
    'HTTP-JPA active surface' = '\bddd-integration-event-http-jpa\b|\bcap4k-ddd-integration-event-http-jpa-starter\b|\bHttpJpa\w*\b'
    'FastJSON active stack' = '\bcom\.alibaba\.fastjson\w*\b|\bfastjson\d?\b'
    'Gson active stack' = '\bcom\.google\.gson\b|\bGsonBuilder\b|\bnew\s+Gson\b'
}

$expectedRetiredDescriptorIdentities = @('console', 'locker', 'saga', 'snowflake')
$retiredDescriptorPolicyFile = Join-Path $repoRoot 'cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RetiredRuntimeDescriptorPolicy.kt'
$retiredDescriptorPolicyText = Get-Content -LiteralPath $retiredDescriptorPolicyFile -Raw -Encoding UTF8
$declaredRetiredDescriptorIdentities = [regex]::Matches(
    $retiredDescriptorPolicyText,
    '(?m)^\s*"([a-z][a-z0-9-]*)",\s*$'
) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
if (Compare-Object $expectedRetiredDescriptorIdentities $declaredRetiredDescriptorIdentities) {
    throw ('Retired Runtime descriptor policy must declare exactly: ' + ($expectedRetiredDescriptorIdentities -join ', ') + '.')
}

$runtimeCatalogFile = Join-Path $repoRoot 'cap4k-plugin-pipeline-agent/src/main/kotlin/com/only4/cap4k/plugin/pipeline/agent/RuntimeAgentFactsCatalog.kt'
$runtimeCatalogText = Get-Content -LiteralPath $runtimeCatalogFile -Raw -Encoding UTF8
$expectedRuntimeCapabilityIds = @(
    'runtime.core-dispatch',
    'runtime.identifier-allocation',
    'runtime.integration-event-transport',
    'runtime.jpa-persistence',
    'runtime.local-domain-event',
    'runtime.reliable-command',
    'runtime.reliable-event'
)
$declaredRuntimeCapabilityIds = [regex]::Matches(
    $runtimeCatalogText,
    '"(runtime\.[a-z0-9-]+)"'
) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
if (Compare-Object $expectedRuntimeCapabilityIds $declaredRuntimeCapabilityIds) {
    throw ('Runtime Agent capability catalog must declare exactly: ' + ($expectedRuntimeCapabilityIds -join ', ') + '.')
}

$expectedRuntimeProviderIds = @(
    'integration-event-transport.http',
    'integration-event-transport.rabbitmq',
    'integration-event-transport.rocketmq'
)
$declaredRuntimeProviderIds = [regex]::Matches(
    $runtimeCatalogText,
    '"(integration-event-transport\.[a-z0-9-]+)"'
) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
if (Compare-Object $expectedRuntimeProviderIds $declaredRuntimeProviderIds) {
    throw ('Runtime Agent provider catalog must declare exactly: ' + ($expectedRuntimeProviderIds -join ', ') + '.')
}
$forbiddenStaticRuntimeStatesPattern = '\b(?:HEALTHY|DEGRADED|RECOVERING|SUCCESS)\b'
if ('catalog state HEALTHY' -notmatch $forbiddenStaticRuntimeStatesPattern) {
    throw 'Static Runtime Agent state guard must reject live provider or execution-success states.'
}
if ($runtimeCatalogText -match $forbiddenStaticRuntimeStatesPattern) {
    throw 'Static Runtime Agent facts must not embed live provider or execution-success states.'
}

$providerIdentitySources = [ordered]@{
    'integration-event-transport.http' = 'cap4k-ddd-integration-event-http-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/HttpIntegrationEventAutoConfiguration.kt'
    'integration-event-transport.rabbitmq' = 'cap4k-ddd-integration-event-rabbitmq-starter/src/main/kotlin/com/only4/cap4k/ddd/application/event/RabbitMqIntegrationEventAutoConfiguration.kt'
    'integration-event-transport.rocketmq' = 'ddd-integration-event-rocketmq/src/main/kotlin/com/only4/cap4k/ddd/application/event/RocketMqIntegrationEventPublisher.kt'
}
foreach ($entry in $providerIdentitySources.GetEnumerator()) {
    $sourceFile = Join-Path $repoRoot $entry.Value
    $sourceText = Get-Content -LiteralPath $sourceFile -Raw -Encoding UTF8
    if ($sourceText -notmatch [regex]::Escape('"' + $entry.Key + '"')) {
        throw ("Runtime provider identity '" + $entry.Key + "' does not match " + $entry.Value + '.')
    }
}

$files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
foreach ($relativeDirectory in $currentDirectories) {
    $directory = Join-Path $repoRoot $relativeDirectory
    if (Test-Path -LiteralPath $directory -PathType Container) {
        Get-ChildItem -LiteralPath $directory -Recurse -File |
            Where-Object { $_.Extension -in @('.md', '.yaml', '.yml') } |
            ForEach-Object { $files.Add($_) }
    }
}
foreach ($relativeFile in $currentFiles) {
    $file = Join-Path $repoRoot $relativeFile
    if (Test-Path -LiteralPath $file -PathType Leaf) {
        $files.Add((Get-Item -LiteralPath $file))
    }
}

$activeRuntimeFiles = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
$activeRuntimeExtensions = @(
    '.gradle',
    '.imports',
    '.java',
    '.json',
    '.kt',
    '.kts',
    '.properties',
    '.sql',
    '.toml',
    '.xml',
    '.yaml',
    '.yml'
)
foreach ($relativeRootFile in @('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties')) {
    $rootFile = Join-Path $repoRoot $relativeRootFile
    if (Test-Path -LiteralPath $rootFile -PathType Leaf) {
        $activeRuntimeFiles.Add((Get-Item -LiteralPath $rootFile))
    }
}
foreach ($relativeBuildRoot in @('buildSrc/src/main', 'gradle')) {
    $buildRoot = Join-Path $repoRoot $relativeBuildRoot
    if (Test-Path -LiteralPath $buildRoot -PathType Container) {
        Get-ChildItem -LiteralPath $buildRoot -Recurse -File |
            Where-Object { $_.Extension -in $activeRuntimeExtensions } |
            ForEach-Object { $activeRuntimeFiles.Add($_) }
    }
}
Get-ChildItem -LiteralPath $repoRoot -Directory |
    Where-Object { -not $_.Name.StartsWith('.') } |
    ForEach-Object {
        $moduleBuild = Join-Path $_.FullName 'build.gradle.kts'
        if (Test-Path -LiteralPath $moduleBuild -PathType Leaf) {
            $activeRuntimeFiles.Add((Get-Item -LiteralPath $moduleBuild))
        }

        $sourceRoot = Join-Path $_.FullName 'src/main'
        if (Test-Path -LiteralPath $sourceRoot -PathType Container) {
            Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
                Where-Object { $_.Extension -in $activeRuntimeExtensions } |
                ForEach-Object { $activeRuntimeFiles.Add($_) }
        }
    }

$violations = [System.Collections.Generic.List[string]]::new()
foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $relativePath = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName).Replace('\', '/')
    foreach ($entry in $retiredTerms.GetEnumerator()) {
        if ($text -match $entry.Value) {
            if ($allowedRetiredTermsByPath.ContainsKey($relativePath) -and $entry.Key -in $allowedRetiredTermsByPath[$relativePath]) {
                continue
            }
            if ($entry.Key -eq 'Console module' -and $relativePath -eq 'docs/comet/specs/runtime-console-retirement/spec.md') {
                continue
            }
            if (
                $relativePath -eq 'docs/comet/specs/runtime-snowflake-retirement/spec.md' -and
                $entry.Key -in @(
                    'Snowflake capability',
                    'Snowflake Runtime module',
                    'Snowflake starter',
                    'Snowflake policy',
                    'Worker-ID capability'
                )
            ) {
                continue
            }
            if (
                $relativePath -eq 'docs/comet/specs/runtime-jackson-only/spec.md' -and
                $entry.Key -in @(
                    'EventSubscriber<T>',
                    'Snowflake capability'
                )
            ) {
                continue
            }
            $violations.Add("${relativePath}: retired runtime term '$($entry.Key)'")
        }
    }
}

foreach ($file in $activeRuntimeFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $relativePath = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName).Replace('\', '/')
    foreach ($identity in $expectedRetiredDescriptorIdentities) {
        $escapedIdentity = [regex]::Escape($identity)
        $descriptorPatterns = @(
            ('\b(?:capabilityId|providerId)\s*=\s*["''](?:[^"'']*\.)?{0}["'']' -f $escapedIdentity),
            ('\boverride\s+val\s+id(?:\s*:\s*String)?\s*=\s*["'']{0}["'']' -f $escapedIdentity)
        )
        if ($descriptorPatterns | Where-Object { $text -match $_ }) {
            $violations.Add("${relativePath}: retired Runtime descriptor identity '$identity'")
        }
    }
}

foreach ($file in $activeRuntimeFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $relativePath = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName).Replace('\', '/')
    foreach ($entry in $activeRuntimePatterns.GetEnumerator()) {
        if ($text -match $entry.Value) {
            $violations.Add("${relativePath}: active retired Runtime surface '$($entry.Key)'")
        }
    }
}

if ($violations.Count -gt 0) {
    throw "Current runtime facts contain retired Runtime surfaces:`n$($violations -join "`n")"
}

Write-Output "OK: current runtime facts contain no retired Runtime surfaces."
