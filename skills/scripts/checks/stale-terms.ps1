$ErrorActionPreference = 'Stop'

function Get-TextFiles {
  param(
    [Parameter(Mandatory = $true)]
    [string] $Root,
    [string[]] $Extensions = @('.md', '.yaml', '.yml')
  )

  if (-not (Test-Path -LiteralPath $Root)) {
    return @()
  }

  return @(Get-ChildItem -LiteralPath $Root -Recurse -File |
    Where-Object { $_.Extension -in $Extensions })
}

function Assert-NoForbiddenPattern {
  param(
    [Parameter(Mandatory = $true)]
    [object[]] $Files,
    [Parameter(Mandatory = $true)]
    [object[]] $Patterns,
    [Parameter(Mandatory = $true)]
    [string] $Scope,
    [switch] $MaskDriftGotchaExamples
  )

  foreach ($file in $Files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    if ($MaskDriftGotchaExamples -and $file.FullName -like '*\skills\shared\references\drift-gotchas.md') {
      $text = $text -replace [regex]::Escape('Repository save'), 'masked-stale-repository-save'
      $text = $text -replace [regex]::Escape('Repository saves aggregates'), 'masked-stale-repository-saves-aggregates'
      $text = $text -replace [regex]::Escape('business projects implement Unit of Work'), 'masked-uow-project-code'
      $text = $text -replace [regex]::Escape('business projects implement Mediator'), 'masked-mediator-project-code'
      $text = $text -replace [regex]::Escape('src-generated/main/kotlin'), 'masked-stale-generated-source-root'
      $text = $text -replace 'client/cli\b', 'masked-stale-client-cli-boundary'
      $text = $text -replace [regex]::Escape('build/cap4k code analysis'), 'masked-stale-spaced-analysis-output'
      $text = $text -replace [regex]::Escape('build/cap4k/analysis plan.json'), 'masked-stale-spaced-analysis-plan'
    }

    foreach ($patternInfo in $Patterns) {
      $name = $patternInfo.Name
      $pattern = $patternInfo.Pattern
      if ($text -match $pattern) {
        throw "Forbidden stale text matched in ${Scope}: $($file.FullName): $name"
      }
    }
  }
}

$skillRuntimeFiles = Get-TextFiles -Root 'skills'
$rootTextFiles = @()
if (Test-Path -LiteralPath 'AGENTS.md') {
  $rootTextFiles += Get-Item -LiteralPath 'AGENTS.md'
}

$serviceIntegrationRoot = Join-Path 'skills' 'cap4k-service-integration'
$staleServiceIntegrationPaths = @(
  ('workflows/handle-' + 'external-' + 'fact.md'),
  ('rules/service-' + 'boundaries.md'),
  ('rules/integration-' + 'events.md')
)

$staleServiceIntegrationTerms = @(
  ('handle-' + 'external-' + 'fact'),
  ('rules/service-' + 'boundaries.md'),
  ('rules/integration-' + 'events.md')
)

foreach ($staleServiceIntegrationPath in $staleServiceIntegrationPaths) {
  $fullPath = Join-Path $serviceIntegrationRoot $staleServiceIntegrationPath
  if (Test-Path -LiteralPath $fullPath) {
    throw "Stale service-integration file still exists: $fullPath"
  }
}

foreach ($file in @($skillRuntimeFiles) + @($rootTextFiles)) {
  $text = Get-Content -LiteralPath $file.FullName -Raw
  foreach ($staleServiceIntegrationTerm in $staleServiceIntegrationTerms) {
    if ($text.Contains($staleServiceIntegrationTerm)) {
      throw "Stale service-integration reference matched in runtime text: $($file.FullName): $staleServiceIntegrationTerm"
    }
  }
}

$removedSkillRefPatterns = @(
  @{ Name = 'cap4k-modeling'; Pattern = [regex]::Escape('cap4k-' + 'modeling') },
  @{ Name = 'cap4k-generation'; Pattern = [regex]::Escape('cap4k-' + 'generation') + '($|[^-])' },
  @{ Name = 'cap4k-generated-output-review'; Pattern = [regex]::Escape('cap4k-' + 'generated-' + 'output-' + 'review') },
  @{ Name = 'generated-output-review'; Pattern = [regex]::Escape('generated-' + 'output-' + 'review') },
  @{ Name = 'cap4k-implementation'; Pattern = [regex]::Escape('cap4k-' + 'implementation') + '($|[^-])' },
  @{ Name = 'cap4k-verification'; Pattern = [regex]::Escape('cap4k-' + 'verification') + '($|[^-])' }
)

Assert-NoForbiddenPattern `
  -Files (@($skillRuntimeFiles) + @($rootTextFiles)) `
  -Patterns $removedSkillRefPatterns `
  -Scope 'active skills and root shell text'

$task8StalePatterns = @(
  @{ Name = 'sources.irAnalysis.enabled'; Pattern = 'sources\.irAnalysis\.enabled' },
  @{ Name = 'generators.flow.enabled'; Pattern = 'generators\.flow\.enabled' },
  @{ Name = 'generators.drawingBoard.enabled'; Pattern = 'generators\.drawingBoard\.enabled' },
  @{ Name = 'kspKotlin'; Pattern = 'kspKotlin' },
  @{ Name = 'design validator'; Pattern = 'design validator' },
  @{ Name = 'unsupported validator tag'; Pattern = 'unsupported validator tag' },
  @{ Name = 'Repository save'; Pattern = 'Repository\s+save' },
  @{ Name = 'Repository 保存'; Pattern = 'Repository.*保存' },
  @{ Name = 'business implements Unit of Work'; Pattern = 'business.*implement.*Unit of Work' },
  @{ Name = 'business implements Mediator'; Pattern = 'business.*implement.*Mediator' },
  @{ Name = 'src-generated/main/kotlin'; Pattern = 'src-generated/main/kotlin' },
  @{ Name = 'client/cli'; Pattern = 'client/cli\b' },
  @{ Name = 'build/cap4k code analysis'; Pattern = 'build/cap4k code analysis' },
  @{ Name = 'build/cap4k/analysis plan.json'; Pattern = 'build/cap4k/analysis plan\.json' },
  @{ Name = 'business subscriber consumes transport'; Pattern = 'business subscriber.*consume' },
  @{ Name = 'raw callback enters domain'; Pattern = 'raw callback.*domain' },
  @{ Name = 'combined tactical/design route'; Pattern = 'cap4k-tactical-modeling or cap4k-technical-design' }
)

Assert-NoForbiddenPattern `
  -Files $skillRuntimeFiles `
  -Patterns $task8StalePatterns `
  -Scope 'skills Markdown/YAML runtime text' `
  -MaskDriftGotchaExamples

$legacySkillPatterns = @(
  @{ Name = 'No design support for integration_event'; Pattern = 'No design support for `integration_' + 'event`' },
  @{ Name = 'integration-event design support does not exist today'; Pattern = 'integration-event design support that does not exist ' + 'today' },
  @{ Name = 'unsupported design tags integration_event'; Pattern = 'unsupported design tags.*integration_' + 'event' },
  @{ Name = 'enumTranslation.set'; Pattern = 'enumTranslation' + '\.set' },
  @{ Name = 'read docs/public during normal operation'; Pattern = 'read docs/public/authoring during normal ' + 'operation' },
  @{ Name = 'cap4k-runtime-integration'; Pattern = 'cap4k-runtime-' + 'integration' },
  @{ Name = 'runtime-integration'; Pattern = 'runtime-' + 'integration' },
  @{ Name = 'value concepts'; Pattern = 'value ' + 'concepts' },
  @{ Name = 'enum translation core DSL'; Pattern = 'enum translation core ' + 'DSL' },
  @{ Name = 'controller/job/subscriber bundle'; Pattern = 'controller / job / ' + 'subscriber' }
)

Assert-NoForbiddenPattern `
  -Files $skillRuntimeFiles `
  -Patterns $legacySkillPatterns `
  -Scope 'active skills runtime text'

$authoringTextFiles = Get-TextFiles -Root 'docs/public/authoring' -Extensions @('.md')
$analysisTextFiles = Get-TextFiles -Root 'docs/superpowers/analysis' -Extensions @('.md')

$authoringForbiddenPatterns = @(
  @{ Name = 'value concepts'; Pattern = 'value ' + 'concepts' },
  @{ Name = 'client/cli'; Pattern = 'client/' + 'cli\b' },
  @{ Name = 'controller/job/subscriber bundle'; Pattern = 'controller / job / ' + 'subscriber' },
  @{ Name = 'cli Chinese boundary wording'; Pattern = [string]::Concat('cli ', [char]0x662F, [char]0x9632, [char]0x8150, [char]0x8FB9, [char]0x754C) },
  @{ Name = 'RPC/message listener wording'; Pattern = 'RPC' + [char]0x3001 + 'message ' + 'listener' },
  @{ Name = 'controller surfaces'; Pattern = 'controller surfaces' },
  @{ Name = 'job surfaces'; Pattern = 'job surfaces' },
  @{ Name = 'subscriber surfaces'; Pattern = 'subscriber surfaces' },
  @{ Name = 'process step'; Pattern = 'process ' + 'step' }
)

Assert-NoForbiddenPattern `
  -Files $authoringTextFiles `
  -Patterns $authoringForbiddenPatterns `
  -Scope 'public authoring docs'

$removedEventGuidancePatterns = @(
  @{ Name = 'AutoAttach'; Pattern = 'Auto' + 'Attach' },
  @{ Name = 'AutoRequest'; Pattern = 'Auto' + 'Request' },
  @{ Name = 'AutoRequests'; Pattern = 'Auto' + 'Requests' },
  @{ Name = 'AutoRelease'; Pattern = 'Auto' + 'Release' },
  @{ Name = 'AutoReleases'; Pattern = 'Auto' + 'Releases' },
  @{ Name = 'Mediator.events.publish'; Pattern = 'Mediator\.events\.' + 'publish' },
  @{ Name = 'IntegrationEventSupervisor.publish'; Pattern = 'IntegrationEventSupervisor\.' + 'publish' },
  @{ Name = 'IntegrationEventSupervisor.instance.publish'; Pattern = 'IntegrationEventSupervisor\.instance\.' + 'publish' },
  @{ Name = 'attach or publish'; Pattern = 'attach ' + 'or ' + 'publish' },
  @{ Name = 'attach/publish'; Pattern = 'attach ' + '/ ' + 'publish' },
  @{ Name = 'attach Chinese publish'; Pattern = 'attach ' + [char]0x6216 + ' ' + 'publish' }
)

Assert-NoForbiddenPattern `
  -Files (@($skillRuntimeFiles) + @($authoringTextFiles) + @($analysisTextFiles)) `
  -Patterns $removedEventGuidancePatterns `
  -Scope 'active skills, public authoring docs, and superpowers analysis docs'

$runtimeSourceRoots = @(
  'ddd-core',
  'cap4k-ddd-starter',
  'ddd-application-request-jpa',
  'ddd-domain-event-jpa',
  'ddd-integration-event-http',
  'ddd-integration-event-http-jpa',
  'ddd-integration-event-rabbitmq',
  'ddd-integration-event-rocketmq'
)

$runtimeSourceFiles = @()
foreach ($root in $runtimeSourceRoots) {
  if (-not (Test-Path -LiteralPath $root)) {
    continue
  }

  $runtimeSourceFiles += Get-ChildItem -LiteralPath $root -Recurse -File |
    Where-Object { $_.Extension -in @('.kt', '.java') }
}

if ($runtimeSourceFiles.Count -gt 0) {
  Assert-NoForbiddenPattern `
    -Files $runtimeSourceFiles `
    -Patterns $removedEventGuidancePatterns `
    -Scope 'runtime source'
}
